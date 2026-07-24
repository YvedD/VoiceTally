#!/usr/bin/env python3
"""
Train a small Keras model from the exported CSV and convert it to a TFLite file.

Usage:
  python tools/train_model.py
  (A file selector will open to choose the training CSV)

This script:
 - Loads the CSV produced by the app
 - Selects a sensible numeric feature set, fills missing values
 - Encodes species labels and saves a mapping JSON next to the output .tflite
 - Trains a small dense network with early stopping
 - Converts the best model to TFLite with optional quantization
"""
import argparse
import json
import os
import tempfile
import sys

# Graphical file selector imports
try:
    import tkinter as tk
    from tkinter import filedialog
    TK_AVAILABLE = True
except Exception:
    TK_AVAILABLE = False

import numpy as np
import pandas as pd
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import LabelEncoder

# TensorFlow is optional: try to import, otherwise fall back to scikit-learn
try:
    import tensorflow as tf
    TF_AVAILABLE = True
except Exception:
    tf = None
    TF_AVAILABLE = False
    from sklearn.ensemble import RandomForestClassifier
    import joblib


def select_files_gui():
    """Opens a GUI to select the training CSV and optional labels JSON."""
    if not TK_AVAILABLE:
        print("Error: tkinter is not installed. Please provide paths via command line or install tkinter.")
        return None, None

    root = tk.Tk()
    root.withdraw()
    root.attributes("-topmost", True)

    print("Opening file selector for training CSV...")
    csv_path = filedialog.askopenfilename(
        title="Selecteer de training_data_current.csv",
        filetypes=[("CSV files", "*.csv"), ("All files", "*.*")]
    )
    
    if not csv_path:
        print("Geen bestand geselecteerd. Afbreken.")
        sys.exit(1)

    # Automatically look for labels in the same directory
    dir_name = os.path.dirname(csv_path)
    default_labels = os.path.join(dir_name, "personal_migration_model.labels.json")
    
    labels_path = None
    if os.path.exists(default_labels):
        print(f"Labels bestand automatisch gevonden: {default_labels}")
        labels_path = default_labels
    else:
        print("Labels bestand niet automatisch gevonden.")
        # Ask to select labels.json
        labels_path = filedialog.askopenfilename(
            title="Selecteer personal_migration_model.labels.json (Optioneel)",
            filetypes=[("JSON files", "*.json"), ("All files", "*.*")]
        )

    return csv_path, labels_path


def load_csv(path):
    try:
        # Stap 1: Snelle C-lezer met strikte separator
        # Gebruik on_bad_lines='skip' om regels met verkeerd aantal kolommen over te slaan
        df = pd.read_csv(path, sep=';', quotechar='"', on_bad_lines='skip', engine='c')
        if len(df.columns) < 20:
             raise ValueError("Te weinig kolommen gedetecteerd met ';'")
        return df
    except Exception as e:
        print(f'Waarschuwing: Snelle CSV-lezer gaf een melding ({e}). Schakelen naar fallback...')
        try:
            # Stap 2: Python engine is langzamer maar toleranter voor afwijkende regels
            df = pd.read_csv(path, sep=None, engine='python', on_bad_lines='skip')
            return df
        except Exception as e2:
            print(f'Fout: CSV is onleesbaar: {e2}')
            raise


def choose_feature_columns(df: pd.DataFrame):
    preferred = [
        'temp_numeric', 'wind_ms_numeric', 'wind_dir_sin', 'wind_dir_cos',
        'cloud_pct', 'visibility', 'precip', 'ref_avg_wind_ms', 'ref_avg_pressure',
        'ref_coast_wind_ms', 'ref_coast_pressure',
        'day_sin', 'day_cos', 'hour_sin', 'hour_cos', 'moon_phase', 
        'wind_chill', 'pressure_trend', 'yesterday_count', 'is_rare', 'label_count'
    ]
    # Check if preferred columns exist
    cols = [c for c in preferred if c in df.columns]
    
    if not cols:
        print("Warning: No preferred feature columns found. Falling back to all numeric columns.")
        skip = {'tellingid', 'epoch', 'siteid', 'label_species_id', 'sample_weight'}
        # Fixed NumPy dtype check to avoid TypeError with StringDtype
        for c in df.columns:
            if c in skip:
                continue
            # Try to convert to numeric first, then check if it's numeric
            col_numeric = pd.to_numeric(df[c], errors='coerce')
            if not col_numeric.isnull().all():
                cols.append(c)
    return cols


def build_model(input_dim, num_classes):
    # Diepere architectuur voor 410 klassen en complexe trek-patronen
    model = tf.keras.Sequential([
        tf.keras.layers.Input(shape=(input_dim,)),
        tf.keras.layers.Dense(512, activation='relu'),
        tf.keras.layers.BatchNormalization(),
        tf.keras.layers.Dropout(0.4),
        tf.keras.layers.Dense(256, activation='relu'),
        tf.keras.layers.BatchNormalization(),
        tf.keras.layers.Dropout(0.3),
        tf.keras.layers.Dense(128, activation='relu'),
        tf.keras.layers.Dense(num_classes, activation='softmax')
    ])
    model.compile(optimizer='adam', loss='sparse_categorical_crossentropy', metrics=['accuracy'])
    return model


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--csv', help='Path to exported training CSV')
    parser.add_argument('--labels', help='Path to labels JSON')
    parser.add_argument('--out', help='Output .tflite path')
    parser.add_argument('--epochs', type=int, default=30)
    args = parser.parse_args()

    # Use GUI if command line args are missing
    csv_path = args.csv
    labels_path = args.labels
    
    if not csv_path:
        csv_path, labels_path = select_files_gui()

    out_path = args.out or os.path.join(os.path.dirname(csv_path), "personal_migration_model.tflite")

    df = load_csv(csv_path)
    print(f'Loaded {len(df)} rows.')

    feature_cols = choose_feature_columns(df)
    print('Using features:', feature_cols)

    # Use features (all 21 numeric columns) with explicit normalization
    X_list = []
    for c in feature_cols:
        col_data = pd.to_numeric(df[c], errors='coerce')
        
        # Apply exact same normalization as in App (AiInferenceEngine.kt)
        if c == 'temp_numeric' or c == 'wind_chill':
            v = (col_data + 20) / 60
        elif 'wind_ms' in c:
            v = col_data / 35
        elif 'sin' in c or 'cos' in c:
            v = (col_data + 1) / 2
        elif c == 'cloud_pct':
            v = col_data / 100
        elif c == 'visibility':
            v = col_data / 20000
        elif c == 'precip':
            v = col_data / 50
        elif 'pressure' in c and 'trend' not in c:
            v = (col_data - 950) / 80
        elif c == 'pressure_trend':
            v = (col_data + 20) / 40
        elif c == 'yesterday_count':
            v = col_data / 10000
        else:
            v = col_data # moon_phase, is_rare, label_count zijn al 0..1
            
        X_list.append(v.fillna(v.mean() if not v.isnull().all() else 0).clip(0, 1).values)
    
    X = np.column_stack(X_list).astype(np.float32)

    # Sample weights logic
    sample_weight = None
    if 'sample_weight' in df.columns:
        sw = pd.to_numeric(df['sample_weight'], errors='coerce').fillna(1.0).values
        sample_weight = sw / (np.mean(sw) + 1e-9)
        print("Using sample weights for training.")

    # Labels processing
    le = LabelEncoder()
    if labels_path and os.path.exists(labels_path):
        with open(labels_path, 'r', encoding='utf-8') as f:
            data = json.load(f)
            known_classes = data['classes']
            le.classes_ = np.array(known_classes)
            print(f"Using {len(known_classes)} classes from JSON.")
            y = le.transform(df['label_species_id'].astype(str).values)
    else:
        y = le.fit_transform(df['label_species_id'].astype(str).values)
        print(f'Found {len(le.classes_)} unique species in CSV.')

    num_classes = len(le.classes_)

    # Splits
    try:
        X_train, X_val, y_train, y_val, sw_train, sw_val = train_test_split(
            X, y, sample_weight if sample_weight is not None else np.ones(len(y)),
            test_size=0.15, stratify=y
        )
    except:
        X_train, X_val, y_train, y_val, sw_train, sw_val = train_test_split(
            X, y, sample_weight if sample_weight is not None else np.ones(len(y)),
            test_size=0.15
        )

    if TF_AVAILABLE:
        model = build_model(X_train.shape[1], num_classes)
        callbacks = [tf.keras.callbacks.EarlyStopping(monitor='val_loss', patience=5, restore_best_weights=True)]
        
        # Determine if we use weights in fit
        fit_sw = sw_train if sample_weight is not None else None
        val_sw = sw_val if sample_weight is not None else None
        
        model.fit(X_train, y_train, 
                  validation_data=(X_val, y_val, val_sw) if val_sw is not None else (X_val, y_val), 
                  sample_weight=fit_sw,
                  epochs=args.epochs, batch_size=128, callbacks=callbacks)

        # Opslaan voor conversie
        tmpdir = tempfile.mkdtemp()
        print(f"Exporteren model naar tijdelijke map: {tmpdir}")
        
        # Keras 3 / TF 2.16+ vereist model.export() voor een SavedModel directory
        try:
            if hasattr(model, 'export'):
                model.export(tmpdir)
            else:
                model.save(tmpdir) # Fallback voor oudere versies
        except Exception as e:
            print(f"Waarschuwing: model.export mislukt ({e}), proberen via tf.saved_model...")
            tf.saved_model.save(model, tmpdir)

        converter = tf.lite.TFLiteConverter.from_saved_model(tmpdir)
        converter.optimizations = [tf.lite.Optimize.DEFAULT]
        tflite_model = converter.convert()
        
        with open(out_path, 'wb') as f:
            f.write(tflite_model)
        
        # Save mapping
        map_path = out_path.replace(".tflite", ".labels.json")
        with open(map_path, 'w', encoding='utf-8') as f:
            json.dump({'classes': le.classes_.tolist()}, f, indent=2)
            
        print(f'Done! Model saved to {out_path}')
    else:
        print('TensorFlow not available, skipping TFLite generation.')

if __name__ == '__main__':
    main()

#!/usr/bin/env python3
"""
Train a small Keras model from the exported CSV and convert it to a TFLite file.

Usage examples:
  python tools/train_model.py --csv /path/to/training_export.csv --out model.tflite
  python tools/train_model.py --csv data.csv --out model.tflite --quantize dynamic --epochs 20

This script:
 - Loads the CSV produced by the app
 - Selects a sensible numeric feature set, fills missing values
 - Encodes species labels and saves a mapping JSON next to the output .tflite
 - Trains a small dense network with early stopping
 - Converts the best model to TFLite with optional quantization

Notes:
 - For real production models more feature engineering and model validation is required.
"""
import argparse
import json
import os
import tempfile

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


def load_csv(path):
    # Try a normal fast C-engine read first; if it fails due to malformed rows
    # (common with exported CSVs that contain stray separators or bad quoting),
    # retry with the python engine and skip bad lines so the training can proceed.
    try:
        df = pd.read_csv(path)
        return df
    except pd.errors.ParserError as e:
        print('Warning: pandas.ParserError while reading CSV:', e)
        print('Retrying with engine="python" and on_bad_lines="skip" to skip malformed rows...')
        try:
            # on_bad_lines='skip' is available in pandas >= 1.3; it will drop offending rows
            df = pd.read_csv(path, engine='python', on_bad_lines='skip')
            return df
        except Exception as e2:
            print('Fallback CSV read also failed:', e2)
            raise


def choose_feature_columns(df: pd.DataFrame):
    # Prefer engineered numeric columns from the exporter; fall back to heuristics
    preferred = [
        'temp_numeric', 'wind_ms_numeric', 'wind_dir_sin', 'wind_dir_cos',
        'cloud_pct', 'visibility', 'precip', 'ref_avg_wind_ms', 'ref_avg_pressure',
        'day_sin', 'day_cos', 'hour_sin', 'hour_cos', 'label_count'
    ]
    cols = [c for c in preferred if c in df.columns]
    if not cols:
        # take all numeric columns except known meta columns
        skip = {'tellingid', 'epoch', 'siteid', 'label_species_id', 'sample_weight'}
        cols = [c for c, t in df.dtypes.items() if np.issubdtype(t, np.number) and c not in skip]
    return cols


def build_model(input_dim, num_classes):
    if not TF_AVAILABLE:
        raise RuntimeError('TensorFlow is not available in this environment')
    model = tf.keras.Sequential([
        tf.keras.layers.Input(shape=(input_dim,)),
        tf.keras.layers.Dense(128, activation='relu'),
        tf.keras.layers.Dropout(0.3),
        tf.keras.layers.Dense(64, activation='relu'),
        tf.keras.layers.Dropout(0.2),
        tf.keras.layers.Dense(num_classes, activation='softmax')
    ])
    model.compile(optimizer='adam', loss='sparse_categorical_crossentropy', metrics=['accuracy'])
    return model


def convert_to_tflite(saved_model_dir: str, out_path: str, quantize: str = 'none'):
    if not TF_AVAILABLE:
        raise RuntimeError('TensorFlow not available for TFLite conversion')
    converter = tf.lite.TFLiteConverter.from_saved_model(saved_model_dir)
    if quantize == 'dynamic':
        converter.optimizations = [tf.lite.Optimize.DEFAULT]
    elif quantize == 'float16':
        converter.optimizations = [tf.lite.Optimize.DEFAULT]
        converter.target_spec.supported_types = [tf.float16]
    # else: no quantization
    tflite_model = converter.convert()
    with open(out_path, 'wb') as f:
        f.write(tflite_model)


def train_with_sklearn(X_train, y_train, X_val, y_val, sample_weight, out_path):
    # Train a RandomForest classifier as a fallback when TF isn't installed
    clf = RandomForestClassifier(n_estimators=200, max_depth=12, random_state=42, n_jobs=-1)
    if sample_weight is not None:
        clf.fit(X_train, y_train, sample_weight=sample_weight)
    else:
        clf.fit(X_train, y_train)
    acc = clf.score(X_val, y_val)
    print(f'Sklearn validation accuracy: {acc:.4f}')
    joblib.dump(clf, out_path)
    print('Wrote sklearn model to', out_path)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--csv', required=True, help='Path to exported training CSV')
    parser.add_argument('--out', required=True, help='Output .tflite path')
    parser.add_argument('--epochs', type=int, default=25)
    parser.add_argument('--batch-size', type=int, default=128)
    parser.add_argument('--val-split', type=float, default=0.15)
    parser.add_argument('--quantize', choices=['none', 'dynamic', 'float16'], default='dynamic')
    parser.add_argument('--seed', type=int, default=42)
    args = parser.parse_args()

    df = load_csv(args.csv)
    print('Loaded', len(df), 'rows. Columns:', list(df.columns))

    feature_cols = choose_feature_columns(df)
    if not feature_cols:
        raise SystemExit('No feature columns found in CSV. Check the header and exporter.')
    print('Using features:', feature_cols)

    X = df[feature_cols].copy()
    # Fill missing numeric values with column mean
    for c in X.columns:
        if X[c].isnull().any():
            mean = X[c].mean()
            X[c] = X[c].fillna(mean)

    X = X.astype(float).values

    # Labels
    le = LabelEncoder()
    y = le.fit_transform(df['label_species_id'].astype(str).values)
    num_classes = len(le.classes_)
    print('Found', num_classes, 'unique species')

    # Save label mapping next to output
    out_dir = os.path.dirname(os.path.abspath(args.out)) or '.'
    labels_path = os.path.join(out_dir, os.path.splitext(os.path.basename(args.out))[0] + '.labels.json')
    with open(labels_path, 'w', encoding='utf-8') as f:
        json.dump({'classes': le.classes_.tolist()}, f, ensure_ascii=False, indent=2)
    print('Wrote label mapping to', labels_path)

    # Sample weights if available
    sample_weight = None
    if 'sample_weight' in df.columns:
        sw = df['sample_weight'].fillna(1.0).astype(float).values
        # Ensure non-negative weights; replace negatives with 0
        sw = np.where(sw < 0, 0.0, sw)
        # If all weights are zero after cleaning, ignore sample_weight
        if np.allclose(sw, 0.0):
            sample_weight = None
        else:
            # Normalize weights to avoid extremely large loss scaling
            mean_sw = np.mean(sw)
            if mean_sw <= 0:
                sample_weight = None
            else:
                sample_weight = sw / (mean_sw + 1e-9)

    # Train/validation split. Prefer stratified split, but if some classes have
    # only one sample train_test_split with stratify will fail. In that case
    # fall back to a regular (non-stratified) split.
    try:
        X_train, X_val, y_train, y_val, sw_train, sw_val = train_test_split(
            X, y, sample_weight if sample_weight is not None else np.zeros(len(y)),
            test_size=args.val_split, random_state=args.seed, stratify=y
        )
    except ValueError as e:
        print('Warning: stratified train_test_split failed:', e)
        print('Falling back to a non-stratified train/validation split (this may change class balance in splits).')
        X_train, X_val, y_train, y_val, sw_train, sw_val = train_test_split(
            X, y, sample_weight if sample_weight is not None else np.zeros(len(y)),
            test_size=args.val_split, random_state=args.seed
        )

    # Convert sw back to None if it wasn't provided
    if sample_weight is None:
        sw_train = None
        sw_val = None
    else:
        # if we passed zeros placeholder, train_test_split returned zeros array; replace with actual split of sample_weight
        sw_train = sw_train
        sw_val = sw_val

    if TF_AVAILABLE:
        model = build_model(X_train.shape[1], num_classes)

        callbacks = [
            tf.keras.callbacks.EarlyStopping(monitor='val_loss', patience=4, restore_best_weights=True)
        ]

        model.fit(
            X_train, y_train,
            validation_data=(X_val, y_val) if sw_val is None else (X_val, y_val, sw_val),
            epochs=args.epochs,
            batch_size=args.batch_size,
            sample_weight=sw_train,
            callbacks=callbacks
        )

        # Save best model to temp dir and convert
        tmpdir = tempfile.mkdtemp(prefix='vt5_saved_')
        print('Saving SavedModel to', tmpdir)
        # Keras 3 changed model.save semantics; prefer SavedModel export.
        try:
            model.save(tmpdir)
        except ValueError as e:
            print('model.save failed, attempting model.export or tf.saved_model.save:', e)
            # Keras may provide model.export() for SavedModel; fall back to tf.saved_model.save
            try:
                export = getattr(model, 'export', None)
                if callable(export):
                    model.export(tmpdir)
                else:
                    tf.saved_model.save(model, tmpdir)
            except Exception as e2:
                print('Failed to export SavedModel:', e2)
                raise

        print('Converting to TFLite (quantize=%s)...' % args.quantize)
        convert_to_tflite(tmpdir, args.out, quantize=args.quantize)
        print('Wrote', args.out)
    else:
        # No TensorFlow available: train a sklearn model and save as joblib
        out_joblib = os.path.splitext(args.out)[0] + '.joblib'
        print('TensorFlow not available: training sklearn RandomForest and writing', out_joblib)
        train_with_sklearn(X_train, y_train, X_val, y_val, sw_train, out_joblib)


if __name__ == '__main__':
    main()


import tensorflow as tf
from tensorflow import keras
from tensorflow.keras import layers
import numpy as np

def create_base_model():
    """
    Creëert een klein, universeel basismodel voor vogelwaarnemingssuggesties.
    Dit model begrijpt abstracte patronen zoals tijdstip, seizoen en weer.
    
    Input features (voorbeeld):
    - epoch (tijdstip)
    - temp, wind, neerslag (weer)
    - day_sin/cos, hour_sin/cos (periodiek tijdstip)
    - siteid (locatie)
    """
    
    # Input laag: 20 features (gebaseerd op TrainingDataPreparer.kt)
    inputs = keras.Input(shape=(20,), name="features")
    
    # Hidden layers voor patroonherkenning
    x = layers.Dense(64, activation='relu')(inputs)
    x = layers.Dense(64, activation='relu')(x)
    x = layers.Dropout(0.2)(x) # Voorkomt overfitting
    
    # "Bottleneck" laag: Deze laag bevat de geëxtraheerde kenmerken (features)
    # die we op het toestel gaan finetunen.
    bottleneck = layers.Dense(32, activation='relu', name="bottleneck")(x)
    
    # Output laag: Een tijdelijke laag (bijv. voor 100 soorten)
    # Deze wordt op het toestel vervangen/aangevuld door de echte soortenlijst.
    outputs = layers.Dense(100, activation='softmax', name="output")(bottleneck)
    
    model = keras.Model(inputs=inputs, outputs=outputs, name="vt5_base_model")
    
    # Compileer het model
    model.compile(
        optimizer='adam',
        loss='sparse_categorical_crossentropy',
        metrics=['accuracy']
    )
    
    return model

if __name__ == "__main__":
    model = create_base_model()
    model.summary()
    
    # Sla het model op als TFLite (float32 voor compatibiliteit)
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    # Zorg dat de training-ops worden meegenomen voor on-device training
    # (vereist in latere versies van TFLite voor on-device backprop)
    # converter.optimizations = [tf.lite.Optimize.DEFAULT]
    
    tflite_model = converter.convert()
    
    with open("base_model.tflite", "wb") as f:
        f.write(tflite_model)
        
    print("\nBasismodel 'base_model.tflite' is aangemaakt!")
    print("Plaats dit bestand in 'app/src/main/assets/' van je Android project.")

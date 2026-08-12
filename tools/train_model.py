import os
import tensorflow as tf
import numpy as np

# VT5 - On-Device Training Model Generator
# Dit script genereert een base_model.tflite met Training Signatures.
# Inputs: 21 features (Weer, Tijd, Druk-trend, Gisteren-factor, Locatie)

NUM_FEATURES = 21
NUM_CLASSES = 1000 # Gereserveerde ruimte voor vogelsoorten

class MigrationModel(tf.Module):
    def __init__(self):
        super(MigrationModel, self).__init__()
        # Eenvoudig maar krachtig neuraal netwerk
        self.w1 = tf.Variable(tf.random.normal([NUM_FEATURES, 128], stddev=0.1), name="w1")
        self.b1 = tf.Variable(tf.zeros([128]), name="b1")
        self.w2 = tf.Variable(tf.random.normal([128, 64], stddev=0.1), name="w2")
        self.b2 = tf.Variable(tf.zeros([64]), name="b2")
        self.w3 = tf.Variable(tf.random.normal([64, NUM_CLASSES], stddev=0.1), name="w3")
        self.b3 = tf.Variable(tf.zeros([NUM_CLASSES]), name="b3")
        
        self.optimizer = tf.optimizers.Adam(learning_rate=0.001)

    @tf.function(input_signature=[
        tf.TensorSpec([None, NUM_FEATURES], tf.float32, name="x")
    ])
    def infer(self, x):
        y = tf.nn.relu(tf.matmul(x, self.w1) + self.b1)
        y = tf.nn.relu(tf.matmul(y, self.w2) + self.b2)
        logits = tf.matmul(y, self.w3) + self.b3
        return {"output": tf.nn.softmax(logits, name="output")}

    @tf.function(input_signature=[
        tf.TensorSpec([None, NUM_FEATURES], tf.float32, name="x"),
        tf.TensorSpec([None, NUM_CLASSES], tf.float32, name="y")
    ])
    def train(self, x, y):
        with tf.GradientTape() as tape:
            prediction = self.infer(x)["output"]
            loss = tf.reduce_mean(tf.keras.losses.categorical_crossentropy(y, prediction))
        
        gradients = tape.gradient(loss, [self.w1, self.b1, self.w2, self.b2, self.w3, self.b3])
        self.optimizer.apply_gradients(zip(gradients, [self.w1, self.b1, self.w2, self.b2, self.w3, self.b3]))
        return {"loss": loss}

    @tf.function(input_signature=[tf.TensorSpec(shape=[], dtype=tf.string)])
    def save(self, checkpoint_path):
        tensor_names = [self.w1.name, self.b1.name, self.w2.name, self.b2.name, self.w3.name, self.b3.name]
        tensors = [self.w1, self.b1, self.w2, self.b2, self.w3, self.b3]
        tf.raw_ops.Save(filename=checkpoint_path, tensor_names=tensor_names, data=tensors)
        return {"status": tf.constant("saved")}

    @tf.function(input_signature=[tf.TensorSpec(shape=[], dtype=tf.string)])
    def restore(self, checkpoint_path):
        tensor_names = [self.w1.name, self.b1.name, self.w2.name, self.b2.name, self.w3.name, self.b3.name]
        dtypes = [tf.float32] * 6
        # RestoreV2 is de modernere versie die meerdere tensors tegelijk kan inladen
        restored_tensors = tf.raw_ops.RestoreV2(
            prefix=checkpoint_path, 
            tensor_names=tensor_names, 
            shape_and_slices=[""] * 6, 
            dtypes=dtypes
        )
        self.w1.assign(restored_tensors[0])
        self.b1.assign(restored_tensors[1])
        self.w2.assign(restored_tensors[2])
        self.b2.assign(restored_tensors[3])
        self.w3.assign(restored_tensors[4])
        self.b3.assign(restored_tensors[5])
        return {"status": tf.constant("restored")}

def main():
    model = MigrationModel()
    
    # Exporteer naar TFLite met alle nodige signatures voor on-device training
    converter = tf.lite.TFLiteConverter.from_concrete_functions([
        model.infer.get_concrete_function(),
        model.train.get_concrete_function(),
        model.save.get_concrete_function(),
        model.restore.get_concrete_function()
    ], model)
    
    converter.target_spec.supported_ops = [
        tf.lite.OpsSet.TFLITE_BUILTINS,
        tf.lite.OpsSet.SELECT_TF_OPS
    ]
    
    tflite_model = converter.convert()
    
    output_path = "app/src/main/assets/base_model.tflite"
    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    with open(output_path, "wb") as f:
        f.write(tflite_model)
    
    print(f"Base model succesvol gegenereerd in {output_path}")
    print("Features: 21 | Signatures: infer, train, save, restore")

if __name__ == "__main__":
    main()

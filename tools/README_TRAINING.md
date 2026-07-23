Training tools for VT-AI
=======================

This folder contains a small example Python training script that consumes the CSV exported by the app
and produces a TFLite model for on-device inference.

Requirements
------------
Install the Python packages in a virtualenv:

```bash
python -m venv .venv
.venv\Scripts\activate   # Windows PowerShell
pip install -r tools/requirements.txt
```

Run the training script
-----------------------
Export a CSV from the app; the app also writes a local copy to the app files directory under `files/ai_training/`.

Copy a CSV to your development machine and run:

```bash
python tools/train_model.py --csv path/to/training_export_....csv --out my_model.tflite
```

The script is intentionally minimal: it demonstrates a workflow (CSV -> simple NN -> TFLite).
For production use you should add feature engineering, class weighting for rare species, cross-validation,
and more training epochs/hyperparameter search.


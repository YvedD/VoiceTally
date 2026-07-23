# Create and activate a virtualenv in tools/.venv and install Python dependencies (excluding TensorFlow)
param(
    [string]$VenvDir = ".venv"
)

Write-Host "Creating virtualenv in tools/$VenvDir (if not exists)"
python -m venv $VenvDir

Write-Host "Activating virtualenv"
Set-ExecutionPolicy -ExecutionPolicy Bypass -Scope Process -Force
.\$VenvDir\Scripts\Activate.ps1

Write-Host "Upgrading pip"
python -m pip install --upgrade pip setuptools wheel

Write-Host "Installing Python deps (pandas, numpy, scikit-learn)"
python -m pip install -r tools/requirements.txt

Write-Host "NOTE: TensorFlow is NOT installed by this script. On Windows installing TensorFlow via pip can fail depending on your Python version and environment."
Write-Host "If you need TensorFlow, either use conda (recommended):"
Write-Host "  conda create -n vt5 python=3.10"
Write-Host "  conda activate vt5"
Write-Host "  conda install -c conda-forge tensorflow"
Write-Host "Or install with pip if you have a supported Python binary (see tools/requirements_tf.txt)."


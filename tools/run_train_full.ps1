<#
Run full training pipeline and produce .tflite from exported CSV.

Save as: C:\AndroidApps\VoiceTally\tools\run_train_full.ps1

Usage examples (PowerShell):
  # default: uses the CSV path from your message and writes model.tflite under tools\data
  powershell -ExecutionPolicy Bypass -File .\tools\run_train_full.ps1

  # explicit paths:
  powershell -ExecutionPolicy Bypass -File .\tools\run_train_full.ps1 -CsvPath "C:\Users\ydsds\Downloads\Trektellen exports\TFLite\trainings CSV's\training_export_1784571460423.csv" -OutPath "C:\AndroidApps\VoiceTally\tools\data\training_model.tflite"

  # if pip install tensorflow fails, attempt to use conda to create an env and run inside it:
  powershell -ExecutionPolicy Bypass -File .\tools\run_train_full.ps1 -UseConda

Notes:
 - This script DOES NOT modify your repository files; you must save it yourself.
 - If TensorFlow pip install fails because of incompatible Python/OS, prefer the conda flow.
#>

param(
    [string]$CsvPath = "C:\Users\ydsds\Downloads\Trektellen exports\TFLite\trainings CSV's\training_export_1784571460423.csv",
    [string]$OutPath = "C:\AndroidApps\VoiceTally\tools\data\training_model.tflite",
    [int]$Epochs = 25,
    [int]$BatchSize = 128,
    [ValidateSet("none","dynamic","float16")]
    [string]$Quantize = "dynamic",
    [switch]$UseConda,          # If present, prefer conda fallback when pip TF install fails
    [switch]$CreateCondaEnv     # If present together with -UseConda, script will try to create a conda env
)

function Write-Log {
    param($msg)
    Write-Host "[run_train_full] $msg"
}

# basic checks
if (-not (Test-Path $CsvPath)) {
    Write-Error "CSV not found: $CsvPath"
    exit 1
}

# ensure out dir exists
$outDir = Split-Path -Path $OutPath -Parent
if (-not (Test-Path $outDir)) {
    Write-Log "Creating output directory: $outDir"
    New-Item -ItemType Directory -Path $outDir -Force | Out-Null
}

# venv location (relative to repo)
$venvDir = "tools\.venv"
if (-not (Test-Path $venvDir)) {
    Write-Log "Creating virtual environment in $venvDir"
    python -m venv $venvDir
}

# activate venv in current session
Write-Log "Activating virtual environment: $venvDir"
Set-ExecutionPolicy -ExecutionPolicy Bypass -Scope Process -Force
& "$venvDir\Scripts\Activate.ps1"

# upgrade pip and install base requirements
Write-Log "Upgrading pip and installing base requirements (pandas,numpy,scikit-learn)"
python -m pip install --upgrade pip setuptools wheel
python -m pip install -r tools/requirements.txt

# attempt to install TF with pip
$tfInstalled = $false
Write-Log "Attempting to install TensorFlow via pip (see tools/requirements_tf.txt)"
try {
    python -m pip install -r tools/requirements_tf.txt
    $tfInstalled = $true
    Write-Log "TensorFlow installed via pip."
} catch {
    Write-Log "pip install of TensorFlow failed: $($_.Exception.Message)"
    $tfInstalled = $false
}

if (-not $tfInstalled -and -not $UseConda.IsPresent) {
    Write-Log "TensorFlow not installed. You can retry with -UseConda to let the script try conda, or install TF manually (see tools/requirements_tf.txt or tools/setup_env.ps1)."
}

# If TF not installed and user asked for conda fallback, try to run via conda
if (-not $tfInstalled -and $UseConda.IsPresent) {
    $condaCmd = Get-Command conda -ErrorAction SilentlyContinue
    if ($null -eq $condaCmd) {
        Write-Error "Conda not found on PATH. Install Anaconda/Miniconda or run pip TensorFlow manually."
        exit 1
    }

    $envName = "vt5_tf_env"
    if ($CreateCondaEnv.IsPresent) {
        Write-Log "Creating conda environment '$envName' with python=3.10 and tensorflow (conda-forge). This can take several minutes."
        & conda create -y -n $envName python=3.10
        if ($LASTEXITCODE -ne 0) { Write-Error "Failed to create conda env"; exit 1 }
        Write-Log "Installing tensorflow in conda env (conda-forge)"
        & conda install -y -n $envName -c conda-forge tensorflow
        if ($LASTEXITCODE -ne 0) { Write-Error "Conda install tensorflow failed"; exit 1 }
    } else {
        Write-Log "Will attempt to run training inside existing or created conda env '$envName' using 'conda run'. If the env doesn't exist the run will fail; rerun with -CreateCondaEnv to create it."
    }

    # run the training inside conda env using conda run
    $pyArgs = @(
        "tools/train_model.py",
        "--csv", $CsvPath,
        "--out", $OutPath,
        "--epochs", $Epochs,
        "--batch-size", $BatchSize,
        "--quantize", $Quantize
    )
    Write-Log "Running training inside conda env '$envName'..."
    & conda run -n $envName python @pyArgs
    exit $LASTEXITCODE
}

# If TF installed (or not but we'll let the script fallback), run local python script
$pyArgs = @(
    "tools/train_model.py",
    "--csv", $CsvPath,
    "--out", $OutPath,
    "--epochs", $Epochs,
    "--batch-size", $BatchSize,
    "--quantize", $Quantize
)
Write-Log "Running training with the virtualenv Python (this may train TF model or sklearn fallback)."
python @pyArgs
$rc = $LASTEXITCODE

if ($rc -ne 0) {
    Write-Error "Training script exited with code $rc"
    exit $rc
}

# post-check: if .tflite exists
if (Test-Path $OutPath) {
    Write-Log "Success: wrote TFLite model to $OutPath"
} else {
    $joblibPath = [System.IO.Path]::ChangeExtension($OutPath, ".joblib")
    if (Test-Path $joblibPath) {
        Write-Log "TensorFlow was not available; trained sklearn fallback and wrote $joblibPath (no .tflite created)"
    } else {
        Write-Log "Training finished but no expected outputs found. Check the script output above for errors."
    }
}

Write-Log "Done."
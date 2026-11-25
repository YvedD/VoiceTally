param(
    [string]$ApkName = ""
)

$tag = "VoiceTally-AI"
$apkBestand = $null

# 1. Als Gradle de exacte bestandsnaam meegaf, zoek die eerst op
if ($ApkName -ne "") {
    if (Test-Path "release\$ApkName") {
        $apkBestand = Get-Item "release\$ApkName"
    } elseif (Test-Path "app\build\outputs\apk\release\$ApkName") {
        $apkBestand = Get-Item "app\build\outputs\apk\release\$ApkName"
    }
}

# 2. Fallback: pak de nieuwste APK in 'release' of 'app\build\outputs\apk\release'
if (-not $apkBestand) {
    $apkBestand = @(
        Get-ChildItem -Path "release" -Filter "*.apk" -ErrorAction SilentlyContinue
        Get-ChildItem -Path "app\build\outputs\apk\release" -Filter "*.apk" -ErrorAction SilentlyContinue
    ) | Sort-Object LastWriteTime -Descending | Select-Object -First 1
}

if (-not $apkBestand) {
    Write-Host "FOUT: Geen release APK gevonden om te uploaden!" -ForegroundColor Red
    exit 1
}

Write-Host "Automatisch uploaden van '$($apkBestand.Name)' naar GitHub Release '$tag'..." -ForegroundColor Cyan

gh release upload $tag $apkBestand.FullName --clobber

if ($LASTEXITCODE -eq 0) {
    Write-Host "GELUKT! '$($apkBestand.Name)' staat nu live onder https://github.com/YvedD/VoiceTally/releases/tag/VoiceTally-AI" -ForegroundColor Green
} else {
    Write-Host "FOUT bij het uploaden naar GitHub Release '$tag'." -ForegroundColor Red
    exit 1
}
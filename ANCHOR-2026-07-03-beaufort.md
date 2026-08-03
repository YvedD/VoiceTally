# Anchor: beaufort-axis-0-7 (2026-07-03)

Commit: 64cd63c
Tag: anchor/beaufort-2026-07-03

Beschrijving van de wijzigingen in deze anchor:

- Forceer de rechter verticale as (beaufort) naar een vaste schaal 0..7 zodat alleen de rechter-as wordt beïnvloed.
  - Implementatie: toegevoegd `createBeaufortLineLayer(maxBeaufort, beaufortColor)` in `VicoLineChartHelper.kt`.
  - Deze layer gebruikt een `CartesianLayerRangeProvider.fixed(0.0, 7.0)` voor de Y-range en is gekoppeld aan de rechter-as (end axis).

- Linker verticale as formatering: labels als gehele getallen (geen decimalen).
  - Implementatie: `createCountAxis()` gebruikt een `CartesianValueFormatter` met "%.0f".

- Rechter verticale as formatering: labels als gehele getallen en voldoende ticks (count=8) voor 0..7.
  - Implementatie: `createBeaufortAxis()` gebruikt integer formatter en itemPlacer count 8.

- Data-sanitatie: beaufort-waarden worden geclamped op max 7 voordat ze naar de chart-producer gestuurd worden.
  - Implementatie: in `DatabaseSoortOverzichtActiviteit.kt` (`updateChart`) wordt `beaufortData` gemapt naar `clampedBeaufort = beaufortData.map { it.coerceAtMost(7.0).toFloat() }`.

- UI: de overbodige knop `btnToonGrafieken` is verwijderd uit de layout-bestanden zodat het grafiek-tabblad voldoende is.
  - Bestanden aangepast: `app/src/main/res/layout/scherm_db_soort_overzicht.xml` en `app/src/main/res/layout-sw600dp/scherm_db_soort_overzicht.xml`.

Hoe te reproduceren / testen:

1. Bouw de debug APK:

```powershell
Set-Location -LiteralPath 'C:\AndroidApps\VoiceTally'
.\gradlew.bat assembleDebug --no-daemon
```

2. Installeer en start op een apparaat/emulator:

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

3. Open de app, navigeer naar "Soort Overzicht", kies een soort met wind-data en open het tabblad "Grafieken".
   - Controleer dat de linker as gehele aantallen toont.
   - Controleer dat de rechter as altijd 0..7 en dat de beaufort-lijn (wind) de blauwe kleur gebruikt.

Opmerking: deze anchor commit bevat alleen de wijzigingen voor de chart/axis/beaufort en layout verwijdering. Andere ongestage bestanden in de workspace zijn niet opgenomen in deze commit.

---

Als je wil dat ik deze tag ook naar een remote (bijv. origin) push, geef even aan welke remote en ik voer `git push origin anchor/beaufort-2026-07-03` uit.


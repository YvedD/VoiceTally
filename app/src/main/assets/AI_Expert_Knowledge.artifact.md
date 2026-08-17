# VT5 AI: Expert Knowledge Base

Dit document bevat de biologische en meteorologische regels die de hersenen van VoiceTally (BSI & Lite-Neural) aansturen. Het is een levend document dat wordt aangevuld met nieuwe inzichten uit de database-analyses.---

## 0. Interpretatie van de Prognose (%)

Het percentage in de app is een **Match-Score** die aangeeft hoe goed de huidige omstandigheden passen bij het historische profiel van een soort in de database.

*   **90% - 100% (Piek-condities):** Alle factoren (datum, wind, regionale stroom) staan op groen. Dit is een moment waarop de soort historisch gezien massaal vliegt.
*   **50% - 80% (Gunstig):** Sterke match met weersomstandigheden. De kans op waarnemingen is hoog, maar het is niet noodzakelijk een historische piekdag.
*   **15% - 40% (Mogelijk):** Matige match. Er is activiteit in de regio of de datum klopt, maar de lokale weersomstandigheden zijn niet ideaal (bijv. zijwind).
*   **< 15% (Onderdrukt):** Onwaarschijnlijke match. De AI verbergt deze soorten meestal om de lijst schoon te houden.

## 1. Meteorologische Vingerafdrukken (Peak Signatures)

De AI kijkt niet alleen naar het weer bij de telpost, maar analyseert de omstandigheden op 21 strategische referentiepunten in Europa voor de periode van **T-24u, T-48u en T-72u**.

### Noordelijke Corridor (Najaarstrek)
*   **Focus:** Vertrekweer in Scandinavië, Denemarken en Noord-Duitsland.
*   **Indicatoren:** Rugwind (N/NO), stijgende luchtdruk, helder weer na een frontpassage.
*   **Locaties:** Falsterbo (SE), Skagen (DK), Helgoland (DE), Texel (NL), etc.

### Zuidelijke Corridor (Voorjaarstrek)
*   **Focus:** Stuwing in Zuid-Europa en de Pyreneeën.
*   **Indicatoren:** Zuidelijke stroming (Z/ZW), gunstige thermiek in de ochtend.
*   **Locaties:** Tarifa (ES), Gibraltar (UK), Cap Gris-Nez (FR), Camargue (FR), etc.

## 2. Gilde-Specifieke Logica

### Raptors (Roofvogels)
*   **Trigger:** Thermiek (Temperatuurgradiënt) + afwezigheid van sterke zeebries (NO aan de kust).
*   **Tijdsvenster:** 10:00 - 16:00 uur.
*   **Krenten-factor:** Boost voor soorten als Bruine Kiekendief en Visarend bij specifieke luchtdruk-stijging.

### Long-legged Waders (Reigers & Ooievaars)
*   **Trigger:** "Wachtkamer-effect": Vertrek bij eerste opklaringen na langdurige regen of tegenwind.
*   **Tijdsvenster:** Vroege ochtend tot middag.
*   **Piek:** Augustus/September (Purperreiger, Lepelaar).

### Passerines (Zangvogels)
*   **Trigger:** Windstilte of lichte rugwind. Nachtelijke trek vertaalt zich in vroege ochtendwaarnemingen.
*   **Tijdsvenster:** 06:00 - 10:00 uur.
*   **Krenten-factor:** Hoge score voor zeldzamere trekkers (Roodkeelpieper) bij aanhoudende oostenwind.

## 3. Dynamische Berekeningen

*   **Solar Gate:** Harde blokkade voor dagtrekkers buiten de uren [ZonOpkomst - 1u] tot [ZonOndergang + 1u].
*   **Gisteren-Factor:** Logaritmische weging van de massa van de voorgaande dag als indicator voor een lopende "golf".
*   **Pressure Trend:** Delta-P over 48 uur als indicator voor vliegweer (stijgend) of stuwing (dalend).

---
*Laatst bijgewerkt: 2026-08-15 - Initialisatie van de Retroactieve Piek-Analyse.*

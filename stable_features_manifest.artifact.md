# VT5 Golden State Manifest - 23 Augustus 2026

Dit document dient als ankerpunt voor de stabiele functionaliteiten van de VT5 app. De onderstaande onderdelen zijn geverifieerd en mogen NIET meer gewijzigd worden zonder expliciete toestemming.

## 1. Teldag Verslagen (Reporting Engine)
- **Logica**: Gebruikt de 12-maands aggregatie (`getSpeciesMonthlyDistribution`) voor maximale visuele stabiliteit.
- **Normalisatie**: Elke curve wordt geschaald naar 1.0 (top van de Sparkline) om elke soort een volwaardig podium te geven.
- **Piek-detectie**: Exacte berekening van vensters (`[Start - End]`) gebaseerd op de 50% drempelwaarde van de historische top.
- **Marker**: Rode verticale indicator (1.5dp) op pixel-exacte positie op de 366dp schaal.

## 2. Layout Standaarden
- **Species Card**:
    - Links: Foto (65dp)
    - Midden-Boven: Naam, Aantal, Sterren (uitgelijnd).
    - Midden-Onder: Telposten breakdown, BpH & Giga-Norm (5 decimalen).
    - Piekregel: Oranje tekst met datums tussen rechte haakjes.
    - Bodem: Zelfstandige grafiek-zone (366dp x 45dp) op achtergrond `#222222`.
- **Kleurstelling**: Lijnkleur `#00FF00` (Fel Groen), Dikte `1dp`.

## 3. Database & Architectuur (Version 2)
- **Hoofdtelpost**: De allereerste telpost in `telpost_locaties.json` is het onwrikbare anker voor de 35km-cluster.
- **Migratie**: v1 naar v2 migratie is geverifieerd en behoudt alle geïmporteerde data.
- **Indexen**: Alle snelheid-indexen voor `waarnemingen` en `telling_headers` zijn officieel verankerd.

## 4. UI-Architectuur & Systeem-integratie
- **Edge-to-Edge**: Alle schermen (`scherm_*.xml`) gebruiken dwingend `android:fitsSystemWindows="true"`.
- **Geen Systeem-marges**: Handmatige `layout_marginTop` of `layout_marginBottom` in root-elementen zijn verboden. Android regelt zelf de veilige ruimte voor statusbalken en navigatiebalken.
- **Consistentie**: Interne marges (tussen componenten) blijven behouden voor de visuele flow, maar de buitenranden volgen de systeem-insets.

## 5. Telpost Beheer
- **Clustercirkel**: Ononderbroken lichtblauwe cirkel (35km) rond de hoofdtelpost.
- **Markers**: Klassieke blauwe markers voor alle posten.

---
*Verankerd in Git Tag: STABLE_REPORTING_V2*

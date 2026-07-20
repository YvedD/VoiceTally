# Concept VT-AI: Slimme Vogelvoorspellingen

Dit document beschrijft de technische mogelijkheden en het concept voor de integratie van zelflerende systemen binnen de VoiceTally app (Branch: `VT-AI`).

## 1. Doelstelling
Het creëren van een intelligent voorspellingsmodel dat:
1.  **Correlaties legt** tussen historische tellingen (Room DB) en weersomstandigheden (Open-Meteo).
2.  **Contextueel bewustzijn** heeft van de tijd van het jaar en geografische locatie.
3.  **Zeldzame soorten prioriteert** in de meldingen ("Smart Alerts"), zodat deze niet verdrinken in de massa-data van algemene soorten.
4.  **Zelflerend is** door on-device updates op basis van nieuwe waarnemingen.

## 2. Specifieke Meldingstypen ("Alerts")

De AI moet verschillende categorieën meldingen genereren om de gebruiker optimaal te informeren:

*   **A. Massa-migratie ("Top-dagen")**: Voorspelling van dagen met extreem hoge aantallen algemene soorten (bijv. >10.000 Vinken of Spreeuwen in het najaar). De AI herkent de specifieke weer-drivers (windrichting, luchtdrukval) die deze golven veroorzaken.
*   **B. Roofvogel-pieken**: Gerichte focus op thermiek- en windomstandigheden die gunstig zijn voor groepen Buizerds, Bruine Kiekendieven of zeldzamere Wespendieven.
*   **C. De "Krenten" (Zeldzaamheden)**: Slimme alerts voor soorten die ornithologisch gezien "mogelijk" zijn bij het huidige weerpatroon, zelfs als ze zelden voorkomen (bijv. Purperreiger, Zeearend).
*   **D. Dynamische Seizoensverschuiving**: De AI leert van trends; als door een zachte winter de migratie van een soort twee weken eerder begint dan vorig jaar, past het model zijn "window of opportunity" automatisch aan op basis van recente waarnemingen in de regio.

## 3. Architecturale Opties

### A. TensorFlow Lite (Gestructureerd ML)
Ideaal voor regressie en classificatie op basis van tabel-data.
*   **Model-type**: Recurrent Neural Network (RNN) of LSTM (Long Short-Term Memory) om tijdreeksen in het weer te begrijpen.
*   **Input Features**:
    *   Datum (sinus/cosinus voor circulariteit).
    *   Windvector (snelheid + richting op locatie én referentiepunten).
    *   Luchtdrukverloop en temperatuurgradiënten.

### B. On-Device LLM (Gemini Nano)
Gebruikmaken van de Google AI Edge SDK voor tekstuele redenering.
*   **Voordeel**: Kan complexe verbanden leggen zoals "Gestuwde migratie" door tekstuele uitleg te geven waarom een specifieke melding wordt verzonden.

## 4. Geavanceerde Analyse & Preprocessing

### Weer-tijdreeks (History Context)
Migratie is geen momentopname. De AI moet kijken naar de "supply chain" van vogels:
*   **Najaar (Noord-Noord-Oost)**: Analyse van het weer 2 à 3 dagen vooraf in Scandinavië en Noord-Duitsland. Als daar een 'opstopping' was door tegenwind en de wind draait nu naar NO, volgt er een piek op de telpost.
*   **Voorjaar (Zuid-Zuid-West)**: Idem voor locaties in de Ardennen of Noord-Frankrijk.

### Gestuwde Migratie (Remote Concentration)
De AI leert het fenomeen begrijpen waarbij vogels door wind van opzij (bijv. sterke ZW wind in het najaar) naar de kustlijn worden gedrukt.
*   **Techniek**: De AI correleert windgegevens van punten *ver weg* van de kust met de aantallen op de telpost. Als de AI ziet dat zijwind op referentiepunten leidt tot hogere concentraties lokaal, wordt dit meegewogen als "stuwing-factor".

## 5. Strategie voor Zeldzame Soorten

Om te voorkomen dat algemene soorten de AI domineren:
*   **Relative Probability**: Focus op de *stijging* van de kans (bijv. 20x hogere kans op Purperreiger dan normaal -> ALERT).
*   **Weighted Training**: Zeldzame waarnemingen krijgen een factor 100-500 gewicht in het leerproces.

## 6. Roadmap VT-AI Branch
1.  **Fase 1: Data Preparation**: Bouwen van de export-module (Room -> Training set met weer-history van 3 dagen).
2.  **Fase 2: Feature Engineering**: Integratie van vaste weer-referentiepunten (N/Z) in de achtergrond.
3.  **Fase 3: Model Ontwikkeling**: Training van een model dat 'stuwing' en 'seizoensverschuiving' begrijpt.
4.  **Fase 4: TFLite/Gemini Integratie**: UI integratie voor Smart Notifications.

## 7. Mogelijke extensies met AI

Naast de kern-voorspellingen liggen er nog diverse opportuniteiten om de AI breder in te zetten:

*   **A. Adaptieve UI (Dynamic Tile Sorting)**: De AI herordent de soort-tegels op het scherm op basis van wat de komende uren het meest waarschijnlijk is. De gebruiker hoeft minder te scrollen omdat de "verwachte soorten" bovenaan staan.
*   **B. Bayesian Fusion met BirdNET-GO**: De AI-voorspelling dient als een 'prior' (voorkennis) voor de audio-detectie. Als BirdNET een twijfelachtig signaal opvangt van een soort die de AI met 90% zekerheid voorspelt, kan de drempelwaarde voor automatische acceptatie van dat geluid tijdelijk verlaagd worden.
*   **C. Data Quality Guard (Uitschieter-detectie)**: De AI fungeert als een real-time validator. Als een gebruiker per ongeluk "1000 Purperreigers" invoert (typfout of spraakfout), ziet de AI dat dit ornithologisch onmogelijk is voor die context en vraagt om een extra bevestiging.
*   **D. Persoonlijke Tel-Coach**: De AI leert de patronen van de gebruiker zelf kennen. Het model kan bijvoorbeeld opmerken: *"Je ziet vaak minder Boomvalken bij tegenwind vergeleken met andere tellers op deze post; let vandaag extra op de hogere luchtlagen."*
*   **E. Crowd-Sourced Sentinel (Vroegtijdige Waarschuwing)**: Indien geanonimiseerde data gedeeld wordt tussen VT5-gebruikers, kan de AI van "Sessie A" een waarschuwing sturen naar "Sessie B" die 50km verderop ligt: *"Er is zojuist een golf Wespendieven gepasseerd op Post X, ze worden over ca. 45 min bij jou verwacht."*
*   **F. Automatische Metadata-verrijking**: De AI kan op basis van de weertrend en locatie automatisch velden invullen die nu handmatig moeten (bijv. trektype 'hoog' of 'gestuwd'), door correlaties te herkennen in hoe de vogels op de wind reageren.

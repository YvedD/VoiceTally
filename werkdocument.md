# Stappenplan voor Update README.md - VoiceTally

Dit document beschrijft de stappen om de `README.md` bij te werken met de recent toegevoegde functionaliteiten: **AI Forecast**, **BirdNET-GO integratie**, en **Master/Client samenwerking**.

## Fase 1: Voorbereiding & Basisstructuur Update
1.  **Inhoudsopgave bijwerken**:
    *   Nieuwe hoofdstukken (16 t/m 21) toevoegen.
    *   Links naar alle secties controleren.
2.  **Hoofdstuk 2 (Permissies)**: Aanvullen met nieuwe benodigde rechten (bv. Camera voor QR, Bluetooth voor HID-knoppen indien van toepassing).
3.  **Hoofdstuk 3 (SAF-mappen)**: De directory-lijst uitbreiden met nieuwe mappen (bv. `ai/`, `models/`, `db_backups/`).
4.  **Hoofdstuk 4 (Bestanden)**: De lijst met automatisch aangemaakte bestanden updaten (bv. AI modellen, database bestanden).

## Fase 2: Configuratie & Weer
1.  **Hoofdstuk 15 (Auto-Weather)**: Volledige toelichting op het ophalen van weerdata op basis van huidige GPS-locatie via Open-Meteo.
2.  **Hoofdstuk 21 (Instellingen)**: De nieuwe opties in het `InstellingenScherm` toelichten (bv. Opslagmodus: Room vs SAF, AI-voorkeuren, BirdNET-host).

## Fase 3: Telling & Spraak (Hoofdstuk 8, 9 & 11)
1.  **Soorttegels**: Nieuwe functionaliteit toelichten (bv. visuele indicatoren, nieuwe klikacties).
2.  **Richting-terug Aliassen**: 
    *   Uitleggen hoe een alias voor "terug" (bv. "vink terug") werkt.
    *   Toelichten dat dit de waarneming direct in de tegenovergestelde trekrichting plaatst.

## Fase 4: AI 3-daagse Prognose (Hoofdstuk 16)
1.  **Beschrijving**: Uitleggen dat de app nu een migratie-prognose kan doen voor de komende 3 dagen.
2.  **Gebruik**: Waar vind je de knop? Uitleg over het overzicht per dag (wind, temp, kans per soortgilde).
3.  **Techniek**: De 'Corridor' berekening (weer stroomopwaarts).

## Fase 5: BirdNET-GO Integratie (Hoofdstuk 17)
1.  **Configuratie**: Host/IP instellen, Auto-discovery.
2.  **Interface**: Pending Ticker en de detectielijst in het telscherm.
3.  **Mapping**: Automatische conversie naar VT5-soorten.

## Fase 6: Master / Client Samenwerking (Hoofdstuk 18)
1.  **Concept**: Master modus (lokale server) vs Client modus.
2.  **Koppeling**: QR-code scannen (vermeld Camera permissie).
3.  **Real-time Sync**: Waarnemingen delen tussen toestellen.

## Fase 7: AI Optimalisatie & Enrichment (Hoofdstuk 19)
1.  **Functionaliteit**: Database verrijken met historische weerdata voor betere AI training.
2.  **Training**: Uitleggen hoe de AI 'leert' van de persoonlijke database van de gebruiker.

---
*Status: In voorbereiding*

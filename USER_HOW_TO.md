# Gebruikershandleiding: Veldkoppeling & P2P Synchronisatie (VoiceTally VT5)

Welkom bij de handleiding voor het opzetten en gebruiken van het lokale veldnetwerk in **VoiceTally (VT5)**. Deze handleiding is bedoeld voor veltellers en master-tellers in open gebieden zonder internet.

---

## 1. Wat heb je nodig in het veld?
- **Master-toestel:** Het hoofdtoestel (meestal van de hoofdhouder/coördinator van de telpost) dat alle tellingen ontvangt en aggregeert.
- **Teller-toestellen:** De smartphones van de individuele veltellers.
- **Geen internet vereist voor de koppeling:** De onderlinge verbinding werkt volledig lokaal via Google Nearby Connections (Bluetooth/Wi-Fi P2P). *Let op: Indien u op een toestel gebruikmaakt van online spraakherkenning voor spraakinvoer, werkt dat via de eigen mobiele data van dat toestel; dit staat volledig los van het onderlinge telnetwerk.*

---

## 2. Stap-voor-stap Koppelen via QR-Code (Binnen 1 seconde verbonden)

Om te voorkomen dat u in een druk veld handmatig naar apparaten hoeft te zoeken, werkt VoiceTally met een supersnelle QR-code koppeling:

1. **Op de Master:**
   - Open het menu **Netwerk / P2P Beheer** op de Master.
   - Start de **Master Server (Advertising)**.
   - Er verschijnt automatisch een unieke **QR-code** op het scherm van de Master.

2. **Op het Teller-toestel:**
   - Open de VoiceTally app en ga naar **Verbinden met Master**.
   - Tik op **Scan QR-code**.
   - Richt de camera op de QR-code op het scherm van de Master.
   - De app scant de code, leest de beveiligde verbindingssleutel en maakt direct verbinding.

3. **Bevestiging:**
   - Zodra de verbinding tot stand is gebracht, verschijnt er op zowel de Master als de Teller een groen verbindingsicoon met de naam van de telpost.

---

## 3. Werken in het Veld & Automatische Herverbinding

- **Doorgaan bij wegvallen van verbinding:** 
  Als u zich in een uitgestrekt veld bevindt en het signaal tijdelijk wegvalt (bijv. achter een dijk of duin), kunt u **ononderbroken doorgaan met tellen**. VoiceTally slaat alle tellingen lokaal veilig op.
- **Automatische Synchronisatie:** 
  Zodra u weer binnen bereik komt van de Master, herstelt de verbinding automatisch. De app stuurt direct alle gemiste tellingen versleuteld door naar de Master. U hoeft hier niets voor te doen!
- **Batterijbesparing:** 
  De app gebruikt een geoptimaliseerde Foreground Service met een rustige statusnotificatie, zodat de verbinding actief blijft, zelfs wanneer uw scherm tijdelijk op standby staat.

---

## 4. Einde van de Telling

- Aan het einde van de teldag beëindigt de Master de sessie.
- Alle data is gecentraliseerd op de Master en kan direct geëxporteerd of doorgestuurd worden zodra er weer internetbereik is.

# BirdNET-GO testparcours voor VoiceTally

Laatst bijgewerkt: 2026-05-20

## Doel

Dit testparcours is bedoeld om de BirdNET-GO integratie in `VoiceTally` gefaseerd, veilig en controleerbaar te implementeren.

De nadruk ligt op:
- **tablet-only UI** in het tellingscherm
- **real-time detecties** zonder historiekpolling als einddoel
- **timestamps** voor resolutie en deduplicatie
- **soortmapping** tussen BirdNET-GO en VT5
- **checkbox -> waarneming -> record**
- **geen regressie** op bestaande telling/partials/finals/tiles/backups

> Belangrijk: in `app/build.gradle.kts` zijn testtaken momenteel uitgeschakeld. Daarom is dit traject vooral gebaseerd op **handmatige functionele tests**, **gerichte build-validatie** en later **veldtests**.

---

## Overzicht van de testfasen

1. **Fase A - Huidige tablet-UI en toggle valideren**
2. **Fase B - BirdNET-paneel ombouwen naar echte detectielijst-UI**
3. **Fase C - IP/hostconfiguratie en connectiecheck**
4. **Fase D - Live detecties + timestamp + deduplicatie**
5. **Fase E - Mapping BirdNET-soort -> VT5-soort**
6. **Fase F - Checkbox -> waarneming -> record**
7. **Fase G - Regresstesten op bestaande tellingflow**
8. **Fase H - Veldtest met Raspberry Pi / BirdNET-GO**

Elke fase mag pas naar de volgende gaan als de acceptatiecriteria gehaald zijn.

---

# Fase A - Huidige tablet-UI en toggle valideren

## Doel
Controleren dat het nieuwe BirdNET-paneel visueel en functioneel correct in het tellingscherm zit, zonder impact op partials.

## Relevante onderdelen
- `app/src/main/res/layout-sw600dp/scherm_telling.xml`
- `app/src/main/java/com/yvesds/vt5/features/telling/TellingScherm.kt`
- `app/src/main/java/com/yvesds/vt5/features/telling/TellingUiManager.kt`

## Testgevallen

### A1. Paneel alleen op tablet
**Stap**
- Open het tellingscherm op een tablet of tablet-emulator (`sw600dp+`).
- Open hetzelfde scherm op een smartphone-formaat.

**Verwacht**
- Tablet: BirdNET-knop zichtbaar.
- Smartphone: BirdNET-knop niet bruikbaar / niet zichtbaar.
- Smartphone-layout blijft compact en ongewijzigd bruikbaar.

### A2. Toggle opent en sluit paneel
**Stap**
- Tik op de BirdNET-knop.
- Tik nogmaals.

**Verwacht**
- Eerste tik: `cardBirdNetDetections` wordt zichtbaar.
- Tweede tik: `cardBirdNetDetections` verdwijnt opnieuw.
- Geen crash, geen layoutsprong buiten de topzone.

### A3. Partials blijven intact
**Stap**
- Laat partials in de partials-card verschijnen.
- Open/sluit BirdNET-paneel meerdere keren.

**Verwacht**
- Bestaande partials blijven zichtbaar.
- Scrollpositie van partials blijft logisch.
- Geen clearing van partial-data.

### A4. Rotatie / Activity-heraanmaak
**Stap**
- Open BirdNET-paneel.
- Forceer Activity-heraanmaak (rotatie indien relevant, background/foreground, of systeem kill/recreate).

**Verwacht**
- De open/dicht-status blijft consistent met de bewaarde state.
- De partials/finals-lijsten blijven behouden.

## Acceptatiecriteria fase A
- Tablet-only gedrag klopt.
- Toggle werkt stabiel.
- Partials gaan nooit verloren.
- App buildt zonder resourcefouten.

---

# Fase B - BirdNET-paneel ombouwen naar echte detectielijst-UI

## Doel
De placeholder vervangen door een echte lijstweergave voor detecties.

## Aanbevolen UI-structuur
Per rij minimaal:
- tijd (`timestamp`)
- soortnaam (`common name` of mapped display name)
- confidence
- checkbox / actie-indicator
- status (optioneel: toegevoegd / niet gemapt / duplicate)

## Testgevallen

### B1. Lege lijsttoestand
**Stap**
- Open BirdNET-paneel zonder datafeed.

**Verwacht**
- Paneel toont nette lege toestand (bv. “Nog geen live detecties”).
- Geen placeholder-ontwikkelaarstekst meer.

### B2. Testdata in lijst
**Stap**
- Injecteer 5-10 dummy-detecties in-memory.

**Verwacht**
- Lijst rendert netjes.
- Nieuwe items verschijnen onder/boven volgens gekozen ontwerp.
- Layout breekt niet bij langere soortnamen.

### B3. Scrollgedrag
**Stap**
- Injecteer 50+ detecties.

**Verwacht**
- Lijst blijft performant.
- Nieuwste items zijn logisch zichtbaar.
- Geen invloed op partials-card.

## Acceptatiecriteria fase B
- BirdNET-paneel gebruikt echte lijst-UI.
- Leeg, gevuld en veel items gedragen zich correct.

---

# Fase C - IP/hostconfiguratie en connectiecheck

## Doel
Voor activatie van de live-feed moet de gebruiker het IP-adres van de Raspberry Pi kunnen invoeren of wijzigen.

## Nodige inputvelden
Minimaal:
- host/IP

Aanbevolen extra velden:
- poort
- endpoint-pad
- time-out in seconden

## Testgevallen

### C1. Popup opent vanuit BirdNET-knop of activatieflow
**Stap**
- Activeer BirdNET-live.

**Verwacht**
- Er verschijnt eerst een popup voor BirdNET-hostconfiguratie.
- Huidige opgeslagen waarden zijn vooraf ingevuld.

### C2. Geldig IP opslaan
**Stap**
- Vul een geldig lokaal IP in en bewaar.

**Verwacht**
- Waarde wordt persistent opgeslagen.
- Bij heropenen staat exact dezelfde waarde ingevuld.

### C3. Ongeldig IP blokkeren
**Stap**
- Vul lege string, tekst zonder hoststructuur of verboden waarden in.

**Verwacht**
- Duidelijke validatiefout.
- Geen feedstart.

### C4. Raspberry niet bereikbaar
**Stap**
- Gebruik geldig maar onbereikbaar IP.

**Verwacht**
- Duidelijke foutmelding.
- App crasht niet.
- Partials/finals/telling blijven onaangetast.

## Acceptatiecriteria fase C
- Gebruiker kan host veilig instellen.
- Foute invoer of onbereikbaarheid veroorzaakt geen regressie.

---

# Fase D - Live detecties + timestamp + deduplicatie

## Doel
Enkel **nieuwe** BirdNET-detecties tonen, voorzien van timestamp, met correcte deduplicatie.

## Verplicht datamodel per detectie
Minimaal voorzien:
- `sourceTimestamp`
- `displayTimestamp`
- `commonName`
- `scientificName` (indien beschikbaar)
- `confidence`
- `sourceEventId` (indien beschikbaar)
- `isAccepted`
- `isResolved`
- `resolvedRecordLocalId`

## Deduplicatie-strategie (aanbevolen)
Volgorde van voorkeur:
1. officiële BirdNET-event-ID
2. fallback: `timestamp + scientificName + confidence`
3. uiterste fallback: `timestamp + commonName + confidence`

## Testgevallen

### D1. Alleen nieuwe detecties sinds activatie
**Stap**
- Activeer live-feed.
- Laat BirdNET al bestaande oude detecties hebben.

**Verwacht**
- Oude historiek verschijnt niet.
- Enkel nieuwe detecties sinds activatie worden getoond.

### D2. Dubbele detectie met zelfde soort
**Stap**
- Stuur twee detecties van dezelfde soort met verschillende timestamps.

**Verwacht**
- Beide blijven zichtbaar als aparte regels.

### D3. Exact duplicaat
**Stap**
- Stuur dezelfde detectie 2x opnieuw.

**Verwacht**
- Slechts één regel zichtbaar.
- Geen dubbele waarneming later mogelijk.

### D4. Out-of-order timestamps
**Stap**
- Laat detecties in verkeerde volgorde binnenkomen.

**Verwacht**
- UI blijft stabiel.
- Sorteerlogica is voorspelbaar en gedocumenteerd.

## Acceptatiecriteria fase D
- Timestamps werken betrouwbaar.
- Duplicaten worden niet dubbel weergegeven.
- Meerdere detecties van dezelfde soort op verschillende tijdstippen blijven onderscheiden.

---

# Fase E - Mapping BirdNET-soort -> VT5-soort

## Doel
BirdNET-soorten correct mappen naar soorten in VoiceTally.

## Aanbevolen matchvolgorde
1. **Wetenschappelijke naam exact**
2. Nederlandse soortnaam exact
3. Alias-/mappingtabel
4. Geen match -> markeer als niet resolvable

## Testgevallen

### E1. Exacte scientific-name match
**Stap**
- Lever een detectie met scientific name die exact voorkomt in VT5-dataset.

**Verwacht**
- Detectie krijgt correct `speciesId` in de app.

### E2. Nederlandse naam match
**Stap**
- Lever detectie in Nederlandse common names.

**Verwacht**
- Correcte soortmatch indien gelijk aan VT5-soortnaam.

### E3. Geen match
**Stap**
- Lever onbekende soortnaam / afwijkende schrijfwijze.

**Verwacht**
- Regel blijft zichtbaar.
- Wordt gemarkeerd als niet gemapt.
- Checkbox is uitgeschakeld of toont duidelijke fout bij poging.

### E4. Ambigue match
**Stap**
- Lever naam die meerdere mogelijke soorten kan opleveren.

**Verwacht**
- Detectie wordt niet automatisch foutief gekoppeld.
- App kiest ofwel veilig geen match, of gebruikt expliciete mappingregel.

## Acceptatiecriteria fase E
- Scientific-name route werkt eerst en betrouwbaar.
- Geen foutieve auto-koppelingen bij ambiguïteit.

---

# Fase F - Checkbox -> waarneming -> record

## Doel
Een gedetecteerde en gemapte BirdNET-regel moet via checkbox effectief als waarneming in de lopende telling komen.

## Verwachte flow
1. Detectie zichtbaar
2. Soort mapped naar VT5 `speciesId`
3. Gebruiker vinkt checkbox aan
4. Record wordt opgebouwd via bestaande recordflow
5. Detectie wordt `resolved`
6. `recordLocalId` wordt teruggekoppeld naar de detectierij

## Testgevallen

### F1. Één detectie accepteren
**Stap**
- Vink een geldige detectie aan.

**Verwacht**
- Soort verschijnt/incrementeert in tiles.
- Finale logregel verschijnt.
- Pending record ontstaat.
- Detectie krijgt status `resolved`.

### F2. Zelfde soort twee keer, andere timestamps
**Stap**
- Accepteer twee BirdNET-detecties van dezelfde soort op andere tijdstippen.

**Verwacht**
- Twee aparte detecties kunnen correct verwerkt worden.
- Record- en timestampkoppeling blijft juist.

### F3. Checkbox dubbel aantikken
**Stap**
- Tik 2x snel op dezelfde checkbox.

**Verwacht**
- Maximaal één record aangemaakt.
- Geen dubbele telling door race condition.

### F4. Record-aanmaak faalt
**Stap**
- Forceer fout in recordflow (bv. geen actieve tellingId).

**Verwacht**
- Duidelijke foutstatus op de detectie.
- Detectie blijft onopgelost.
- Geen half-gecreëerde state.

## Acceptatiecriteria fase F
- Checkbox creëert precies één waarneming.
- Resolved-status koppelt correct terug naar detectie.
- Timestamps blijven bruikbaar voor onderscheid tussen meerdere detecties.

---

# Fase G - Regresstesten op bestaande tellingflow

## Doel
Zeker zijn dat BirdNET geen bestaande functionaliteit beschadigt.

## Regressiegebieden
- speech partials
- finals
- tiles
- annotaties
- afronden/upload
- pending records / backup
- master/client
- settings openen/sluiten

## Testgevallen

### G1. Spraakherkenning blijft werken
**Stap**
- Gebruik spraak terwijl BirdNET-paneel open staat.

**Verwacht**
- Partials blijven verschijnen.
- Finals blijven correct aangemaakt.
- Geen vermenging van BirdNET en speech-data.

### G2. Tile taps blijven werken
**Stap**
- Verhoog soorten via tegels terwijl BirdNET-paneel open staat.

**Verwacht**
- Tile-aggregatie blijft correct.
- Geen UI-locks of crashes.

### G3. Annotaties op finals blijven werken
**Stap**
- Open annotaties op een finals-logregel na BirdNET-gebruik.

**Verwacht**
- Annotatiescherm blijft bruikbaar.

### G4. Backup/pending flow blijft intact
**Stap**
- Maak BirdNET-records aan en sluit/heropen de app.

**Verwacht**
- Pending records blijven consistent.
- Geen corrupte envelope.

### G5. Master/client blijft intact
**Stap**
- Gebruik master/client samenwerking en BirdNET-paneel in dezelfde sessie.

**Verwacht**
- Geen conflict in logweergave of records.
- Eventuele BirdNET-records blijven gewone tellingrecords.

## Acceptatiecriteria fase G
- Bestaande kernfunctionaliteit werkt ongewijzigd.
- Geen regressie op logs, tiles, backup of samenwerking.

---

# Fase H - Veldtest met Raspberry Pi / BirdNET-GO

## Doel
Echte test op locatie met BirdNET-GO op Raspberry Pi 4B.

## Minimale veldsetup
- Android-tablet met VoiceTally
- Raspberry Pi 4B met BirdNET-GO actief
- zelfde netwerk
- BirdNET-GO endpoint bekend
- logging ingeschakeld voor testnotities

## Veldtesten

### H1. Verbinding op locatie
- Controleer IP wijzigen
- Controleer connectiecheck
- Controleer time-out-gedrag

### H2. Realtime detecties in de praktijk
- Laat BirdNET meerdere detecties genereren
- Observeer sortering, timestamps en duplicates

### H3. Rustige vs drukke periode
- Test 5 min zonder detecties
- Test periode met veel detecties kort na elkaar

### H4. Wegvallend netwerk
- Schakel wifi tijdelijk uit / onstabiel netwerk
- Controleer herstelgedrag

### H5. Einde sessie
- Sluit telling netjes af
- Controleer of BirdNET-records correct mee in de telling zitten

## Acceptatiecriteria fase H
- Feed werkt stabiel genoeg voor veldgebruik.
- Geen dataverlies bij korte netwerkonderbreking.
- Detecties kunnen betrouwbaar als tellingrecords worden toegevoegd.

---

# Testdata die best op voorhand voorzien wordt

## Dummy-detecties voor UI-test
Maak minimaal deze testset:

1. `Boerenzwaluw`, timestamp T1, confidence 0.93
2. `Boerenzwaluw`, timestamp T2, confidence 0.89
3. `Hirundo rustica`, timestamp T3, confidence 0.97
4. onbekende soort, timestamp T4, confidence 0.88
5. exacte duplicate van item 1
6. lange soortnaam voor layouttest

## Mappingtestset
Voorzie voorbeelden van:
- exact NL match
- exact scientific match
- alias match
- geen match
- ambigue match

---

# Praktische build-/validatiecyclus per wijziging

Na elke niet-triviale wijziging minimaal uitvoeren:

```powershell
Set-Location 'C:\AndroidApps\VoiceTally'
.\gradlew.bat :app:assembleDebug --no-daemon --console=plain
```

Bij resource/layout-wijzigingen ook telkens handmatig controleren:
- tabletlayout
- smartphone-layout
- BirdNET-knop zichtbaar / verborgen zoals bedoeld
- partials nog intact

---

# Aanbevolen implementatievolgorde

1. **Fase A volledig afronden**
2. **Fase B eerst met dummydata**
3. **Fase C IP-popup + connectiecheck**
4. **Fase D timestamps + dedupe**
5. **Fase E mappinglaag**
6. **Fase F checkbox -> recordflow**
7. **Fase G regressies**
8. **Fase H veldtest op echte BirdNET-GO**

---

# Go/No-Go criteria voor productiegebruik

## Go
- Geen verlies van partials/finals
- BirdNET-detecties worden niet dubbel aangemaakt
- Multiple detections van dezelfde soort zijn correct onderscheidbaar via timestamp
- Mapping is veilig en fouttolerant
- Checkbox creëert exact één record
- Veldtest succesvol

## No-Go
- Duplicaten worden niet betrouwbaar tegengehouden
- Soorten worden foutief gekoppeld
- Detecties verdwijnen of worden “resolved” zonder echt record
- BirdNET-paneel verstoort spraak- of tellingflow

---

# Beslissingen die nog expliciet vastgelegd moeten worden

1. Wat is de exacte BirdNET-GO broninterface?
   - push / event stream / polling endpoint

2. Welke dedupe-sleutel wordt definitief?
   - event ID
   - timestamp + scientific name
   - timestamp + name + confidence

3. Hoe gaan we om met niet-gemapte soorten?
   - alleen tonen
   - checkbox blokkeren
   - handmatige mapping later

4. Wat is de standaard hoeveelheid per checkbox?
   - altijd `1`
   - of later configureerbaar

---

## Samenvatting

Dit testparcours laat toe om BirdNET-GO **stapsgewijs** te implementeren zonder de bestaande tellingflow te destabiliseren.

De kernprioriteiten zijn:
- **tablet-only UI veilig houden**
- **timestamps correct gebruiken**
- **duplicates uitsluiten**
- **species mapping veilig oplossen**
- **checkbox -> recordflow robuust maken**
- **regressies vroeg detecteren**


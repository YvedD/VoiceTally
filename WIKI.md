# VoiceTally Wiki

## Wat is VoiceTally?

**VoiceTally** is een Android-app voor het registreren van vogeltrekwaarnemingen in het veld.  
De app is gemaakt om **snel, robuust en hands-on** te werken tijdens een telling:

- waarnemingen invoeren via **spraak**
- aantallen aanvullen via **tegels**
- waarnemingen **annoteren**
- tellingen **lokaal bewaren**
- tellingen uiteindelijk **uploaden naar trektellen.nl**

De app is in de eerste plaats gebouwd rond het scherm `TellingScherm`: daar komt alles samen wat tijdens een actieve telling nodig is.

---

## Opzet van de app

De globale flow van de app ziet er als volgt uit:

1. **(Her)Installatie**
   - permissies geven
   - opslagmap kiezen via SAF
   - serverdata downloaden
2. **Hoofdscherm**
   - nieuwe telling starten
   - in een lopende sessie invoegen als client
   - alarm beheren
   - tellingen bewerken
   - instellingen wijzigen
3. **Metadata**
   - telpost, datum, tijd, tellers en weersituatie voorbereiden
4. **Telling**
   - soorten kiezen
   - waarnemingen invoeren via spraak of tegels
   - annotaties toevoegen
   - totalen opvolgen
5. **Afronden**
   - telling afsluiten
   - lokaal bewaren en uploaden
   - eventueel een vervolgtelling starten

### Belangrijkste schermen

#### 1. InstallatieScherm
Voor de eerste configuratie:

- permissies controleren
- SAF-map kiezen
- mappenstructuur aanmaken in `Documents/VT5/`
- login testen
- JSON-data van de server downloaden
- alias-index opbouwen

#### 2. HoofdActiviteit
Het hoofdscherm is het startpunt van de app. Van hieruit kan je:

- de installatie openen
- metadata voor een telling invullen
- **rechtstreeks invoegen in een lopende master/client-sessie**
- tellingen bewerken
- exports opkuisen
- instellingen openen

#### 3. MetadataScherm
Hier bereid je de telling inhoudelijk voor:

- telpost
- datum en starttijd
- teller(s)
- wind, bewolking, neerslag, temperatuur, zicht en luchtdruk
- vrije weeropmerking

VoiceTally kan hier ook automatisch weergegevens ophalen.

![Metadatascherm](app/src/main/images/metadatascherm.jpg)

#### 4. TellingScherm
Dit is het operationele hart van de app. Tijdens een telling kan je hier:

- soorten selecteren
- spraakinvoer gebruiken
- aantallen handmatig via tegels invoeren
- finals en partials opvolgen
- annotaties toevoegen
- huidige stand bekijken
- master/client-samenwerking starten
- offline import/export gebruiken
- afronden en uploaden

![TellingScherm](app/src/main/images/tellingscherm.jpg)

---

## Kernfunctionaliteit van de app

### 1. Spraakinvoer
De app is geoptimaliseerd voor snelle invoer van vogelnamen in het veld.

- spreek soortnaam + aantal uit
- partials tonen tussenresultaten
- finals tonen bevestigde registraties
- herkende waarnemingen worden meteen aan de telling toegevoegd

### 2. Tegels en snelle invoer
Elke geselecteerde soort verschijnt als tegel in het telscherm.

- tikken op een tegel verhoogt de telling
- recente soorten versnellen de selectie
- niet-geselecteerde soorten kunnen tijdens de telling toegevoegd worden

### 3. Annotaties
Een geregistreerde waarneming kan verder verfijnd worden met:

- leeftijd
- geslacht
- kleed
- opmerkingen
- richting / tegenrichting

Dat maakt de telling niet alleen sneller, maar ook rijker in detail.

### 4. Aliassen voor spraak
Als een naam slecht herkend wordt, kan je een spraakvariant als alias opslaan.  
Dat is nuttig voor:

- dialect- of streekvarianten
- fonetische varianten
- verkorte benamingen

### 5. Lokale opslag en herstel
VoiceTally bewaart gegevens niet alleen voor upload, maar ook voor veiligheid:

- back-ups per record
- een volledige telling-envelope voor herstel
- archiefbestanden in `Documents/VT5/counts/`
- exports in `Documents/VT5/exports/`

Daardoor kan de app ook beter omgaan met tijdelijke fouten of onderbroken sessies.

### 6. Huidige stand
Tijdens de telling kan je een overzicht openen van:

- totaal per soort
- hoofdrichting
- tegenrichting

### 7. Auto-weather
De app kan op basis van locatie actuele weergegevens ophalen en invullen in de metadata.

### 8. Upload naar trektellen.nl
De telling wordt op het einde als envelope opgebouwd en geüpload.  
Bij netwerkproblemen blijft de data lokaal bewaard zodat je later opnieuw kan proberen.

---

## Bestandsstructuur die de gebruiker merkt

Na installatie werkt VoiceTally typisch met deze mappen:

```text
Documents/VT5/
├── assets/
├── binaries/
├── serverdata/
├── counts/
└── exports/
```

Praktisch betekent dit:

- **assets/**: aliassen, annotaties en configuratie
- **binaries/**: geoptimaliseerde runtimebestanden
- **serverdata/**: soorten, telposten, codes en gebruikersinfo
- **counts/**: afgewerkte of bewaarde tellingen
- **exports/**: exportbestanden en logs

---

## Master/client mogelijkheden (bèta release)

De master/client-functionaliteit is bedoeld om **meerdere tellers met meerdere Android-toestellen** aan dezelfde telling te laten werken.

### Rolverdeling

#### Master
De master:

- start of beheert de telling
- draait lokaal de sessie/server
- ontvangt waarnemingen van clients
- verwerkt die waarnemingen in dezelfde telling
- blijft de **enige** die finaal uploadt naar trektellen.nl

#### Client
De client:

- koppelt aan een actieve master
- stuurt nieuwe waarnemingen door
- kan bij verbindingsproblemen lokaal bufferen
- kan indien nodig offline exporteren

### Wat werkt er in de huidige bèta?

Op basis van de huidige codebase zijn dit de belangrijkste bèta-mogelijkheden:

- samenwerken via **lokaal Wi-Fi-netwerk of hotspot**
- master-server **on-demand** starten vanuit het telscherm via **Clients**
- client koppelen via:
  - IP-adres + PIN
  - automatisch ontdekte masters op het netwerk
  - QR-scan voor Wi-Fi + pairing
- live binnenkomende client-waarnemingen op de master
- statusbalken voor master en client
- synchronisatie van tegels van master naar client
- **offline export/import** als fallback
- nette sessie-afsluiting
- **master-handover** wanneer de master afrondt en geen vervolgtelling start

### Praktische workflow

#### Scenario A — master start de samenwerking tijdens een telling

1. Start een telling zoals gewoonlijk.
2. Tik in het telscherm op **Clients** / **Add clients**.
3. Kies **Start als master**.
4. De master toont een PIN en pairing-opties.
5. Clients verbinden via IP/PIN, netwerkdetectie of QR.
6. Nieuwe client-waarnemingen verschijnen live in de telling van de master.

#### Scenario B — client voegt in op een lopende sessie

1. Open op het hoofdscherm **Invoegen in lopende sessie**.
2. De app opent het telscherm als client.
3. Tik op **Koppelen met master**.
4. Verbind via PIN/IP of scan de QR-stroom.
5. Na koppeling worden waarnemingen naar de master gestuurd.

### Offline fallback

Als live netwerkverkeer niet lukt:

- client kan een JSON-export maken
- master kan die later importeren

Dat is belangrijk voor telposten met een onbetrouwbare hotspot of tijdelijke netwerkuitval.

### Waarom deze opzet belangrijk is

De master/client-opzet is bewust zo ontworpen dat er maar **één bron van waarheid** is voor de uiteindelijke upload: de master.  
Dat verkleint de kans op dubbele uploads, conflicten en onduidelijkheid over welke telling “de echte” is.

---

## Mogelijke valkuilen

Deze pagina is bewust **niet limitatief**, maar dit zijn de meest voorkomende aandachtspunten.

### 1. Android-installatie van APK’s
Moderne Android-versies en zeker sommige Samsung-toestellen kunnen streng reageren op sideloaded APK’s.

- sta installatie uit onbekende bron toe voor de gebruikte browser of bestandsapp
- verwacht waarschuwingen van Play Protect

### 2. Permissies vergeten
VoiceTally leunt op meerdere permissies:

- microfoon voor spraak
- locatie voor auto-weather en Wi-Fi scan
- opslagtoegang via SAF
- camera voor QR-scans

Als één van deze ontbreekt, lijken delen van de app “niet te werken”, terwijl het eigenlijk een permissieprobleem is.

### 3. SAF-map of mappenstructuur niet correct ingesteld
Als `Documents/VT5/` niet correct gekozen of aangemaakt is, kunnen downloads, back-ups en tellingen mislukken of onvolledig zijn.

### 4. Geen serverdata gedownload
Zonder soorten, telposten en codes van de server is de app functioneel onvolledig.

### 5. Spraakherkenning blijft contextgevoelig
Spraak werkt snel, maar niet foutloos:

- moeilijke soortnamen kunnen slecht herkend worden
- woorden die op Engelse woorden lijken geven soms problemen
- namen die met een telwoord beginnen, zijn extra gevoelig voor misinterpretatie

Daarom zijn aliassen en handmatige correctie belangrijk.

### 6. Master/client werkt alleen goed binnen dezelfde lokale context
Voor de bèta moet je praktisch uitgaan van:

- hetzelfde Wi-Fi-netwerk, of
- één toestel als hotspot waarop de andere toestellen zitten

Als toestellen niet op hetzelfde lokale netwerk zitten, zal pairing vaak mislukken.

### 7. Controleer vóór elke sessie expliciet de rol
De master/client-bèta is nog in beweging. Controleer daarom telkens bewust:

- welk toestel master is
- welke toestellen client zijn
- of de client effectief aan de juiste master gekoppeld is

Doe dit zeker vóór de echte telling start.

### 8. Alleen de master uploadt finaal
Dit is geen fout, maar wel een belangrijk werkingsprincipe.  
Als de master wegvalt of te vroeg afgesloten wordt, moet je goed weten wat lokaal nog bewaard is en of een offline export/import of handover nodig is.

### 9. Wi-Fi- en QR-flows vragen soms extra Android-bevestiging
In de praktijk kan Android nog bijkomende handelingen vragen:

- toestemming voor locatie bij Wi-Fi scan
- bevestiging van een Wi-Fi-suggestie
- cameratoestemming voor QR-scans

Hou dus rekening met extra systeemvensters tijdens het koppelen.

### 10. Bèta betekent: testen vóór veldgebruik
De master/client-functionaliteit is bruikbaar, maar blijft een bèta-release.  
Aanbevolen werkwijze:

1. test vooraf thuis of op de telpost
2. oefen minstens één volledige master/client-sessie
3. test ook het fallback-pad via offline export/import

---

## Aanbevolen gebruik in de praktijk

Voor een stabiele sessie in het veld:

1. installeer en configureer alle toestellen vooraf
2. download serverdata vóór vertrek
3. test spraak en aliassen op de soorten die je vaak gebruikt
4. spreek vooraf af welk toestel de master wordt
5. test pairing vóór de telling start
6. gebruik bij twijfel de offline export/import als reserveplan

---

## Samenvatting

VoiceTally combineert **snelle veldinvoer**, **lokale veiligheid**, **spraakgestuurde tellingen** en in de bèta ook **samenwerking tussen meerdere toestellen**.

De kern van de app is eenvoudig:

- eerst correct installeren
- daarna metadata invullen
- vervolgens snel tellen via spraak en tegels
- en tenslotte veilig afronden en uploaden

De master/client-bèta maakt de app krachtiger voor drukke telposten, maar vraagt ook meer discipline:

- juiste rolverdeling
- stabiel lokaal netwerk
- aandacht voor permissies
- een fallback-plan als de verbinding wegvalt

Wie dat goed voorbereidt, haalt het meeste uit VoiceTally.

# VT5 Master/Client-handleiding

Deze handleiding legt stap voor stap uit hoe u in VT5 een **master/client-sessie** opzet voor een gezamenlijke telling met meerdere toestellen.

De uitleg begint bij het opzetten van het **master-toestel**, gaat daarna verder met de noodzakelijke stappen op de **client-toestellen**, en behandelt ook wat er gebeurt tijdens het tellen, bij het verlaten van de sessie, bij het beëindigen van de samenwerking en bij een mogelijke **handover** wanneer een client de masterrol overneemt.

---

## Inhoud

1. [Wat is een master/client-sessie?](#1-wat-is-een-masterclient-sessie)
2. [Belangrijkste principes vooraf](#2-belangrijkste-principes-vooraf)
3. [Wat u vooraf in orde brengt](#3-wat-u-vooraf-in-orde-brengt)
4. [Stap 1 — Het master-toestel opzetten](#4-stap-1--het-master-toestel-opzetten)
5. [Stap 2 — Het master-toestel klaarzetten om clients te koppelen](#5-stap-2--het-master-toestel-klaarzetten-om-clients-te-koppelen)
6. [Stap 3 — Wat elke client precies moet doen](#6-stap-3--wat-elke-client-precies-moet-doen)
7. [Waarom elke client twee verschillende QR-codes moet scannen](#7-waarom-elke-client-twee-verschillende-qr-codes-moet-scannen)
8. [Wat er gebeurt zodra de client verbonden is](#8-wat-er-gebeurt-zodra-de-client-verbonden-is)
9. [Hoe een client een waarneming annoteert](#9-hoe-een-client-een-waarneming-annoteert)
10. [Wat het effect van annotaties is op de telling](#10-wat-het-effect-van-annotaties-is-op-de-telling)
11. [Exit-flows: een sessie verlaten of beëindigen](#11-exit-flows-een-sessie-verlaten-of-beëindigen)
12. [Handover-flows: wanneer een client de masterrol overneemt](#12-handover-flows-wanneer-een-client-de-masterrol-overneemt)
13. [Offline fallback: export/import](#13-offline-fallback-exportimport)
14. [Praktische aandachtspunten en veelvoorkomende fouten](#14-praktische-aandachtspunten-en-veelvoorkomende-fouten)
15. [Korte checklist in het veld](#15-korte-checklist-in-het-veld)

---

## 1. Wat is een master/client-sessie?

Een master/client-sessie is een manier om **met meerdere Android-toestellen tegelijk aan dezelfde telling te werken**.

- **Eén toestel is de master**
  - dit toestel beheert de sessie;
  - ontvangt de waarnemingen van alle clients;
  - verwerkt die in de actieve telling;
  - en doet uiteindelijk de centrale afhandeling.

- **Alle andere toestellen zijn clients**
  - zij tellen mee;
  - voeren waarnemingen in via tegels, spraak of bestaande invoerflows;
  - en sturen hun waarnemingen door naar de master.

Kort gezegd:

- de **master** is het coördinerende toestel;
- de **clients** zijn de meewerkende toestellen.

---

## 2. Belangrijkste principes vooraf

Voor u begint, zijn dit de belangrijkste spelregels van een master/client-telling:

1. **Eén telling, één master**
   - Per gezamenlijke sessie is er altijd exact één master-toestel.

2. **Clients tellen mee, maar de master blijft centraal**
   - Clients voeren observaties in.
   - De master ontvangt en verwerkt die observaties.

3. **De client is geen losse parallelle telling**
   - Een client maakt deel uit van dezelfde gezamenlijke sessie.
   - Het is dus niet de bedoeling dat elke teller “maar wat apart telt” zonder koppeling.

4. **De client moet eerst op het juiste netwerk zitten**
   - Zonder netwerkverbinding met de master kan er geen live samenwerking zijn.

5. **Bij QR-koppeling zijn twee scans nodig**
   - Eerst de **Wi‑Fi QR**.
   - Daarna de **pairing-QR**.
   - Die twee QR-codes hebben elk een ander doel en zijn allebei nodig in de standaard QR-flow.

6. **Een client kan de sessie niet verlaten zolang er nog onbevestigde waarnemingen wachten**
   - VT5 verhindert dat om gegevensverlies te vermijden.

7. **Annotaties horen mee bij de waarneming**
   - Wanneer een client een waarneming annoteert, worden die annotatiegegevens mee doorgestuurd.
   - De master krijgt die informatie dus ook binnen.

---

## 3. Wat u vooraf in orde brengt

Voor een vlotte opstart doet u best eerst deze voorbereiding.

### 3.1 Op alle toestellen

Controleer dat:

- VT5 correct geïnstalleerd is;
- de app al eens volledig is doorlopen qua basisconfiguratie;
- de noodzakelijke gegevens lokaal beschikbaar zijn;
- de teller weet welk toestel de master zal zijn.

### 3.2 Kies vooraf het master-toestel

Kies bij voorkeur een toestel:

- met een stabiele batterij;
- dat centraal bij de telling blijft;
- en dat tijdens de sessie niet voortdurend van gebruiker wisselt.

### 3.3 Zorg voor netwerk

Alle toestellen moeten op hetzelfde lokale netwerk uitkomen.

Dat kan op twee manieren:

- via een bestaand Wi‑Fi-netwerk;
- of via de hotspot van het master-toestel.

Als u met hotspot werkt, is het belangrijk dat de clients **eerst correct met die hotspot verbinden** vóór ze de eigenlijke pairing doen.

---

## 4.0 Het master-toestel opzetten

De volledige sessie begint altijd op het master-toestel.

### 4.1 Zet het toestel in master-modus gedurende een lopende sessie/telling

1. Open VT5.
2. Ga naar **Invullen telpostgegevens**.
3. Voer alle gegevens in zoals **telpost** (noodzakelijk), **type telling** (noodzakelijk), druk op **Auto** om het lokale weerbeeld op te halen, vul eventueel het vak **Opmerkingen weer/Opmerkingen** in (optioneel)
4. Kies **Verder**.
5. Kies eventueel nu reeds de te verwachten soorten of hergebruik **Alle recente** soorten
6. Kies **OK**, zolang er géén cliënts aangemeld zijn verloopt de telling net zoals in de 'solo-modus'
7. Je komt nu terecht in het Tellingscherm, kies bovenaan het **cirkeltje met kruis erin** om cliënts toe te voegen gedurende een lopende sessie/telling
8. Je krijgt de melding **Samenwerken starten** -> **OK**
9. Kies **Start als master**
10. Scan de wifi-signalen, en maak je keuze (je kan deze gegevens ook opslaan voor hergebruik via hetzelfde netwerk op een ander tijdstip/datum met hetzelfde toestel.
11. Bevestig Wifi **Gereed**
12. Kies onderaan **Sluiten**
13. Hiermee is de setup van de master ingesteld.
14. Als er medetellers actief aan deze lopende sessie/telling willen deelnemen kies dan bovenaan de knop **Cliënts**
15. Laat de collega tellers **beide QR-codes inscannen, eerst de Wi-Fi QR, vervolgens de QR-code voor Cliënts**
16. Bevestig het deelnemen van een client in de melding die je nadien krijgt!

Hiermee vertelt u aan VT5 dat dit toestel:

- clients mag ontvangen;
- de sessie mag beheren;
- en als centraal punt zal werken.

### 4.2 Doe daarna de telling verder zoals gewoonlijk

## 4.3 Wat de twee QR-codes doen

De master toont twee verschillende QR-codes omdat ze twee verschillende taken hebben:

1. **Wi‑Fi QR**
   - helpt de client om eerst op het juiste netwerk/hotspot uit te komen.

2. **Pairing-QR**
   - bevat de gegevens om daarna de client met de lopende mastersessie te koppelen.

Dat onderscheid is belangrijk:

- netwerktoegang alleen is **niet genoeg**;
- pairing alleen is **ook niet genoeg** als de client nog niet op het juiste netwerk zit.

### 4.4 Wat de master verder nog moet doen

De master moet tijdens het koppelen:

- het venster open houden zolang clients nog moeten aansluiten;
- de PIN of QR-codes beschikbaar houden;
- en nieuwe clientverzoeken toestaan wanneer VT5 daar om vraagt.

Pas nadat de clients gekoppeld zijn, kan de gezamenlijke telling vlot live lopen.

---

## 5.0 Wat elke client precies moet doen

Elke client moet zijn toestel apart voorbereiden en koppelen.

Doe deze stappen **op elk client-toestel afzonderlijk**.

1. Open VT5.
2. Ga naar **Invoegen in lopende sessie**.
3. Scan beide QR-codes afzonderlijk, beginnende met de **Wi-Fi QR**
4. Nadat ook de onderste QR-code ingescant is de **Client** actief deelnemer aan de lopende sessie/telling.

Daarmee staat dit toestel klaar om:

- observaties door te sturen;
- en niet zelf de centrale sessie te beheren.

### 5.1 Ga ook op de client gewoon verder met de normale tellingflow

De client gaat vervolgens ook automatisch naar het telscherm.
In het telscherm van de cliënt zijn ineens ook alle reeds waargenomen soorten, op de master, actief !

Praktisch betekent dit:

1. alle waarnemingen van een cliënt worden doorgezonden en verwerkt door de master;
2. nieuwe soorten, door een cliënt waargenomen en ingevoerd, die niet actief zijn op het telscherm van de master worden daar nu ook weergegeven;
3. annotaties bij waarnemingen op een cliënt worden ook correct doorgegeven en verwerkt door de master.

### 6.1 Wat de client doet

De client kan daarna:

- waarnemingen invoeren via tegels;
- waarnemingen invoeren via spraak;
- bestaande invoerflows gebruiken;
- en observaties annoteren.

### 6.2 Wat de master doet

De master:

- ontvangt clientwaarnemingen;
- verwerkt ze in dezelfde lopende telling;
- werkt totalen en records bij;
- en toont clientinvoer in de log met het prefix **[C]**.

Dat betekent:

- clientwaarnemingen zijn geen aparte bijlage;
- ze worden geïntegreerd in de telling zelf.

### 6.3 Wat als de verbinding even wegvalt?

Als de client tijdelijk geen verbinding heeft:

- bewaart VT5 de nog niet bevestigde waarnemingen lokaal;
- probeert de app automatisch opnieuw te verbinden;
- en worden wachtende waarnemingen later opnieuw doorgestuurd.

Dat is precies waarom een client de sessie niet zomaar mag verlaten terwijl er nog onbevestigde observaties openstaan.

---

## 7. Hoe een client een waarneming annoteert

Een client kan, net zoals in een gewone telling, een waarneming annoteren.

### 7.1 Wat annoteren betekent

Annoteren betekent dat u extra informatie toevoegt aan een waarneming, zoals bijvoorbeeld:

- **geslacht**;
- **leeftijd**;
- **kleed**;
- **opmerkingen**;
- en andere beschikbare annotatievelden.

### 7.2 Hoe een client dat praktisch doet

De precieze invoer blijft dezelfde als elders in VT5:

1. voer eerst de waarneming in;
2. open daarna het annotatiescherm voor die waarneming door in het groene kader op de gewenste waarneming te tikken;
3. kies de gewenste annotaties;
4. vul eventueel opmerkingen of aanvullende gegevens in;
5. bevestig de annotatie.

### 7.3 Welke soorten annotaties er beschikbaar zijn

De annotatie-opties komen uit de annotatieconfiguratie van VT5 en kunnen onder meer velden bevatten zoals:

- leeftijd;
- geslacht;
- kleed;
- opmerkingen;
- en andere voorgedefinieerde keuzes.
- een werkend kompas om een afwijkende vliegrichting door te geven
- 
### 7.4 Wat er technisch met die annotatie gebeurt

Wanneer de client bevestigt:

- worden de annotatiegegevens gekoppeld aan die waarneming;
- gaan die gegevens mee in de clientobservatie;
- en worden ze doorgestuurd naar de master.

---

## 8. Wat het effect van annotaties is op de telling

Dit is een cruciaal punt voor gebruikers.

### 8.1 Annotaties blijven niet enkel op de client

Annotaties die een client invult:

- blijven **niet** alleen lokaal zichtbaar;
- ze worden ook gemeld aan de master.

De master ontvangt dus niet alleen “er is een waarneming”, maar ook de meegegeven details van die waarneming.

### 8.2 Impact op de sessie/telling

De impact is als volgt:

1. **De waarneming maakt deel uit van de gezamenlijke telling**
   - De master verwerkt ze als onderdeel van de actieve telling.

2. **De annotatie hoort bij diezelfde waarneming**
   - Velden zoals geslacht, leeftijd, kleed en opmerkingen worden mee doorgestuurd.

3. **De master krijgt die annotatie-info ook**
   - De master heeft dus zicht op wat de client heeft meegegeven.

4. **Annotaties veranderen niet automatisch de tellingstotalen, tenzij de waarneming zelf anders is**
   - Een annotatie is in de eerste plaats extra context bij de observatie.
   - Het aantal wordt bepaald door de waarneming zelf.

5. **Als een client een observatie bijwerkt, moet de master die update mee verwerken**
   - Een latere aanpassing aan de waarneming of haar annotaties hoort dus nog steeds bij dezelfde gedeelde sessie.

### 8.3 Wat de mastergebruiker daarvan merkt

De mastergebruiker merkt dit doordat:

- clientwaarnemingen in de mastertelling terechtkomen;
- clientinvoer herkenbaar is in de log;
- en de ontvangen observatiegegevens niet losstaan van de actieve telling.

Samengevat:

> **Annotaties van een client tellen mee als informatie bij de gedeelde waarneming en worden ook aan de master gemeld.**

---

## 9. Exit-flows: een sessie verlaten of beëindigen

In een gezamenlijke telling zijn er meerdere manieren waarop iemand uit de sessie kan gaan. Het is belangrijk dat gebruikers het onderscheid goed begrijpen.

### 9.1 Exit-flow A — een client verlaat zelf de telling

Dit is de flow wanneer **één client** wil stoppen, terwijl de master en eventuele andere clients verder kunnen werken.

#### Verloop

1. De client tikt op **Verlaat telling**.
2. VT5 controleert of er nog onbevestigde waarnemingen wachten.

#### Mogelijkheid 1: er wachten nog onbevestigde waarnemingen

Dan mag de client **nog niet** vertrekken.

De reden is eenvoudig:

- VT5 wil voorkomen dat nog niet bevestigde clientwaarnemingen verloren gaan.

De gebruiker moet dan:

- wachten tot de master de waarnemingen bevestigd heeft;
- of eerst zorgen dat de verbinding zich herstelt.

#### Mogelijkheid 2: alles is bevestigd

Dan kan de client wel veilig vertrekken.

Daarna:

1. verstuurt VT5 een nette “leave”-melding naar de master;
2. stopt de clientverbinding;
3. krijgt de gebruiker daarna nog een vervolgvraag.

### 9.2 Wat een client na het verlaten van de sessie kan doen

Na een geslaagde client-exit biedt VT5 twee vervolgstappen:

1. **App sluiten**
2. **App herstarten**

#### App sluiten

Kies dit als de teller klaar is en het toestel gewoon uit de sessie mag verdwijnen.

#### App herstarten

Kies dit als de teller opnieuw proper wil beginnen vanaf het hoofdscherm, zonder restanten van de clientsessie.

### 9.3 Exit-flow B — de master beëindigt de samenwerking handmatig

Dit is de flow wanneer de **master zelf beslist om de samenwerking stop te zetten**.

#### Verloop

1. De master opent het client/samenwerkingsvenster.
2. De master kiest **Beëindig samenwerking**.
3. VT5 vraagt bevestiging.
4. Bij bevestiging verstuurt de master een **sessie-einde** naar alle verbonden clients.

#### Gevolg voor de clients

Alle clients:

- krijgen het signaal dat de samenwerking beëindigd is;
- stoppen als client van die sessie;
- en worden losgekoppeld van de master.

Dit is dus een **centrale beëindiging** vanuit de master.

### 9.4 Exit-flow C — de master rondt de telling af

Wanneer de master de telling normaal afrondt, is de situatie iets subtieler.

Er zijn dan twee mogelijke vervolgen:

1. de master start zelf een vervolgtelling;
2. of de master verlaat de telpost zonder zelf verder te gaan.

Dat tweede geval leidt tot een **handover-flow** voor de clients. Die wordt hieronder apart uitgelegd.

### 9.5 Exit-flow D — teruggaan of scherm sluiten terwijl er nog pending observaties zijn

Ook dit is belangrijk:

- als de client nog onbevestigde waarnemingen heeft;
- dan blokkeert VT5 het nette verlaten van de sessie.

De app doet dat bewust om te vermijden dat gegevens verdwijnen vóór de master ze ontvangen heeft.

---

## 10. Handover-flows: wanneer een client de masterrol overneemt

Een handover is **niet hetzelfde** als “samenwerking beëindigen”.

Bij een handover stopt de oude master, maar krijgt een client de kans om de rol over te nemen en een vervolgtelling te starten.

### 10.1 Wanneer een handover gebeurt

Een handover is bedoeld voor de situatie waarin:

- de master de huidige telling correct heeft afgerond;
- de oude master de telpost verlaat;
- en de telling op de telpost toch moet doorgaan via iemand anders.

### 10.2 Wat de clients dan zien

De clients krijgen een dialoog in de stijl van:

- **Master verlaat de telpost**

Daarin wordt uitgelegd dat:

- de master de telling heeft afgerond;
- de telpost verlaat;
- en dat een client de masterfunctie kan overnemen voor een vervolgtelling.

### 10.3 Handover-flow A — client accepteert de overname

Als een client kiest voor **Ja, overnemen**:

1. stopt het toestel met de oude clientverbinding;
2. wordt de oude clientsessie opgeruimd;
3. schakelt het toestel naar **master-modus**;
4. start VT5 de nieuwe masterflow;
5. opent de app het metadata-/startscherm voor een **vervolgtelling**;
6. de eindtijd van de vorige telling kan daarbij als startpunt voor de nieuwe telling gebruikt worden.

Praktisch gevolg:

- dit toestel wordt de nieuwe master voor de volgende telling;
- de oude master is dan uit beeld;
- en de telling kan op de telpost verdergezet worden.

### 10.4 Handover-flow B — client weigert de overname

Als de client kiest voor **Nee, niet overnemen**:

1. stopt de oude clientsessie;
2. wordt de koppeling met de oude master opgeruimd;
3. keert het toestel terug uit die sessieflow.

Die client neemt dan dus **niet** de rol van nieuwe master op.

### 10.5 Belangrijk onderscheid tussen handover en sessie-einde

Dit is het onderscheid:

- **Beëindig samenwerking**
  - de master stopt de samenwerking;
  - clients worden losgekoppeld;
  - er is geen overnamevraag nodig.

- **Handover**
  - de oude master stopt;
  - maar minstens één client kan de rol van nieuwe master opnemen;
  - bedoeld voor een vervolgtelling op dezelfde telpost.

---

## 11. Offline fallback: export/import

Niet elke situatie laat stabiele live samenwerking toe. Daarom bestaat er ook een fallback.

### 11.1 Wanneer gebruikt u dit?

Gebruik offline export/import wanneer:

- de netwerkverbinding te onstabiel is;
- een client niet live kon koppelen;
- of u observaties achteraf toch nog naar de master wilt overbrengen.

### 11.2 Flow op de client

1. Kies **Offline export**.
2. VT5 maakt een exportbestand van de lokale observaties.
3. Deel dat bestand via een geschikte methode.

### 11.3 Flow op de master

1. Kies **Offline import**.
2. Selecteer het ontvangen bestand.
3. Laat VT5 de records inlezen.

Dit is geen vervanging van de live master/client-flow, maar wel een nuttige noodoplossing.

---

## Praktische aandachtspunten en veelvoorkomende fouten

### Een client scant alleen de pairing-QR

Gevolg:

- de client mist mogelijk nog de juiste netwerkverbinding.

Oplossing:

- laat de client eerst de **Wi‑Fi QR** scannen;
- en daarna pas de **pairing-QR**.

### Een client wil vertrekken terwijl er nog waarnemingen wachten

Gevolg:

- VT5 laat het verlaten niet toe.

Oplossing:

- wacht op bevestiging door de master;
- of herstel eerst de verbinding.

### De master sluit te vroeg het koppelvenster

Gevolg:

- late clients missen mogelijk de actuele PIN/QR-flow.

Oplossing:

- houd het mastervenster open totdat alle noodzakelijke clients gekoppeld zijn;
- of open het opnieuw en genereer zo nodig een nieuwe PIN.

### Gebruikers denken dat annotaties alleen lokaal zijn

Dat is onjuist.

Annotaties van een client:

- horen bij de waarneming;
- worden meegestuurd;
- en worden ook aan de master gemeld.

### Gebruikers verwarren “sessie beëindigen” met “handover”

Onthoud:

- **sessie beëindigen** = samenwerking stopzetten;
- **handover** = een client kan de masterrol overnemen voor een vervolgtelling.

---

## Korte checklist in het veld

### Voor de master

- [ ] Zet VT5 in **Master-modus** via het cirkeltje met het kruis erin bovenaan het tellingscherm gedurende een lopende sessie/telling
- [ ] Open **Clients**
- [ ] Controleer PIN, Wi‑Fi QR en pairing-QR
- [ ] Laat nieuwe clientverzoeken toe

### Voor elke client

- [ ] Zet VT5 in **Client-modus** via het hoofdscherm **Invoegen in lopende sessie**
- [ ] **Scan eerst de Wi‑Fi QR**
- [ ] **Scan daarna de pairing-QR**
- [ ] Controleer of de status **verbonden met master** toont
- [ ] Het tellingscherm word geopend met alle reeds waargenomen soorten bij de master
- [ ] Voer de waarnemingen in via spraakinvoer of via de soorten tegels
- [ ] 
### Tijdens de telling

- [ ] Gebruik VT5 normaal voor waarnemingen
- [ ] Gebruik annotaties waar nodig
- [ ] Onthoud dat annotaties ook naar de master gaan
- [ ] Verlaat de sessie pas wanneer alles bevestigd is

### Bij einde of wissel

- [ ] Laat clients netjes vertrekken via **Verlaat telling**
- [ ] Gebruik **Beëindig samenwerking** als de master alles centraal stopt
- [ ] Gebruik de **handover-flow** als een client de masterrol moet overnemen

---

## Samenvatting in één alinea

Een correcte VT5 master/client-sessie begint altijd met het opzetten van de **master**, daarna koppelt u elke **client** afzonderlijk. In de QR-flow moet een client **twee verschillende QR-codes** scannen: **eerst de Wi‑Fi QR en daarna de pairing-QR**. Tijdens de telling stuurt elke client zijn waarnemingen live naar de master, inclusief eventuele **annotaties** zoals geslacht, leeftijd, kleed en opmerkingen. Die annotaties blijven dus niet alleen lokaal, maar worden **ook aan de master gemeld** en maken deel uit van de gedeelde telling. Bij het stoppen zijn er verschillende flows: een client kan de sessie netjes verlaten, de master kan de samenwerking centraal beëindigen, of er kan een **handover** plaatsvinden waarbij een client de masterrol overneemt voor een vervolgtelling.

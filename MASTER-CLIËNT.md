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

## 4. Stap 1 — Het master-toestel opzetten

De volledige sessie begint altijd op het master-toestel.

### 4.1 Zet het toestel in master-modus

1. Open VT5.
2. Ga naar **Instellingen**.
3. Zoek het onderdeel **Samenwerken (master-client)**.
4. Kies **Master**.

Hiermee vertelt u aan VT5 dat dit toestel:

- clients mag ontvangen;
- de sessie mag beheren;
- en als centraal punt zal werken.

### 4.2 Start daarna de telling zoals gewoonlijk

Ga vervolgens terug en start de telling via de normale tellingflow.

Met andere woorden:

1. open de metadata/telpost-invoer;
2. vul de telling in zoals u dat in solo-modus ook zou doen;
3. ga door tot u in het actieve **telscherm** zit.

Belangrijk:

- u zet **niet eerst alle clients op** en pas daarna de telling op de master;
- u start **eerst de mastertelling**;
- daarna maakt u de sessie open voor clients.

---

## 5. Stap 2 — Het master-toestel klaarzetten om clients te koppelen

Zodra de telling loopt op de master:

1. open in het telscherm de knop **Clients**;
2. VT5 opent het koppelvenster voor de master;
3. daar ziet u de gegevens die nodig zijn om clients te laten aansluiten.

### 5.1 Wat de master daar toont

In dit venster ziet de master onder meer:

- een **PIN-code**;
- een **pairing-QR**;
- een **Wi‑Fi QR**;
- en een overzicht van verbonden clients zodra die aansluiten.

### 5.2 Wat de PIN doet

De PIN-code dient om de client toe te laten tot de sessie.

Die PIN:

- is tijdelijk;
- hoort bij de huidige pairing-sessie;
- en wordt gebruikt wanneer de client zich aanmeldt bij de master.

De master kan indien nodig:

- een nieuwe PIN genereren;
- en zo de vorige code ongeldig maken voor nieuwe koppelingen.

### 5.3 Wat de twee QR-codes doen

De master toont twee verschillende QR-codes omdat ze twee verschillende taken hebben:

1. **Wi‑Fi QR**
   - helpt de client om eerst op het juiste netwerk/hotspot uit te komen.

2. **Pairing-QR**
   - bevat de gegevens om daarna de client met de lopende mastersessie te koppelen.

Dat onderscheid is belangrijk:

- netwerktoegang alleen is **niet genoeg**;
- pairing alleen is **ook niet genoeg** als de client nog niet op het juiste netwerk zit.

### 5.4 Wat de master verder nog moet doen

De master moet tijdens het koppelen:

- het venster open houden zolang clients nog moeten aansluiten;
- de PIN of QR-codes beschikbaar houden;
- en nieuwe clientverzoeken toestaan wanneer VT5 daar om vraagt.

Pas nadat de clients gekoppeld zijn, kan de gezamenlijke telling vlot live lopen.

---

## 6. Stap 3 — Wat elke client precies moet doen

Elke client moet zijn toestel apart voorbereiden en koppelen.

Doe deze stappen **op elk client-toestel afzonderlijk**.

### 6.1 Zet het toestel in client-modus

1. Open VT5.
2. Ga naar **Instellingen**.
3. Zoek **Samenwerken (master-client)**.
4. Kies **Client**.

Daarmee staat dit toestel klaar om:

- met een master te verbinden;
- observaties door te sturen;
- en niet zelf de centrale sessie te beheren.

### 6.2 Start ook op de client de tellingflow

De client gaat vervolgens ook naar het telscherm.

Praktisch betekent dit:

1. open de telling via de normale flow;
2. zorg dat u in het actieve telscherm zit;
3. tik daar op **Koppelen met master**.

Pas vanuit dat scherm start de effectieve koppelprocedure.

### 6.3 Open het client-koppelvenster

In het koppelvenster kan de client:

- de master scannen via QR;
- of in sommige situaties handmatig IP en PIN invullen.

Voor de **volledige QR-flow** in VT5 geldt echter:

1. eerst de **Wi‑Fi QR** scannen;
2. daarna de **pairing-QR** scannen.

### 6.4 De correcte volgorde voor de client

De juiste volgorde is:

1. **Scan de Wi‑Fi QR**
   - de client krijgt daarmee de netwerk/hotspotgegevens;
   - VT5 kan vervolgens de verbinding met het juiste Wi‑Fi-netwerk aanvragen;
   - op sommige toestellen moet Android die Wi‑Fi-verbinding nog bevestigen.

2. **Wacht tot de client op het juiste netwerk zit**
   - dit is essentieel;
   - zonder die netwerkstap kan live koppeling niet goed werken.

3. **Scan daarna de pairing-QR**
   - pas nu worden master-IP, poort en PIN voor de sessie ingevuld;
   - daarna kan VT5 effectief proberen koppelen met de master.

### 6.5 Wat u op het scherm ziet als het lukt

Wanneer de koppeling geslaagd is, toont de clientstatus:

- **Client ▪ verbonden met master**

Vanaf dat moment kan de teller gewoon beginnen tellen.

---

## 7. Waarom elke client twee verschillende QR-codes moet scannen

Dit punt is zo belangrijk dat het apart herhaald wordt.

### 7.1 Er zijn echt twee verschillende QR-codes

De master toont:

1. een **Wi‑Fi QR**;
2. een **pairing-QR**.

Dat zijn **niet** twee versies van dezelfde code.

Ze hebben elk een eigen functie:

- de ene is voor het netwerk;
- de andere voor de sessiekoppeling.

### 7.2 Waarom één QR-code niet volstaat

Als een client alleen de pairing-QR scant:

- dan kent het toestel wel de mastergegevens;
- maar het zit mogelijk nog niet op het juiste netwerk;
- en dan kan de live verbinding alsnog mislukken.

Als een client alleen de Wi‑Fi QR scant:

- dan zit het toestel misschien wél op het juiste netwerk;
- maar het is nog niet gekoppeld aan de lopende telling;
- en het kan dus nog geen geldige sessie starten.

### 7.3 Daarom is de juiste volgorde noodzakelijk

Voor de QR-flow moet de client dus steeds:

1. **eerst de Wi‑Fi QR scannen**;
2. **daarna de pairing-QR scannen**.

Schakel dus niet over naar tellen na slechts één scan.

Pas na beide scans is de client volledig klaar voor de gezamenlijke sessie.

---

## 8. Wat er gebeurt zodra de client verbonden is

Na een succesvolle koppeling werkt de client in de praktijk bijna zoals in solo-modus, maar met één groot verschil:

- de waarnemingen blijven niet alleen lokaal;
- ze worden doorgegeven aan de master.

### 8.1 Wat de client doet

De client kan daarna:

- waarnemingen invoeren via tegels;
- waarnemingen invoeren via spraak;
- bestaande invoerflows gebruiken;
- en observaties annoteren.

### 8.2 Wat de master doet

De master:

- ontvangt clientwaarnemingen;
- verwerkt ze in dezelfde lopende telling;
- werkt totalen en records bij;
- en toont clientinvoer in de log met het prefix **[C]**.

Dat betekent:

- clientwaarnemingen zijn geen aparte bijlage;
- ze worden geïntegreerd in de telling zelf.

### 8.3 Wat als de verbinding even wegvalt?

Als de client tijdelijk geen verbinding heeft:

- bewaart VT5 de nog niet bevestigde waarnemingen lokaal;
- probeert de app automatisch opnieuw te verbinden;
- en worden wachtende waarnemingen later opnieuw doorgestuurd.

Dat is precies waarom een client de sessie niet zomaar mag verlaten terwijl er nog onbevestigde observaties openstaan.

---

## 9. Hoe een client een waarneming annoteert

Een client kan, net zoals in een gewone telling, een waarneming annoteren.

### 9.1 Wat annoteren betekent

Annoteren betekent dat u extra informatie toevoegt aan een waarneming, zoals bijvoorbeeld:

- **geslacht**;
- **leeftijd**;
- **kleed**;
- **opmerkingen**;
- en andere beschikbare annotatievelden.

### 9.2 Hoe een client dat praktisch doet

De precieze invoer blijft dezelfde als elders in VT5:

1. voer eerst de waarneming in;
2. open daarna het annotatiescherm voor die waarneming;
3. kies de gewenste annotaties;
4. vul eventueel opmerkingen of aanvullende gegevens in;
5. bevestig de annotatie.

### 9.3 Welke soorten annotaties er beschikbaar zijn

De annotatie-opties komen uit de annotatieconfiguratie van VT5 en kunnen onder meer velden bevatten zoals:

- leeftijd;
- geslacht;
- kleed;
- opmerkingen;
- en andere voorgedefinieerde keuzes.

### 9.4 Wat er technisch met die annotatie gebeurt

Wanneer de client bevestigt:

- worden de annotatiegegevens gekoppeld aan die waarneming;
- gaan die gegevens mee in de clientobservatie;
- en worden ze doorgestuurd naar de master.

---

## 10. Wat het effect van annotaties is op de telling

Dit is een cruciaal punt voor gebruikers.

### 10.1 Annotaties blijven niet enkel op de client

Annotaties die een client invult:

- blijven **niet** alleen lokaal zichtbaar;
- ze worden ook gemeld aan de master.

De master ontvangt dus niet alleen “er is een waarneming”, maar ook de meegegeven details van die waarneming.

### 10.2 Impact op de sessie/telling

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

### 10.3 Wat de mastergebruiker daarvan merkt

De mastergebruiker merkt dit doordat:

- clientwaarnemingen in de mastertelling terechtkomen;
- clientinvoer herkenbaar is in de log;
- en de ontvangen observatiegegevens niet losstaan van de actieve telling.

Samengevat:

> **Annotaties van een client tellen mee als informatie bij de gedeelde waarneming en worden ook aan de master gemeld.**

---

## 11. Exit-flows: een sessie verlaten of beëindigen

In een gezamenlijke telling zijn er meerdere manieren waarop iemand uit de sessie kan gaan. Het is belangrijk dat gebruikers het onderscheid goed begrijpen.

### 11.1 Exit-flow A — een client verlaat zelf de telling

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

### 11.2 Wat een client na het verlaten van de sessie kan doen

Na een geslaagde client-exit biedt VT5 twee vervolgstappen:

1. **App sluiten**
2. **App herstarten**

#### App sluiten

Kies dit als de teller klaar is en het toestel gewoon uit de sessie mag verdwijnen.

#### App herstarten

Kies dit als de teller opnieuw proper wil beginnen vanaf het hoofdscherm, zonder restanten van de clientsessie.

### 11.3 Exit-flow B — de master beëindigt de samenwerking handmatig

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

### 11.4 Exit-flow C — de master rondt de telling af

Wanneer de master de telling normaal afrondt, is de situatie iets subtieler.

Er zijn dan twee mogelijke vervolgen:

1. de master start zelf een vervolgtelling;
2. of de master verlaat de telpost zonder zelf verder te gaan.

Dat tweede geval leidt tot een **handover-flow** voor de clients. Die wordt hieronder apart uitgelegd.

### 11.5 Exit-flow D — teruggaan of scherm sluiten terwijl er nog pending observaties zijn

Ook dit is belangrijk:

- als de client nog onbevestigde waarnemingen heeft;
- dan blokkeert VT5 het nette verlaten van de sessie.

De app doet dat bewust om te vermijden dat gegevens verdwijnen vóór de master ze ontvangen heeft.

---

## 12. Handover-flows: wanneer een client de masterrol overneemt

Een handover is **niet hetzelfde** als “samenwerking beëindigen”.

Bij een handover stopt de oude master, maar krijgt een client de kans om de rol over te nemen en een vervolgtelling te starten.

### 12.1 Wanneer een handover gebeurt

Een handover is bedoeld voor de situatie waarin:

- de master de huidige telling correct heeft afgerond;
- de oude master de telpost verlaat;
- en de telling op de telpost toch moet doorgaan via iemand anders.

### 12.2 Wat de clients dan zien

De clients krijgen een dialoog in de stijl van:

- **Master verlaat de telpost**

Daarin wordt uitgelegd dat:

- de master de telling heeft afgerond;
- de telpost verlaat;
- en dat een client de masterfunctie kan overnemen voor een vervolgtelling.

### 12.3 Handover-flow A — client accepteert de overname

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

### 12.4 Handover-flow B — client weigert de overname

Als de client kiest voor **Nee, niet overnemen**:

1. stopt de oude clientsessie;
2. wordt de koppeling met de oude master opgeruimd;
3. keert het toestel terug uit die sessieflow.

Die client neemt dan dus **niet** de rol van nieuwe master op.

### 12.5 Belangrijk onderscheid tussen handover en sessie-einde

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

## 13. Offline fallback: export/import

Niet elke situatie laat stabiele live samenwerking toe. Daarom bestaat er ook een fallback.

### 13.1 Wanneer gebruikt u dit?

Gebruik offline export/import wanneer:

- de netwerkverbinding te onstabiel is;
- een client niet live kon koppelen;
- of u observaties achteraf toch nog naar de master wilt overbrengen.

### 13.2 Flow op de client

1. Kies **Offline export**.
2. VT5 maakt een exportbestand van de lokale observaties.
3. Deel dat bestand via een geschikte methode.

### 13.3 Flow op de master

1. Kies **Offline import**.
2. Selecteer het ontvangen bestand.
3. Laat VT5 de records inlezen.

Dit is geen vervanging van de live master/client-flow, maar wel een nuttige noodoplossing.

---

## 14. Praktische aandachtspunten en veelvoorkomende fouten

### 14.1 Een client scant alleen de pairing-QR

Gevolg:

- de client mist mogelijk nog de juiste netwerkverbinding.

Oplossing:

- laat de client eerst de **Wi‑Fi QR** scannen;
- en daarna pas de **pairing-QR**.

### 14.2 Een client wil vertrekken terwijl er nog waarnemingen wachten

Gevolg:

- VT5 laat het verlaten niet toe.

Oplossing:

- wacht op bevestiging door de master;
- of herstel eerst de verbinding.

### 14.3 De master sluit te vroeg het koppelvenster

Gevolg:

- late clients missen mogelijk de actuele PIN/QR-flow.

Oplossing:

- houd het mastervenster open totdat alle noodzakelijke clients gekoppeld zijn;
- of open het opnieuw en genereer zo nodig een nieuwe PIN.

### 14.4 Gebruikers denken dat annotaties alleen lokaal zijn

Dat is onjuist.

Annotaties van een client:

- horen bij de waarneming;
- worden meegestuurd;
- en worden ook aan de master gemeld.

### 14.5 Gebruikers verwarren “sessie beëindigen” met “handover”

Onthoud:

- **sessie beëindigen** = samenwerking stopzetten;
- **handover** = een client kan de masterrol overnemen voor een vervolgtelling.

---

## 15. Korte checklist in het veld

### Voor de master

- [ ] Zet VT5 in **Master-modus**
- [ ] Start de telling
- [ ] Open **Clients**
- [ ] Controleer PIN, Wi‑Fi QR en pairing-QR
- [ ] Laat nieuwe clientverzoeken toe

### Voor elke client

- [ ] Zet VT5 in **Client-modus**
- [ ] Open het telscherm
- [ ] Tik op **Koppelen met master**
- [ ] **Scan eerst de Wi‑Fi QR**
- [ ] **Scan daarna de pairing-QR**
- [ ] Controleer of de status **verbonden met master** toont

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

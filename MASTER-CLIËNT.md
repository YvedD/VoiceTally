# VT5 Master/Client-handleiding

Deze handleiding beschrijft de **actuele** VT5-workflow voor samenwerken met meerdere toestellen aan **éénzelfde telling**. In deze handleiding worden de woorden **telling** en **sessie** als hetzelfde gebruikt.

De belangrijkste wijziging is deze:

- een teller kan **eerst alleen in solo-modus starten**;
- en **pas later, tijdens de lopende telling**, het toestel live omschakelen naar **master-modus**;
- **zonder `TellingScherm` te verlaten**.

Dat is vooral handig wanneer er onverwacht een tweede teller toekomt op de telpost.

Voor een korte veldversie:

- zie [`SPIEKBRIEFJE-TELLERS.md`](SPIEKBRIEFJE-TELLERS.md)

---

## Inhoud

1. [Wat is een master/client-sessie?](#1-wat-is-een-masterclient-sessie)
2. [Belangrijkste principes vooraf](#2-belangrijkste-principes-vooraf)
3. [Wat u vooraf in orde brengt](#3-wat-u-vooraf-in-orde-brengt)
4. [Voorkeursflow: een solo-telling live omzetten naar master](#4-voorkeursflow-een-solo-telling-live-omzetten-naar-master)
5. [Wat de twee headericonen in `TellingScherm` doen](#5-wat-de-twee-headericonen-in-tellingscherm-doen)
6. [Wat elke client precies moet doen](#6-wat-elke-client-precies-moet-doen)
7. [Waarom elke client twee verschillende QR-codes moet scannen](#7-waarom-elke-client-twee-verschillende-qr-codes-moet-scannen)
8. [Wat er gebeurt zodra de client verbonden is](#8-wat-er-gebeurt-zodra-de-client-verbonden-is)
9. [Hoe een client een waarneming annoteert](#9-hoe-een-client-een-waarneming-annoteert)
10. [Wat het effect van annotaties is op de telling](#10-wat-het-effect-van-annotaties-is-op-de-telling)
11. [Exit-flows: een sessie verlaten of beëindigen](#11-exit-flows-een-sessie-verlaten-of-beëindigen)
12. [Handover-flows: wanneer een client de masterrol overneemt](#12-handover-flows-wanneer-een-client-de-masterrol-overneemt)
13. [Offline fallback: export/import](#13-offline-fallback-exportimport)
14. [Praktische aandachtspunten en veelvoorkomende fouten](#14-praktische-aandachtspunten-en-veelvoorkomende-fouten)
15. [Korte checklist in het veld](#15-korte-checklist-in-het-veld)
16. [Samenvatting in één alinea](#16-samenvatting-in-één-alinea)

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
   - Per gezamenlijke telling is er altijd exact één master-toestel.

2. **Clients tellen mee, maar de master blijft centraal**
   - Clients voeren observaties in.
   - De master ontvangt en verwerkt die observaties.

3. **Een solo-telling kan tijdens het tellen live worden omgezet naar een master-telling**
   - U hoeft daarvoor `TellingScherm` niet te verlaten.
   - Dat gebeurt via het **wifi-icoon** in de header van `TellingScherm`.

4. **De client gebruikt nog steeds dezelfde normale instapflow**
   - Een client start via het hoofdscherm met **Invoegen in lopende telling**.

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
- de teller weet welk toestel de master zal zijn als er samengewerkt wordt.

### 3.2 Kies vooraf het meest geschikte master-toestel

Kies bij voorkeur een toestel:

- met een stabiele batterij;
- dat centraal bij de telling blijft;
- en dat tijdens de sessie niet voortdurend van gebruiker wisselt.

### 3.3 Houd rekening met permissies

Wanneer u op het master-toestel voor het eerst live samenwerking activeert, kan Android toestemming vragen voor:

- **Nabije wifi-apparaten**

Die toestemming is nodig om het lokale netwerk voor de samenwerking te starten.

### 3.4 Begrijp het netwerkmodel

In de actuele workflow probeert VT5 op het master-toestel altijd eerst de **meest veilige en minst omslachtige netwerkroute** te gebruiken.

Dat betekent in de praktijk:

- is het master-toestel al verbonden met een **extern Wi‑Fi-netwerk of hotspot**, dan hergebruikt VT5 dat netwerk;
- is er nog geen bruikbaar Wi‑Fi-netwerk, dan start VT5 zelf een **lokaal netwerk** via `LocalOnlyHotspot`;
- in **beide** gevallen verbinden clients daarna nog steeds via exact dezelfde vaste volgorde:
  1. eerst de **Wi‑Fi QR**;
  2. daarna de **pairing-QR**.

Zo blijft de clientflow overal gelijk, ook als het netwerk per telpost verschilt.

---

## 4. Voorkeursflow: een solo-telling live omzetten naar master

Dit is de **nieuwe voorkeursflow** in het veld.

Gebruik deze flow wanneer:

- één teller al bezig is in solo-modus;
- en een collega later onverwacht wil aansluiten op diezelfde lopende telling.

### 4.1 Stap voor stap op het master-toestel

1. Open VT5.
2. Ga naar **Invullen telpostgegevens**.
3. Vul de telling normaal in.
4. Kies **Verder**.
5. Kies de soorten zoals u dat normaal zou doen.
6. Start de telling zodat u in `TellingScherm` terechtkomt.
7. Telt u voorlopig alleen, dan blijft alles gewoon werken zoals in solo-modus.
8. Zodra een tweede teller wil aansluiten, tikt u in de header van `TellingScherm` op het **wifi-icoon**.
9. Dat wifi-icoon staat **links naast het QR-icoon**.
10. Als Android een permissie vraagt voor **Nabije wifi-apparaten**, staat u die toe.
11. VT5 kiest nu automatisch de beste netwerkroute:
    - ofwel hergebruikt de app het reeds actieve externe Wi‑Fi-netwerk van de master;
    - ofwel start de app zelf een lokaal hotspot op het master-toestel.
12. Bij succes krijgt u de melding **Lokaal netwerk actief**.
13. Staat het master-toestel al op extern Wi‑Fi, dan kan VT5 éénmalig het wachtwoord van dat netwerk vragen om de Wi‑Fi QR correct te kunnen tonen.
14. Vanaf dat moment is de lopende telling omgeschakeld van solo naar master, zonder dat u het scherm moest verlaten.
15. Toon daarna de twee QR-codes voor clients.
16. Laat de collega-teller daarna via zijn eigen toestel aansluiten.

### 4.2 Wat dit precies betekent

Door op het wifi-icoon te tikken vertelt u aan VT5 dat dit toestel voortaan:

- clients mag ontvangen;
- de sessie mag beheren;
- en als centraal punt voor de lopende telling zal werken.

### 4.3 Wat er daarna gebeurt met latere tellingen

Als dit toestel in master-modus blijft en u van daaruit een **vervolgtelling** start, blijft die master-intentie behouden.

Praktisch betekent dit:

- dezelfde gebruiker kan verderwerken als master;
- nieuwe clients kunnen later opnieuw aansluiten;
- en ook volgende tellingen op dezelfde telpost kunnen in dezelfde samenwerkingslogica verdergaan.

### 4.4 Alternatieve route

VT5 kan ook rechtstreeks als master gestart worden vanaf het hoofdscherm via **Telling starten als master**.

Toch is in deze handleiding de **voorkeursroute** de live omschakeling vanuit `TellingScherm`, omdat die precies de praktijksituatie oplost waarbij een tweede teller pas later toekomt.

---

## 5. Wat de twee headericonen in `TellingScherm` doen

Bovenaan in `TellingScherm` staan in deze workflow twee belangrijke iconen.

### 5.1 Het wifi-icoon

Het **wifi-icoon** dient om een lopende solo-telling om te schakelen naar **master-modus**.

Bij tikken gebeurt dit:

- VT5 controleert de nodige permissie;
- VT5 start een lokaal netwerk op het toestel;
- en bij succes wordt de lopende telling master-ready.

Zodra master actief is, kan het wifi-icoon visueel aangeven dat samenwerken actief is.

### 5.2 Het QR-icoon

Het **QR-icoon** dient om de twee QR-codes voor clients te tonen:

1. de **Wi‑Fi QR**;
2. de **pairing-QR**.

Belangrijk:

- eerst activeert u de master via het **wifi-icoon**;
- pas daarna gebruikt u het **QR-icoon** om clients te laten aansluiten.

### 5.3 Wat de master verder nog moet doen

De master moet tijdens het koppelen:

- het QR-venster beschikbaar houden zolang clients nog moeten aansluiten;
- de twee QR-codes laten scannen in de juiste volgorde;
- en nieuwe clientverzoeken toestaan wanneer VT5 daarom vraagt.

Pas daarna loopt de gezamenlijke telling volledig live.

---

## 6. Wat elke client precies moet doen

Elke client moet zijn toestel apart voorbereiden en koppelen.

Doe deze stappen **op elk client-toestel afzonderlijk**.

1. Open VT5.
2. Kies op het hoofdscherm **Invoegen in lopende telling**.
3. Volg de clientflow om te scannen.
4. Scan eerst de **Wi‑Fi QR** van het master-toestel.
5. Scan daarna de **pairing-QR** van datzelfde master-toestel.
6. Na die twee scans wordt het client-toestel gekoppeld aan de lopende telling.
7. Het client-toestel opent daarna het telscherm in client-modus.

Daarmee staat dit toestel klaar om:

- observaties door te sturen;
- mee te tellen in dezelfde sessie;
- en niet zelf de centrale sessie te beheren.

### 6.1 Wat belangrijk is voor extra clients

Extra clients gebruiken **exact dezelfde flow**:

- open **Invoegen in lopende telling**;
- scan eerst de Wi‑Fi QR;
- scan daarna de pairing-QR.

Dat geldt:

- voor de eerste extra client;
- voor alle latere extra clients;
- en ook voor volgende tellingen zolang hetzelfde toestel de master blijft.

---

## 7. Waarom elke client twee verschillende QR-codes moet scannen

De master toont twee verschillende QR-codes omdat ze twee verschillende taken hebben.

1. **Wi‑Fi QR**
   - helpt de client om eerst op het juiste lokale netwerk van de master uit te komen.

2. **Pairing-QR**
   - bevat de gegevens om daarna de client met de lopende mastersessie te koppelen.

Dat onderscheid is belangrijk:

- netwerktoegang alleen is **niet genoeg**;
- pairing alleen is **ook niet genoeg** als de client nog niet op het juiste netwerk zit.

De standaardvolgorde blijft dus altijd:

1. **eerst de Wi‑Fi QR**;
2. **daarna de pairing-QR**.

---

## 8. Wat er gebeurt zodra de client verbonden is

De client gaat vervolgens automatisch verder naar het telscherm.

In het telscherm van de client zijn ook de relevante soorten zichtbaar die op de mastertelling actief zijn.

Praktisch betekent dit:

1. alle waarnemingen van een client worden doorgestuurd en verwerkt door de master;
2. nieuwe soorten die via een client in de gedeelde telling terechtkomen, worden ook in de mastercontext verwerkt;
3. annotaties bij waarnemingen van een client worden eveneens aan de master doorgegeven.

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
2. open daarna het annotatiescherm voor die waarneming door in het groene kader op de gewenste waarneming te tikken;
3. kies de gewenste annotaties;
4. vul eventueel opmerkingen of aanvullende gegevens in;
5. bevestig de annotatie.

### 9.3 Welke soorten annotaties er beschikbaar zijn

De annotatie-opties komen uit de annotatieconfiguratie van VT5 en kunnen onder meer velden bevatten zoals:

- leeftijd;
- geslacht;
- kleed;
- opmerkingen;
- andere voorgedefinieerde keuzes;
- en waar van toepassing ook extra richtingsinformatie.

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

1. verstuurt VT5 een nette leave-melding naar de master;
2. stopt de clientverbinding;
3. krijgt de gebruiker daarna nog een vervolgvraag.

### 11.2 Wat een client na het verlaten van de sessie kan doen

Na een geslaagde client-exit biedt VT5 twee vervolgstappen:

1. **App sluiten**
2. **App herstarten**

### 11.3 Exit-flow B — de master beëindigt de samenwerking handmatig

Dit is de flow wanneer de **master zelf beslist om de samenwerking stop te zetten**.

#### Verloop

1. De master beëindigt de samenwerking vanuit de actieve mastersessie.
2. VT5 vraagt bevestiging.
3. Bij bevestiging verstuurt de master een sessie-einde naar alle verbonden clients.

#### Gevolg voor de clients

Alle clients:

- krijgen het signaal dat de samenwerking beëindigd is;
- stoppen als client van die sessie;
- en worden losgekoppeld van de master.

### 11.4 Exit-flow C — de master rondt de telling af

Wanneer de master de telling normaal afrondt, zijn er twee mogelijke vervolgen:

1. de master start zelf een vervolgtelling;
2. of de master verlaat de telpost zonder zelf verder te gaan.

Dat tweede geval leidt tot een **handover-flow** voor de clients.

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

### De master vergeet eerst het wifi-icoon te gebruiken

Gevolg:

- het toestel is nog geen actieve master;
- en het QR-icoon levert dan niet de bedoelde clientkoppeling voor een lopende solo-telling.

Oplossing:

- activeer eerst de samenwerking via het **wifi-icoon** in `TellingScherm`;
- wacht op de melding **Lokaal netwerk actief**;
- open pas daarna het **QR-icoon**.

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

### De master sluit het QR-venster te snel

Gevolg:

- late clients kunnen de actuele QR-codes niet meer scannen.

Oplossing:

- open het QR-venster opnieuw via het **QR-icoon**;
- laat clients opnieuw beide QR-codes scannen in de juiste volgorde.

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

## 15. Korte checklist in het veld

### Voor de master

- [ ] Start de telling gerust eerst in solo-modus
- [ ] Tik in `TellingScherm` op het **wifi-icoon** zodra een extra teller wil aansluiten
- [ ] Sta zo nodig **Nabije wifi-apparaten** toe
- [ ] Wacht op de melding **Lokaal netwerk actief**
- [ ] Open daarna het **QR-icoon**
- [ ] Laat elke client eerst de **Wi‑Fi QR** en daarna de **pairing-QR** scannen
- [ ] Laat nieuwe clientverzoeken toe

### Voor elke client

- [ ] Kies op het hoofdscherm **Invoegen in lopende telling**
- [ ] **Scan eerst de Wi‑Fi QR**
- [ ] **Scan daarna de pairing-QR**
- [ ] Controleer of de status **verbonden met master** toont
- [ ] Controleer of het tellingscherm opent in client-modus
- [ ] Voer waarnemingen in via spraak of via de soorten tegels

### Tijdens de telling

- [ ] Gebruik VT5 normaal voor waarnemingen
- [ ] Gebruik annotaties waar nodig
- [ ] Onthoud dat annotaties ook naar de master gaan
- [ ] Verlaat de sessie pas wanneer alles bevestigd is
- [ ] Laat latere extra clients opnieuw via **Invoegen in lopende telling** aansluiten

### Bij einde of wissel

- [ ] Laat clients netjes vertrekken via **Verlaat telling**
- [ ] Gebruik **Beëindig samenwerking** als de master alles centraal stopt
- [ ] Gebruik de **handover-flow** als een client de masterrol moet overnemen
- [ ] Weet dat een vervolgtelling op hetzelfde master-toestel opnieuw samenwerking kan blijven ondersteunen

---

## 16. Samenvatting in één alinea

In de actuele VT5-workflow kan een teller perfect **alleen in solo-modus beginnen** en pas later, tijdens de lopende telling, via het **wifi-icoon in `TellingScherm`** live omschakelen naar **master-modus**, zonder het scherm te verlaten. Na de melding **Lokaal netwerk actief** opent de master via het **QR-icoon** de twee QR-codes waarmee clients kunnen aansluiten. Elke client gebruikt daarvoor nog steeds de gewone hoofdschermflow **Invoegen in lopende telling** en scant **eerst de Wi‑Fi QR en daarna de pairing-QR**. Daarna worden clientwaarnemingen, inclusief eventuele annotaties, live doorgestuurd naar de master en geïntegreerd in dezelfde gedeelde telling. Ook latere extra clients en vervolgtellingen kunnen op dezelfde samenwerkingslogica blijven voortbouwen.

# VT5 — Master / Client(s) handleiding (pré-draft)

> Dit is een **voorlopige handleiding** voor de samenwerkingmodus van VT5.
> De bestaande `README.md` blijft de algemene gebruikershandleiding voor solo-gebruik en installatie.
> Dit document focust alleen op **solo / master / client(s)**, zodat de flow duidelijk en praktisch blijft.

> **Icoonlegende:** `✅` = OK, `⏳` = pending, `❌` = rejected, <img src="app/src/main/images/wifi.png" width="15" alt="WIFI" style="vertical-align:middle;"> Wi-Fi = Wi‑Fi, <img src="app/src/main/images/QR.png" width="15" alt="WIFI" style="vertical-align:middle;"> = QR-code

---

## 1. Wat is het verschil tussen solo, master en client?

VT5 kan op drie manieren gebruikt worden:

### Solo
- Eén toestel doet alles zelf.
- Dit is de standaardmodus.
- Alle waarnemingen, annotaties en afrondingen gebeuren lokaal op dat toestel.

### Master
- Eén toestel is de **hoofdteller**.
- De master verwerkt waarnemingen van zichzelf én van verbonden clients.
- De master is het toestel dat uiteindelijk afrondt en uploadt naar de server.

### Client
- Eén of meerdere andere toestellen sluiten aan op de master.
- Een client voert waarnemingen in, maar stuurt die door naar de master.
- Een client kan de telling niet zelfstandig afronden.

Kort gezegd:
- **solo** = volledig alleen
- **master** = hoofdtoestel van een gezamenlijke telling
- **clients** = mee-tellende toestellen die naar de master synchroniseren

---

## 2. Belangrijk uitgangspunt

VT5 probeert **geen Wi‑Fi-netwerken automatisch te wijzigen** <span style="font-size: 0.85em; white-space: nowrap;">📶</span>

Dat betekent:
- de toestellen moeten al handmatig verbonden zijn met hetzelfde bestaande Wi‑Fi-netwerk <span style="font-size: 0.85em; white-space: nowrap;">📶</span>
- VT5 gebruikt dat netwerk enkel voor communicatie
- als Wi‑Fi of permissies niet in orde zijn, zal de app je daarop wijzen

Dit is bewust zo ontworpen om Android-beperkingen te respecteren en fouten te vermijden.

---

## 3. Algemene flow in de app

### Solo-flow
1. Start de app
2. Kies `Invoeren telpostgegevens`
3. Vul metadata in
4. Kies soorten
5. Open `TellingScherm`
6. Voeg waarnemingen toe
7. Rond af en upload

### Master-flow
1. Start de app
2. Kies `Invoeren telpostgegevens`
3. Vul metadata in
4. Kies soorten
5. Open `TellingScherm`
6. Start daar de master-sessie via het master/client-icoon bovenaan
7. Toon de QR-code aan de clients
8. Bevestig clientaanvragen
9. Verwerk eigen en clientwaarnemingen
10. Rond af en upload

### Client-flow
1. Start de app
2. Kies `Invoegen als cliënt`
3. Scan de QR-code van de master
4. Bevestig eventueel een alias of clientnaam
5. De client gaat naar het `TellingScherm`
6. De client ontvangt tegels van de master en telt mee
7. De client blijft verbonden tot de master de sessie beëindigt of de gebruiker de telling verlaat

---

## 4. Wat moet al in orde zijn vóór je begint?

### Op beide toestellen
- VT5 moet geïnstalleerd zijn
- De nodige permissies moeten toegestaan zijn
- Beide toestellen moeten op hetzelfde Wi‑Fi-netwerk zitten
- De master moet de telling eerst normaal gestart hebben

### Minimale permissies
- Microfoon voor spraakinvoer
- Camera voor het scannen van de QR-code
- Netwerktoegang voor de communicatie tussen master en clients

### Opmerking
VT5 vraagt permissies op het moment dat ze nodig zijn. Toch is het beter om bij het opstarten van de app al te controleren of alles in orde is.

---

## 5. Master gebruiken

### 5.1 Normaal starten
De master start **niet** vanuit een aparte master-app of losse serverknop.
De master begint met de **gewone tellingflow**:

1. `Invoeren telpostgegevens`
2. Metadata invullen
3. Soorten kiezen
4. `TellingScherm` openen
5. Daar de master-clientknop gebruiken om de sessie te starten

### 5.2 Master-sessie starten
Op het `TellingScherm` staat bovenaan een master/client-icoon.
Als je daarop tikt als master:
- VT5 start de lokale master-sessie <span style="font-size: 0.85em; white-space: nowrap;">📶</span>
- de app verzamelt de nodige verbindingsgegevens
- er wordt een QR-code getoond voor clients <span style="font-size: 0.85em; white-space: nowrap;">▣</span>
- clients kunnen zich aansluiten

### 5.3 Wat toont de master?
De master ziet:
- een status dat de master-sessie actief is
- verbonden clients
- clientwaarnemingen in de finals-log
- sync van soorten/tegels naar clients

### 5.4 Master blijft eindverantwoordelijk
De master:
- verwerkt alle waarnemingen
- houdt de final log bij
- beheert de actieve soorten/t tegels
- rondt de telling af
- uploadt naar de server

De clients zijn dus enkel meewerkende toestellen.

---

## 6. Client gebruiken

### 6.1 Client starten
Een client start via **`Invoegen als cliënt`** in het hoofdscherm.

Daarna gebeurt dit:
1. De systeemcamera opent
2. De client scant de QR-code van de master <span style="font-size: 0.85em; white-space: nowrap;">▣</span>
3. De master bevestigt de client
4. De client gaat direct naar het `TellingScherm`

### 6.2 Client-identiteit
De client kan een alias invullen, bijvoorbeeld:
- Brecht
- Luc
- Team 1

Als er geen alias is, krijgt de client automatisch een volgnummer van de master, zoals:
- `Cl001`
- `Cl002`
- `Cl003`

Die naam zie je terug in meldingen en in de final log op de master.

### 6.3 Wat mag een client wel en niet?
Een client mag:
- waarnemingen invoeren
- annotaties toevoegen
- tegels krijgen via sync van de master
- meetellen in de live telling

Een client mag niet:
- zelfstandig afronden
- uploaden naar de server als eindverantwoordelijke
- de sessie van de master overschrijven

---

## 7. QR-code en koppeling

### Wat staat er in de QR-code?
De QR-code bevat verbindingsinformatie voor de master-sessie.
Die code is bedoeld om een client naar de juiste master te brengen.

### Wat doet de client met die QR-code?
- de QR wordt gescand <span style="font-size: 0.85em; white-space: nowrap;">▣</span>
- VT5 haalt daar de verbindingsgegevens uit
- de client maakt verbinding met de master <span style="font-size: 0.85em; white-space: nowrap;">📶</span>
- de master bevestigt of weigert de aanvraag <span style="font-size: 0.85em; white-space: nowrap;">✅ / ❌</span>

### Wat ziet de gebruiker?
Bij een succesvolle koppeling:
- krijgt de client een bevestiging <span style="font-size: 0.85em; white-space: nowrap;">✅</span>
- krijgt de master een bevestiging <span style="font-size: 0.85em; white-space: nowrap;">✅</span>
- beide toestellen tonen dat de sessie actief is

---

## 8. Tegels en synchronisatie

VT5 synchroniseert de actieve soorten tussen master en clients.

### Belangrijk gedrag
- Als de **master** een nieuwe soorttegel toevoegt, verschijnt die ook op de clients.
- Als een **client** een nieuwe soorttegel toevoegt, verschijnt die ook op de master.
- Tegels worden niet alleen gesynchroniseerd wanneer er al een waarneming bij zit.
- Ook tegels met `aantal = 0` of `aantal_terug = 0` zijn belangrijk.

Dat is nodig omdat de tegels ook gebruikt worden voor spraakinvoer en snelle herkenning.

---

## 9. Waarnemingen en final log

### Master verwerkt eigen waarnemingen
Als de master zelf een waarneming invoert voor een reeds actieve soort:
- dan verloopt dat zoals in solo-modus
- de final log wordt bijgewerkt
- de record komt in de enveloppe terecht

### Client verwerkt waarnemingen
Als een client een waarneming invoert:
- die wordt doorgestuurd naar de master
- de master meldt ontvangst
- de master toont de waarneming in het final logvenster
- de master bewaart die record in de server-enveloppe

### Status bij client-waarnemingen
Client-waarnemingen kunnen een korte status krijgen, zoals:
- pending <span style="font-size: 0.85em; white-space: nowrap;">⏳</span>
- ontvangen/verwerkt <span style="font-size: 0.85em; white-space: nowrap;">✅</span>
- geweigerd <span style="font-size: 0.85em; white-space: nowrap;">❌</span>

Dit helpt om visueel te zien wat er met een waarneming gebeurd is.

---

## 10. Annotaties

Annotaties zijn extra gegevens bij een waarneming, zoals:
- geslacht
- leeftijd
- kleed
- locatie
- hoogte
- opmerkingen

### Belangrijk in master/client-modus
- Als een client een waarneming annoteert, moet die annotatie ook op de master bij het juiste record terechtkomen.
- De annotatie moet daarna ook in de uiteindelijke server-enveloppe zitten.
- Annotaties mogen dus niet aan de verkeerde regel of aan een andere telling gekoppeld worden.

---

## 11. Afronden

### Solo
Solo-toestellen kunnen zelf afronden en uploaden.

### Master
De master kan afronden en uploaden.

### Client
Een client kan **niet** afronden.

Dat is bewust zo:
- de master is de enige die de telling naar de server afsluit
- clients blijven aangesloten tot de master klaar is of de sessie eindigt

---

## 12. Permissies en meldingen

VT5 moet bij het opstarten of bij de relevante acties controleren of de permissies in orde zijn.

Typische aandachtspunten:
- microfoon voor spraak
- camera voor QR-scan
- netwerkstatus voor master/client-communicatie

Als permissies ontbreken:
- toon een duidelijke melding
- forceer geen Android-instellingen voor netwerkselectie
- laat de gebruiker zelf verder handelen

---

## 13. Praktische gebruikersregels

1. Begin eerst met een normale telling.
2. Start daarna pas de master-sessie vanuit het `TellingScherm`.
3. Laat clients pas aansluiten nadat de master actief is.
4. Zorg dat alle toestellen al op hetzelfde Wi‑Fi-netwerk zitten.
5. Gebruik de QR-code van de master, niet handmatige netwerktrucs.
6. Laat clients alleen tellen; de master houdt de eindcontrole.
7. Gebruik de master voor afronding en upload.

---

## 14. Wat je als gebruiker mag verwachten

### Op de master
- actieve tegels
- live final log
- bevestiging van clients
- geïntegreerde clientwaarnemingen
- upload naar server aan het einde

### Op de client
- directe doorgang naar het telscherm
- eigen invoer die via de master loopt
- duidelijke meldingen over verbinding en verwerking
- geen eigen serverupload

---

## 15. Opmerking over deze pré-draft

Dit document is bedoeld als **heldere gebruikershandleiding** voor de master/client-flow.
Het kan later nog opgeschoond worden naar de uiteindelijke `README.md` of opgesplitst worden in:
- een algemene README
- een aparte master/client-handleiding
- een korte “snelle start”

---

## 16. Snelle samenvatting

- **Solo** = één toestel, alles lokaal
- **Master** = hoofdtoestel, verwerkt en uploadt
- **Clients** = tellende extra toestellen, synchroniseren met de master
- Alle toestellen moeten al op hetzelfde Wi‑Fi-netwerk zitten
- De master toont een QR-code
- Clients scannen die QR-code en voegen in
- De master keurt clientaanvragen goed of af
- De master is de enige die afrondt en uploadt

---

Einde van deze pré-draft.


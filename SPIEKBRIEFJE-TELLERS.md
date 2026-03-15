# VT5 Spiekbriefje voor tellers

Korte veldversie voor een gedeelde telling met **master** en **client(s)**.

Uitgangspunt:

- één toestel is de **master**;
- alle andere toestellen zijn **clients**;
- een **telling** en een **sessie** zijn hier hetzelfde.

---

## 1. Snelste praktijksituatie

Gebruik dit wanneer:

- teller 1 al bezig is in **solo-modus**;
- teller 2 of later extra tellers willen aansluiten.

### Op het master-toestel

1. Start de telling gewoon in solo-modus.
2. Zodra een extra teller wil aansluiten: tik in `TellingScherm` op het **wifi-icoon**.
3. Dat icoon staat **links naast het QR-icoon**.
4. Sta zo nodig **Nabije wifi-apparaten** toe.
5. VT5 hergebruikt nu ofwel het bestaande Wi‑Fi-netwerk van de master, of start zelf een lokaal hotspot.
6. Wacht op de melding **Lokaal netwerk actief**.
7. Toon daarna de twee QR-codes.
8. Laat de client **eerst de Wi‑Fi QR** en **daarna de pairing-QR** scannen.
9. Bevestig nieuwe clientverzoeken wanneer VT5 daarom vraagt.

### Op elk client-toestel

1. Open VT5.
2. Kies **Invoegen in lopende telling**.
3. Scan eerst de **Wi‑Fi QR**.
4. Scan daarna de **pairing-QR**.
5. Controleer of de status **verbonden met master** toont.
6. Tellen kan nu gewoon starten of doorgaan.

---

## 2. Wat betekenen de twee iconen bovenaan?

### Wifi-icoon

Gebruik dit om een lopende solo-telling live om te zetten naar **master-modus**.

### QR-icoon

Gebruik dit pas **nadat** master actief is, om de twee QR-codes voor clients te tonen.

---

## 3. Volgorde van de twee QR-codes

Altijd in deze volgorde:

1. **Wi‑Fi QR**
2. **pairing-QR**

Niet omgekeerd.

---

## 4. Extra clients later laten aansluiten

Elke latere extra client gebruikt opnieuw exact dezelfde flow:

1. **Invoegen in lopende telling**
2. **Wi‑Fi QR** scannen
3. **pairing-QR** scannen

---

## 5. Tijdens de telling

### Master

- ontvangt alle clientwaarnemingen;
- verwerkt die in dezelfde telling;
- ziet clientinvoer in de log met **[C]**.

### Client

- kan tellen via spraak en tegels;
- kan annotaties toevoegen;
- stuurt alles door naar de master.

### Belangrijk

- annotaties blijven **niet** alleen lokaal;
- ze worden ook naar de master doorgestuurd.

---

## 6. Als de verbinding even wegvalt

- de client bewaart nog niet bevestigde waarnemingen lokaal;
- VT5 probeert automatisch opnieuw te verbinden;
- wachtende waarnemingen worden later opnieuw doorgestuurd.

Daarom kan een client de sessie niet zomaar verlaten zolang er nog onbevestigde waarnemingen openstaan.

---

## 7. Einde van de sessie

### Client wil stoppen

- kies **Verlaat telling**;
- dit kan pas als alles bevestigd is.

### Master stopt alles

- gebruik **Beëindig samenwerking**;
- alle clients worden dan losgekoppeld.

### Master rondt af en vertrekt

- clients kunnen een **handover** krijgen;
- één client kan dan de masterrol overnemen voor een vervolgtelling.

---

## 8. Mini-checklist in het veld

### Master

- [ ] Solo-telling gestart
- [ ] Wifi-icoon aangetikt
- [ ] Melding **Lokaal netwerk actief** gezien
- [ ] QR-icoon geopend
- [ ] Client laat eerst **Wi‑Fi QR** scannen
- [ ] Client laat daarna **pairing-QR** scannen

### Client

- [ ] **Invoegen in lopende telling** gekozen
- [ ] **Wi‑Fi QR** gescand
- [ ] **pairing-QR** gescand
- [ ] Status **verbonden met master** gecontroleerd

---

## 9. Meer uitleg

Voor de volledige handleiding met detailflows:

- zie [`MASTER-CLIËNT.md`](MASTER-CLIËNT.md)


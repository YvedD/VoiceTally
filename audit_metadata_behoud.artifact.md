# Audit: Behoud van Metadata en Server Sync (Room vs JSON)

**Status:** Analyse voltooid
**Datum:** 22 mei 2024

## 1. Conclusie
De analyse bevestigt dat er momenteel een aanzienlijk risico is op het verlies van metadata bij het bewerken via de 'Database Beheer' module. Daarnaast is de synchronisatie-logica via de Room-route weliswaar aanwezig, maar technisch incompleet vergeleken met de JSON-route.

## 2. Bevindingen: Metadata Verlies

In het bestand `DatabaseTellingDetailActiviteit.kt` heb ik een kritieke tekortkoming gevonden in de `saveChanges()` methode. Hoewel de UI alle relevante velden (zoals wind, weer, bewolking, zicht, etc.) toont en laat bewerken, worden deze **niet** allemaal opgeslagen in de database.

**Lijst van velden die momenteel VERLOREN gaan bij opslaan:**
*   `windrichting`, `windkracht`
*   `temperatuur`, `bewolking`, `bewolkinghoogte`
*   `zicht`, `neerslag`, `duurneerslag`
*   `typetelling`, `metersnet`, `geluid`
*   `hydro`, `hpa`, `equipment`
*   `tellersactief`, `tellersaanwezig`

Enkel de 'basis' metadata velden worden momenteel meegenomen in de `header.copy()` actie. Dit verklaart waarom een bewerkte telling op de server mogelijk niet de volledige informatie bevat.

## 3. Bevindingen: Server Sync (Upload)

De gebruiker merkte op dat de JSON-route de servergegevens succesvol overschrijft, terwijl de Room-route dit niet lijkt te doen.

### API Aanroepen
*   **Beide routes** gebruiken uiteindelijk dezelfde API: `TrektellenApi.postCountsSave`. Er is geen alternatieve "update-only" API.
*   De server van Trektellen herkent een update aan de hand van de `onlineid` of de `uuid`.

### Mogelijke oorzaak falen Room-route:
1.  **Bron Identificatie (`bron`):** In de JSON-route wordt hardcoded `bron = "4"` meegestuurd. In de Room-entiteit staat de default op `"VT5"`. Als een header niet correct vanuit een live-telling is aangemaakt maar vanuit een andere bron, kan de server de update weigeren omdat de bron niet overeenkomt.
2.  **Volledigheid van de Enveloppe:** Omdat de `saveChanges` methode metadata verliest (zie punt 2), wordt er bij een "Sync naar server" een incomplete enveloppe gestuurd. Het is mogelijk dat de server bepaalde validaties uitvoert waardoor de records niet worden verwerkt als essentiële metadata ontbreekt of corrupt is.
3.  **OnlineId Mapping:** In de `EDITOR_UPLOAD` modus (gebruikt bij de database-sync) moet de `onlineid` strikt overeenkomen met wat de server verwacht. Als dit veld per ongeluk gewist is of niet correct uit de UI wordt opgehaald, wordt de telling als "nieuw" gezien of geweigerd.

## 4. Aanbevelingen

1.  **Reparatie `saveChanges()`:** De methode moet worden uitgebreid zodat ELK veld uit de `TellingHeader` dat in de UI bewerkbaar is, ook daadwerkelijk wordt opgeslagen.
2.  **Synchronisatie van `bron`:** Zorg dat de `bron` waarde consistent op `"4"` staat voor alle Room-headers die naar de server gaan, net als bij de JSON-route.
3.  **Validatie voor Sync:** Voeg een log-stap toe die de volledige JSON-enveloppe die naar de server gaat via de Room-route print naar de `fileLogger`. Hiermee kunnen we exact zien wat er verstuurd wordt en dit vergelijken met een succesvolle JSON-upload.

**Eindoordeel:** De architectuur voor de Room-route is correct (gebruikt dezelfde API), maar de implementatie van het opslaan van wijzigingen is op dit moment incompleet, wat leidt tot dataverlies en mislukte server-updates.
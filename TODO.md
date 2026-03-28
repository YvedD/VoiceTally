# TODO

## Idee: update van getrainde aliassen detecteren bij installatie

- Ga ervan uit dat de gebruiker zelf een nieuwere versie van het JSON-bestand met getrainde aliassen plaatst in `Documents/VT5/assets`.
- Wanneer de gebruiker vanuit het `HoofdScherm` naar het `InstallatieScherm` gaat, controleert de app of er een nieuwere versie van de getrainde aliassen beschikbaar is.
- Als dat zo is, toon een popup:
  - **"Nieuwe getrainde aliassen beschikbaar! Binair bestand aanmaken"**
  - knoppen: **[OK]** en **[Annuleren]**
- Bij **OK** moet het toestel zelf het binaire aliasbestand opnieuw opbouwen op basis van dat JSON-bestand.
- Laat de bestaande, werkende aliasflow ongemoeid; voeg dit als een veilige extra controle/actiestap toe.

## Korte implementatieprompt voor later

"Controleer in `InstallatieScherm` of er in `Documents/VT5/assets` een recentere alias-JSON-versie aanwezig is dan de lokaal geïnstalleerde versie. Vergelijk bij voorkeur op manifest/version + bestandsgrootte + checksum. Toon alleen bij een echte upgrade de popup 'Nieuwe getrainde aliassen beschikbaar! Binair bestand aanmaken'. Laat het doeltoestel daarna zelf de JSON inlezen en de binaire aliascache opnieuw genereren, zonder de huidige werkende aliasmatching te breken."

## Idee: spiekbriefje per soort met getrainde aliassen

- Voorzie een read-only overzicht per soort waarin de reeds getrainde aliassen snel geraadpleegd kunnen worden.
- Toon standaard enkel de user-getrainde aliassen, met eventueel een toggle om ook seed-aliassen te tonen.
- Plaats dit bij voorkeur in `InstellingenScherm` of in een apart alias-overzichtsscherm dat vanuit instellingen bereikbaar is.
- Gebruik de bestaande aliasbron als read-only bron; deze lijst mag de matcher, de trainingflow of de bestaande opslag niet wijzigen.

## Korte implementatieprompt voor later

"Maak een read-only spiekbriefje voor aliassen per soort. Gebruik `AliasRepository.getAliasesForSpecies()` of een equivalente read-only bron om per speciesId de aliassen te tonen. Standaard filteren op `source == user_field_training`, met optionele toggle voor seed-aliases. Toon dit via `InstellingenScherm` of een apart overzichtsscherm. Belangrijk: niets aanpassen aan de matcher, geen bestaande aliasdata wijzigen, enkel lezen en weergeven."


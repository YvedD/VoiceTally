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


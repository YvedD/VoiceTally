# To-Do Lijst voor Room Refactoring Sessie

## 1. Verificatie & Cross-check
- [ ] **Cross-check met 'main'**: Vergelijk de nieuwe Room-logica met de oude JSON-implementatie op de `main`-branch. Let specifiek op default-waarden en berekeningen die mogelijk niet zijn meegenomen.
- [ ] **Release-build Check**: Voer een build uit om te kijken of er compilatie- of ProGuard-fouten optreden met de nieuwe entiteiten en datatypes.

## 2. Logica Verificatie
- [ ] **AfrondWorker**: Controleren of de achtergrond-upload de uitgebreide metadata correct uit Room haalt.
- [ ] **Vervolgtelling**: Verifiëren of het `MetadataScherm` gegevens correct herstelt uit de nieuwe Room-velden bij een vervolgsessie.
- [ ] **String-only Validatie**: Garanderen dat nergens per ongeluk numerieke conversies plaatsvinden voor velden die naar de server gaan.

## 3. End-to-End Test (Simulatie/Analyse)
- [ ] Volledige flow doorlopen: Metadata invullen -> Start sessie -> Waarneming -> Upload.
- [ ] Verifiëren dat de 'envelope' exact de juiste tekstuele waarden bevat.

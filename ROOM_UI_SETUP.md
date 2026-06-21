# Plan: Herbruikbaar Record Management UI (Room-gebaseerd)

Dit document beschrijft de stapsgewijze implementatie van een herbruikbare UI-component voor het beheren van waarnemingen (`ObservationEntity`). Het doel is om één centrale component te hebben die zowel vanuit het actieve tel-scherm als vanuit het beheer-scherm geopend kan worden.

## Architectuur Uitgangspunten
- **Stack**: XML Layouts + ViewBinding + Hilt ViewModel.
- **Pattern**: UI -> ViewModel -> Repository -> Room.
- **Component**: `RecordManagerFragment` (gehost als DialogFragment of in een container).
- **Context**: Dynamisch filteren op `tellingId` via argumenten.

---

## Fase 1: Data & Repository Uitbreiding
*Focus: Zorgen dat de database batch-operaties en geavanceerde queries ondersteunt.*

### Stap 1.1: DAO Uitbreiding
- **Bestanden**: `TellingDao.kt`
- **Actie**: 
    - Toevoegen van `deleteObservationsByIds(ids: List<String>)`.
    - Toevoegen van een zoek/filter query (indien nodig voor performantie).

### Stap 1.2: Repository Update
- **Bestanden**: `TellingRepository.kt`
- **Actie**: 
    - Exponeren van de nieuwe DAO functies.
    - Toevoegen van een `getFilteredObservationsFlow(tellingId, query)` functie.

---

## Fase 2: State Management (ViewModel)
*Focus: De logica loskoppelen van de UI.*

### Stap 2.1: RecordManagerViewModel
- **Bestanden**: `RecordManagerViewModel.kt` (Nieuw)
- **Actie**: 
    - Beheren van de `tellingId`.
    - Filter-logica (tijd, soort, type).
    - Bijhouden van multi-select state (geselecteerde record IDs).
    - Afhandelen van CRUD-commando's vanuit de UI.

---

## Fase 3: Gebruikersinterface (Layouts)
*Focus: Ontwerpen van een flexibele en moderne Dark UI.*

### Stap 3.1: XML Layouts
- **Bestanden**: `fragment_record_manager.xml`, `item_record_manageable.xml` (Nieuw)
- **Actie**: 
    - Hoofdlayout met SearchBar, Filter-chips en RecyclerView.
    - Itemlayout met checkbox voor batch-selectie en duidelijke visuele indicators.

---

## Fase 4: Implementatie Fragment & Adapter
*Focus: De herbruikbare component bouwen.*

### Stap 4.1: RecordAdapter
- **Bestanden**: `RecordAdapter.kt` (Nieuw)
- **Actie**: 
    - Gebruik van `DiffUtil` voor soepele updates.
    - Ondersteuning voor 'Selectie-modus'.
    - Callbacks voor edit/delete acties.

### Stap 4.2: RecordManagerFragment
- **Bestanden**: `RecordManagerFragment.kt` (Nieuw)
- **Actie**: 
    - Koppeling met `RecordManagerViewModel`.
    - Implementatie van de bevestigingsdialogen (Confirmation before delete).
    - Navigatie argumenten verwerken (`tellingId`).

---

## Fase 5: Integratie & Opkuis
*Focus: De component activeren in de rest van de app.*

### Stap 5.1: Koppeling in Bestaande Schermen
- **Bestanden**: `TellingScherm.kt`, `TellingBeheerScherm.kt`
- **Actie**: 
    - Verwijderen van lokale (gedupliceerde) record-lijst logica.
    - Toevoegen van een knop/actie om de `RecordManagerFragment` te openen.

---

## Rollback & Veiligheid
- Bij elke stap wordt gecontroleerd of de app nog compileert (Gradle Sync).
- Er worden geen wijzigingen aangebracht in de `ObservationEntity` structuur zelf om data-migratie issues te voorkomen.
- Gebruik van `TellingRepository` garandeert dat alle bestaande business rules (zoals ID generatie) gerespecteerd worden.

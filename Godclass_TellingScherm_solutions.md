# Godclass Oplossingsplan — `TellingScherm.kt`

> **Doel:** Afstappen van de Godclass in `TellingScherm.kt` (3123 lijnen, ~20 helper dependencies)
>
> **Status:** 📋 Fase 0 voltooid — Rollback-punt aangemaakt

---

## 0. Rollback-punt (milestone)

Voordat we ook maar één regel code wijzigen, maken we een **git tag** aan als rollback-punt.

```bash
# 1. Zorg dat alle wijzigingen gecommit zijn
git add -A
git commit -m "chore: checkpoint voor Godclass-refactor TellingScherm"

# 2. Maak een git tag (duidelijk milestone op de tijdlijn)
git tag -a v5.0.0-godclass-before -m "Milestone: TellingScherm Godclass-refactor startpunt"

# 3. (Optioneel) push tag naar remote
git push origin v5.0.0-godclass-before
```

> **Waarom een tag?** Een tag is een vast punt op de git-tijdlijn dat niet verandert.
> Je kunt er altijd met `git checkout v5.0.0-godclass-before` naar terugkeren,
> ongeacht hoeveel commits er later bij komen. Dit is veiliger dan alleen een backup-bestand.

### Fase 0: ✅ Rollback-punt instellen (afgerond)

- [x] **Stap 0.1** — Commit alle huidige wijzigingen
- [x] **Stap 0.2** — Maak git tag `v5.0.0-godclass-before`
- [x] **Stap 0.3** — Bevestig dat tag bestaat (`git tag -l`)

---

## 1. Vermoedelijke oorzaak

`TellingScherm.kt` is organisch gegroeid doordat alle scherm-logica, initialisatie, event-handling, UI-updates en coördinatie van sub-componenten in één Activity-klasse zijn gestopt. Elke nieuwe feature (BirdNET-ticker, spraakherkenning, upload, annotaties, tegelbeheer, etc.) voegde directe dependencies en methodes toe aan deze ene klasse, zonder duidelijke scheiding van verantwoordelijkheden. Het ontbreken van Dependency Injection (Hilt/Koin) versterkt dit: alle helpers worden handmatig in `initializeHelpers()` aangemaakt en als `lateinit var`-velden bijgehouden.

---

## 2. Betrokken code

| Onderdeel | Bestand | Omvang |
|-----------|---------|--------|
| **Godclass Activity** | `TellingScherm.kt` | ~3123 lijnen |
| **ViewModel (passief)** | `TellingViewModel.kt` | Wordt gebruikt als spiegel, niet als source of truth |
| **Helpers (~20 stuks)** | Diverse bestanden in `features/telling/` | Bijv. `TellingSpeechHandler`, `TellingSpeciesManager`, `TellingUploadCore`, `TellingEnvelopePersistence`, `TellingSessionManager`, `TellingLogManager`, `TellingUiManager`, `TellingBackupManager`, etc. |
| **Sub-schermen** | `HuidigeStandScherm.kt`, `TellingBeheerScherm.kt`, `AnnotatieScherm.kt`, `RecordsBeheer.kt`, `TegelBeheer.kt` | Aparte schermen die vanuit TellingScherm worden aangeroepen |

---

## 3. Gefaseerd plan met afvinklijst

### Fase 1: 🟢 ViewModel als echte source of truth (laag risico, ~2-3 dagen)

- [ ] **Stap 1.1** — Identificeer alle state-variabelen in `TellingScherm.kt` die naar ViewModel moeten
- [ ] **Stap 1.2** — Verplaats state naar `TellingViewModel` met `StateFlow`/`MutableStateFlow`
- [ ] **Stap 1.3** — Vervang `LiveData` in ViewModel door `StateFlow` (consistentie)
- [ ] **Stap 1.4** — Laat `TellingScherm` state observeren via `repeatOnLifecycle` i.p.v. directe manipulatie
- [ ] **Stap 1.5** — Verplaats coördinatie-logica (bijv. spraak → species → upload) naar ViewModel
- [ ] **Stap 1.6** — Test rotatie-herstel: state moet behouden blijven
- [ ] **Stap 1.7** — Commit + tag: `v5.0.0-fase1-viewmodel`

**Resultaat:** `TellingScherm.kt` krimpt ~30-40%; ViewModel wordt testbaar; rotatie werkt correct.

---

### Fase 2: 🟡 Extractie van domein-specifieke controllers (medium risico, ~3-5 dagen)

- [ ] **Stap 2.1** — Maak `BirdNetTickerController`: beheert SSE-verbinding + ticker-state
- [ ] **Stap 2.2** — Maak `SpeechInputController`: coördineert spraakherkenning + match-resultaten
- [ ] **Stap 2.3** — Maak `UploadController`: beheert upload queue + retry-logica
- [ ] **Stap 2.4** — Injecteer controllers in ViewModel (via constructor of service-locator)
- [ ] **Stap 2.5** — Verplaats bijbehorende logica uit `TellingScherm` naar controllers
- [ ] **Stap 2.6** — Verwijder overbodige `lateinit var`-velden en `initializeHelpers()`-aanroepen
- [ ] **Stap 2.7** — Commit + tag: `v5.0.0-fase2-controllers`

**Resultaat:** `TellingScherm.kt` nog eens ~20% kleiner; elke controller heeft één verantwoordelijkheid.

---

### Fase 3: 🔴 UI-fragmentatie (optioneel, hoog risico, ~1-2 weken)

- [ ] **Stap 3.1** — Vervang `startActivity(Intent(...))` door Jetpack Navigation
- [ ] **Stap 3.2** — Maak `TellingMainFragment` (tellen)
- [ ] **Stap 3.3** — Maak `BirdNetFragment` (ticker/detecties)
- [ ] **Stap 3.4** — Maak `StatusFragment` (huidige stand)
- [ ] **Stap 3.5** — Maak container-Activity met Navigation Graph
- [ ] **Stap 3.6** — Verwijder oude `TellingScherm.kt` Activity
- [ ] **Stap 3.7** — Commit + tag: `v5.0.0-fase3-fragments`

**Resultaat:** Volledig modulaire UI; type-safe navigation; herbruikbare fragmenten.

---

## Prioriteit & Tijdsinschatting

| Fase | Risico | Geschatte inspanning | Resultaat |
|------|--------|---------------------|-----------|
| Fase 0: Rollback-punt | 🟢 Laag | 5 minuten | ✅ Veilige terugkeer mogelijk |
| Fase 1: ViewModel als source of truth | 🟢 Laag | 2-3 dagen | -30-40% regels in TellingScherm |
| Fase 2: Extractie controllers | 🟡 Medium | 3-5 dagen | -50-60% totaal, testbare units |
| Fase 3: Fragmenten | 🔴 Hoog | 1-2 weken | Volledig modulair, maar grootste wijziging |

**Huidige fase:** ✅ Fase 0 afgerond → **Volgende: Fase 1**

---

## Hoe verder

1. ~~Voer de stappen in **Fase 0** uit (git tag aanmaken).~~ ✅
2. Vink elke stap af in dit document naarmate je vordert.
3. Na elke fase: commit + tag, zodat je altijd een fase kunt terugdraaien.
4. Bij twijfel: `git checkout v5.0.0-godclass-before` brengt je terug naar de start.

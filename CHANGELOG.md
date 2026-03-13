# VoiceTally (VT5) — Version history / Versiegeschiedenis

> This changelog covers every significant commit since the initial import.  
> Het bevat alle wijzigingen van elke betekenisvolle commit sinds de eerste import.  
> Maintained in **NL + EN** — onderhouden in **NL + EN**.

---

## Unreleased / Nog niet uitgebracht

- Master/Client versie/version (checkout releases section / zie releases sectie)

---

## 1.0.3d — 2026-03-12 *(tags: `1.0.3d`, `Beta_release`)*

### NL — Belangrijkste wijzigingen
- **TellingScherm:** grote herstructurering van de telscherm-code; verantwoordelijkheden beter opgesplitst.
- **TellingUiManager:** kleine uitbreidingen aan de UI-manager.
- **HoofdActiviteit:** ~70 regels nieuwe logica toegevoegd (hoofdnavigatie en activiteitsbeheer).
- **Tablet-layout (scherm_telling):** `layout-sw600dp/scherm_telling.xml` verbeterd met extra elementen en betere weergave.
- **Phone-layout (scherm_telling):** `scherm_telling.xml` gelijkaardig uitgebreid voor smartphones.
- **APK-release:** `Voicetally.5.version.1.0.3d.apk` gepubliceerd als Beta voor alle toestellen.

### EN — Main changes
- **TellingScherm:** major restructuring of the counting screen code; cleaner separation of responsibilities.
- **TellingUiManager:** minor expansions to the UI manager.
- **HoofdActiviteit:** ~70 lines of new logic added (main navigation and activity management).
- **Tablet layout (scherm_telling):** `layout-sw600dp/scherm_telling.xml` improved with extra elements and better display.
- **Phone layout (scherm_telling):** `scherm_telling.xml` similarly expanded for smartphones.
- **APK release:** `Voicetally.5.version.1.0.3d.apk` published as Beta for all devices.

---

## 1.0.3c — 2026-03-03 *(tag: `1.0.3c`)*

### NL — Belangrijkste wijzigingen
- **Rechten (permissions):** toestemmingen worden nu actief aangevraagd in het **Instellingen-scherm** en het **Installatie-scherm** (runtime permissions).
- **InstallatieScherm:** installatieflow uitgebreid met ~112 regels nieuwe UI-logica en rechtenbeheer.
- **TellingUploadFlags:** nieuw bestand dat upload-vlaggen beheert (follow-up telling na upload).
- **TellingAfrondHandler:** optie toegevoegd om na het uploaden een vervolgmeting te starten.
- **TellingScherm:** grote uitbreiding (+180 regels) met verbeterde afronding en voorbereidingen voor master-client.
- **TellingLogManager:** extra logregels en opschoning.
- **TellingBeheerScherm:** kleine verbeteringen in het beheeroverzicht.
- **SpeechLogAdapter:** uitgebreide adapter voor spraaklog-weergave (+71 regels).
- **InstellingenScherm:** sterk uitgebreid (+235 regels); meer opties, betere layout.
- **Tablet-instellingen:** `layout-sw600dp/scherm_instellingen.xml` uitgebreid met tabletspecifieke instellingsvelden.
- **Layouts:** `scherm_installatie.xml`, `scherm_instellingen.xml`, `item_telling_beheer.xml`, `scherm_soort_selectie.xml` en `scherm_telling.xml` aangepast.
- **Strings:** 33 nieuwe string-resources toegevoegd.
- **README:** installatie-instructies voor APK's op Samsung-toestellen toegevoegd.
- **Ontwerpdocumenten:** `Future improvements.md` (master-client architectuurontwerp) en `MasterClient audit.md` (bestandsaudit voor master-client) toegevoegd als interne referentiedocumenten.
- **SpeciesUsageScoreStore:** kleine bugfix in de score-store.

### EN — Main changes
- **Permissions:** permissions are now actively requested in the **Settings screen** and **Installation screen** (runtime permissions).
- **InstallatieScherm:** installation flow expanded with ~112 lines of new UI logic and permission handling.
- **TellingUploadFlags:** new file managing upload flags (follow-up count after upload).
- **TellingAfrondHandler:** option added to start a follow-up count after uploading.
- **TellingScherm:** major expansion (+180 lines) with improved finalisation and master-client preparations.
- **TellingLogManager:** additional log entries and cleanup.
- **TellingBeheerScherm:** minor improvements in the management overview.
- **SpeechLogAdapter:** expanded speech log adapter (+71 lines).
- **InstellingenScherm:** heavily expanded (+235 lines); more options, better layout.
- **Tablet settings:** `layout-sw600dp/scherm_instellingen.xml` extended with tablet-specific settings fields.
- **Layouts:** `scherm_installatie.xml`, `scherm_instellingen.xml`, `item_telling_beheer.xml`, `scherm_soort_selectie.xml` and `scherm_telling.xml` updated.
- **Strings:** 33 new string resources added.
- **README:** APK installation instructions for Samsung devices added.
- **Design documents:** `Future improvements.md` (master-client architecture design) and `MasterClient audit.md` (file audit for master-client) added as internal reference documents.
- **SpeciesUsageScoreStore:** minor bugfix in the score store.

---

## 1.0.3b — 2026-02-05 *(tag: `1.0.3b`)*
*(APK release: `Voicetally5-v1.0.3b.apk` — 2026-02-26)*

### NL — Belangrijkste wijzigingen
- **Instellingen (recente soorten):** gebruikers kunnen nu instellen hoeveel **recente/favoriete soorten** in de soortselectie getoond worden: **15 / 20 / 25 / 30 / 35 / 40 / 75 / ALLE**.
  - **ALLE** betekent: alle soorten die gebruikt werden in de **laatste 10 sessies**, met een veiligheidslimiet van **max. 120** soorten.
  - Favorieten zijn **score-based** (geen "pinned" lijst): vaak waargenomen soorten stijgen, "oude" soorten verdwijnen geleidelijk via **week-decay**.
- **SpeciesUsageScoreStore:** nieuw systeem (299 regels) voor score-gebaseerde soortenranking met wekelijkse score-afname.
- **PopupThemeHelper:** nieuwe helper voor consistente popup-theming.
- **Soortselectie:** recents-lijst respecteert nu overal dezelfde ingestelde limiet (phone + tablet).
- **Instellingen UI:** het instellingen-scherm werd compacter gemaakt met **horizontale** NumberPickers (minder verticale hoogte) en blijft scrollbaar; ook de tablet-layout bijgewerkt.
- **Herinneringstimer:** herinneringstijd voor het afronden van tellingen aangepast.
- **Vervolgmeting:** optie toegevoegd om na het uploaden een vervolgmeting te starten.
- **Metadata-scherm:** layout (`dialog_edit_metadata.xml`) sterk herzien.
- **Popup-kleuren:** nieuw `popup_colors.xml` met kleurpalette voor popup-dialogen.
- **Stabiliteit:** extra failsafe in het Instellingen-scherm om hard-crashes door view/id mismatches te vermijden.
- **Annotaties:** kleine update van `annotations.json` (veldbeschrijvingen verfijnd).
- **README:** kompas-functie beschreven; kleine opmaakfixes.

### EN — Main changes
- **Settings (recent species):** users can now configure how many **recent/favorite species** are shown in the species selection screen: **15 / 20 / 25 / 30 / 35 / 40 / 75 / ALL**.
  - **ALL** means: all species observed/used in the **last 10 sessions**, capped at **120** for UI performance.
  - Favorites are **score-based** (no pinned list): frequently used species rise, older ones fade out using a **weekly decay**.
- **SpeciesUsageScoreStore:** new system (299 lines) for score-based species ranking with weekly score decay.
- **PopupThemeHelper:** new helper for consistent popup theming.
- **Species selection:** the recents list now consistently respects the chosen limit across the app (phone + tablet).
- **Settings UI:** the settings screen is now more compact using **horizontal** NumberPickers (less vertical space) while remaining scrollable; tablet layout also updated.
- **Reminder timer:** reminder time for completing counts adjusted.
- **Follow-up count:** option added to start a follow-up count after uploading.
- **Metadata screen:** layout (`dialog_edit_metadata.xml`) heavily revised.
- **Popup colours:** new `popup_colors.xml` with colour palette for popup dialogs.
- **Stability:** a failsafe was added in the settings screen to avoid hard crashes due to view/id mismatches.
- **Annotations:** minor update to `annotations.json` (field descriptions refined).
- **README:** compass feature documented; minor formatting fixes.

---

## 1.0.3 — 2026-01-29 *(tags: `version_1.0.3`, `all_devices_v1.0.3`)*

### NL — Belangrijkste wijzigingen
- **Smartphone-upgrade:** grote herstructurering en uitbreiding van de app voor smartphones.
- **DialogStyler + UiColorPrefs:** nieuw systeem voor consistente dialoog-stijl en kleurinstellingen in de UI.
- **InstellingenScherm:** sterk uitgebreid (+106 regels); meer configuratieopties voor de gebruiker.
- **TelpostDirectionLabelProvider:** nieuwe utility-klasse voor dynamische richtingslabels op basis van telpostgegevens (r1/r2).
- **TellingAlarmHandler:** vereenvoudigd en geherstructureerd.
- **TellingDialogHelper:** opgeschoond en compacter gemaakt.
- **TellingScherm:** geherstructureerd voor betere leesbaarheid en onderhoudbaarheid.
- **TellingSpeciesManager:** verbeterd soortenbeheer.
- **TegelBeheer:** kleine uitbreidingen.
- **InstallationDialogManager:** opgeschoond (installatiedialog-logica ingekort).
- **Layouts:** nieuw `dialog_number_input_dual.xml` voor dubbele getaleninvoer; `item_color_option.xml` voor kleuropties; `scherm_instellingen.xml` volledig herschreven.
- **Strings / Styles / Themes:** 11 nieuwe strings; nieuwe stijlen en theming toegevoegd.
- **AnnotatieScherm:** vernieuwd (tablet- en phone-layout grondig herzien).
- **TellingBeheerScherm:** grote refactoring voor betere UX in het beheeroverzicht.
- **HoofdActiviteit:** uitgebreid voor betere navigatie.
- **Density-iconen:** nieuwe iconen voor compact/standaard/super-compact weergave.
- **APK-release:** `all_devices_v1.0.3.apk` gepubliceerd voor alle toestellen (phone + tablet).

### EN — Main changes
- **Smartphone upgrade:** major restructuring and expansion of the app for smartphones.
- **DialogStyler + UiColorPrefs:** new system for consistent dialog styling and colour settings in the UI.
- **InstellingenScherm:** heavily expanded (+106 lines); more configuration options for the user.
- **TelpostDirectionLabelProvider:** new utility class for dynamic direction labels based on telpost data (r1/r2).
- **TellingAlarmHandler:** simplified and restructured.
- **TellingDialogHelper:** cleaned up and made more compact.
- **TellingScherm:** restructured for better readability and maintainability.
- **TellingSpeciesManager:** improved species management.
- **TegelBeheer:** minor extensions.
- **InstallationDialogManager:** cleaned up (installation dialog logic shortened).
- **Layouts:** new `dialog_number_input_dual.xml` for dual number input; `item_color_option.xml` for colour options; `scherm_instellingen.xml` completely rewritten.
- **Strings / Styles / Themes:** 11 new strings; new styles and theming added.
- **AnnotatieScherm:** renewed (tablet and phone layouts thoroughly revised).
- **TellingBeheerScherm:** major refactoring for better UX in the management overview.
- **HoofdActiviteit:** expanded for better navigation.
- **Density icons:** new icons for compact/standard/super-compact display.
- **APK release:** `all_devices_v1.0.3.apk` published for all devices (phone + tablet).

---

## 1.0.1 — 2025-12-18 *(tag: `Version_1.0.1`)*

### NL — Belangrijkste wijzigingen
- **Tablet-layout telscherm:** nieuw `layout-sw600dp/scherm_telling.xml` (107 regels) voor optimale weergave op tablets (schermbreedte ≥ 600 dp).
- **Gradle-update:** Gradle Wrapper en `libs.versions.toml` bijgewerkt naar recentere versies.
- **Tips voor spraakherkenning:** tips voor betere spraakherkenningsnauwkeurigheid toegevoegd aan de README.
- **README:** herschreven met uitgebreide functionele beschrijving, HTML-regelafbrekingen voor duidelijkheid, en badges voor releases en platformen.
- **Licentie:** nieuw `LICENSE.md` met tweetalige (NL + EN) licentietekst op basis van **CC-BY-NC-SA-4.0** met aangepaste voorwaarden.
- **Auteursrecht:** copyright-jaar in `LICENSE.md` bijgewerkt.
- **Opschoning:** `KMP_ROADMAP.md`, `KMP_MIGRATION_AUDIT.md`, `AUDIT_SAMENVATTING.md` verwijderd (tijdelijke analyse-documenten).

### EN — Main changes
- **Tablet layout counting screen:** new `layout-sw600dp/scherm_telling.xml` (107 lines) for optimal display on tablets (screen width ≥ 600 dp).
- **Gradle update:** Gradle Wrapper and `libs.versions.toml` updated to more recent versions.
- **Speech recognition tips:** tips for better speech recognition accuracy added to the README.
- **README:** rewritten with detailed functional description, HTML line breaks for clarity, and badges for releases and platforms.
- **License:** new `LICENSE.md` with bilingual (NL + EN) license text based on **CC-BY-NC-SA-4.0** with custom terms.
- **Copyright:** copyright year in `LICENSE.md` updated.
- **Cleanup:** `KMP_ROADMAP.md`, `KMP_MIGRATION_AUDIT.md`, `AUDIT_SAMENVATTING.md` removed (temporary analysis documents).

---

## 1.0.0 — 2025-12-17 *(Copilot PRs #2, #3, #4 — post-initial improvements)*

### NL — Belangrijkste wijzigingen
- **PR #2 — Soortsnaam + kompasknop (`TellingBeheerScherm`):**
  - Soortsnaam wordt nu getoond in het bewerkingsdialoog van een telregel.
  - Kompasknop toegevoegd in `dialog_edit_record_full.xml` voor richting-selectie.
  - `TellingBeheerScherm.kt` uitgebreid met ~221 regels (soortsnaam laden, kompas-integratie).
- **PR #3 — Dynamische richtingslabels (`AnnotatieScherm`):**
  - Richtingslabels (r1/r2) worden nu dynamisch geladen op basis van de telpostgegevens van de site.
  - Nieuw: `TellingAnnotationHandler.kt` als aparte handler voor annotatie-logica.
  - `TellingScherm.kt` uitgebreid voor annotatie-initialisatie.
  - AGP-versie gecorrigeerd van de niet-bestaande 8.10.1 naar geldige 8.5.0.
- **PR #4 — Tablet-layout annotatieScherm:**
  - Nieuw `layout-sw600dp/activity_annotatie.xml` (820 regels) voor een tabletgeoptimaliseerde annotatieweergave.

### EN — Main changes
- **PR #2 — Species name + compass button (`TellingBeheerScherm`):**
  - Species name is now shown in the edit dialog of a counting record.
  - Compass button added in `dialog_edit_record_full.xml` for direction selection.
  - `TellingBeheerScherm.kt` expanded with ~221 lines (loading species name, compass integration).
- **PR #3 — Dynamic direction labels (`AnnotatieScherm`):**
  - Direction labels (r1/r2) are now dynamically loaded based on the site's telpost data.
  - New: `TellingAnnotationHandler.kt` as a separate handler for annotation logic.
  - `TellingScherm.kt` expanded for annotation initialisation.
  - AGP version corrected from non-existent 8.10.1 to valid 8.5.0.
- **PR #4 — Tablet layout annotation screen:**
  - New `layout-sw600dp/activity_annotatie.xml` (820 lines) for a tablet-optimised annotation view.

---

## 1.0.0 — 2025-12-10 *(tag: `Initial_release`)*

### NL — Eerste release / initiële import

De eerste volledige import van VoiceTally (VT5), een Android-app voor gespreksgestuurde vogeltellingen bij erkende telposten.

**Kernfunctionaliteiten bij initiële import:**
- **Spraakherkenning:** realtime spraak-naar-telling via Android SpeechRecognizer; gesproken soortsnamen worden gematcht op de soortsendatabase.
- **Telscherm (`TellingScherm`):** centrale telteller met speech-log, soortselectie, annotatie en afronding.
- **Soortselectie (`SoortSelectieScherm`):** snel zoeken en selecteren van soorten via tegelweergave; recente soorten worden bijgehouden.
- **Annotatiescherm (`AnnotatieScherm`):** richting (r1/r2), gedrag, leeftijd, geslacht en noot per waarneming invoeren.
- **Telbeheer (`TellingBeheerScherm`):** overzicht van alle ingevoerde telregels, met bewerkings- en verwijdermogelijkheid.
- **Metadata-scherm (`MetadataScherm`):** invoer van telpostinfo, telling-ID en overige metadata vóór de telling.
- **Instellingen (`InstellingenScherm`):** basisinstellingen voor de gebruiker.
- **Installatie-scherm (`InstallatieScherm`):** invoer van server-URL, gebruikersnaam en API-sleutel bij eerste gebruik.
- **Weermodule (`WeatherManager`):** real-time weersopvraging en weergave in het telscherm.
- **Backup & herstel (`TellingBackupManager`, `TellingEnvelopePersistence`):** automatisch opslaan van telgegevens bij onderbreking.
- **Uploaden (`DataUploader`, `AfrondWorker`):** volledige upload van telgegevens naar de Trektellen-server na afronding.
- **Alias-systeem:** intern systeem voor het koppelen van gesproken varianten van soortsnamen aan de juiste soort.
- **Serverdata-samples:** voorbeeldbestanden (soorten, sites, protocollen, annotaties) voor lokale tests.
- **Stijlen & thema's:** custom drawable-assets, kleurenschema, stijlen en tabletondersteuning.

**Directe opschoning na import (2025-12-10):**
- Gevoelige gebruikersdata verwijderd uit `checkuser.json`.
- Alias-CSV-bestanden (`user_aliases.csv`, `aliasmapping.csv`, `alias_merged.csv`, `alias_index.json`) verwijderd; vervangen door een modern inline alias-systeem.
- IDE Copilot-migratiebestanden verwijderd (`.idea/copilot.data.migration.*.xml`).
- Interne auditdocumenten verwijderd: `AUDIT_RESULTATEN.md`, `BUILD_FIX_EXPLANATION.md`, `SUMMARY_FOR_USER.md`, `TELLING_BEHEER_AUDIT.md`, `TOEKOMSTIGE_SUGGESTIES.md`, `.github/copilot-instructions.md`.

### EN — First release / initial import

The first full import of VoiceTally (VT5), an Android app for voice-driven bird counts at recognised counting posts.

**Core features at initial import:**
- **Speech recognition:** real-time speech-to-count via Android SpeechRecognizer; spoken species names are matched against the species database.
- **Counting screen (`TellingScherm`):** central count recorder with speech log, species selection, annotation and finalisation.
- **Species selection (`SoortSelectieScherm`):** fast search and selection of species via tile view; recently used species are tracked.
- **Annotation screen (`AnnotatieScherm`):** enter direction (r1/r2), behaviour, age, sex and note per observation.
- **Count management (`TellingBeheerScherm`):** overview of all entered count rows with edit and delete options.
- **Metadata screen (`MetadataScherm`):** enter telpost info, count ID and other metadata before counting.
- **Settings (`InstellingenScherm`):** basic user settings.
- **Installation screen (`InstallatieScherm`):** enter server URL, username and API key on first use.
- **Weather module (`WeatherManager`):** real-time weather fetching and display in the counting screen.
- **Backup & restore (`TellingBackupManager`, `TellingEnvelopePersistence`):** automatic saving of count data on interruption.
- **Upload (`DataUploader`, `AfrondWorker`):** full upload of count data to the Trektellen server after finalisation.
- **Alias system:** internal system for mapping spoken variants of species names to the correct species.
- **Server data samples:** example files (species, sites, protocols, annotations) for local testing.
- **Styles & themes:** custom drawable assets, colour scheme, styles and tablet support.

**Direct cleanup after import (2025-12-10):**
- Sensitive user data removed from `checkuser.json`.
- Alias CSV files (`user_aliases.csv`, `aliasmapping.csv`, `alias_merged.csv`, `alias_index.json`) removed; replaced by a modern inline alias system.
- IDE Copilot migration files removed (`.idea/copilot.data.migration.*.xml`).
- Internal audit documents removed: `AUDIT_RESULTATEN.md`, `BUILD_FIX_EXPLANATION.md`, `SUMMARY_FOR_USER.md`, `TELLING_BEHEER_AUDIT.md`, `TOEKOMSTIGE_SUGGESTIES.md`, `.github/copilot-instructions.md`.

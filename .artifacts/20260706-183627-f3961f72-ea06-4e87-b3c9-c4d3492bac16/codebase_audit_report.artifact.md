# Codebase Audit & Optimization Report

This report documents the findings of a thorough audit of the VoiceTally codebase, focusing on redundancy, performance, and APK size optimization.

## Status: IN PROGRESS
**Last Updated:** 2024-05-22 (Simulated)

---

## 1. Redundant Files & Unused Code

### Redundant Files (Safe to Delete)
- `app/src/main/java/com/yvesds/vt5/features/serverdata/model/ServerDataRepository.kt.backup`: Redundant backup file.
- `app/src/main/java/com/yvesds/vt5/features/alias/PrecomputeAliasIndex.kt`: Logic is already duplicated in `AliasManager` and `AliasCborRebuilder`.
- `app/src/main/res/layout/dialog_pairing_client.xml`: Unused layout.
- `app/src/main/res/layout/item_speech_log_secondary.xml`: Unused layout.

### Unused Resources (Safe to Delete)
- **Strings**:
    - `beheer_gearchiveerd`: Not used.
    - `msg_annotations_not_available`: Not used.
    - `instellingen_opslag_titel` (+ options and description): Choice UI removed, logic hardcoded to "Parallel".
    - `instellingen_placeholder3`: Placeholder.
- **Drawables**:
    - `ic_vertical_align_foreground.xml`: Likely a project template leftover.

### Unused Functions/Functionaliteit
- **Alias System**:
    - `AliasSafWriter.writeCopyToExports`: Never called.
    - `AliasRepository.addAliasInMemory`, `getAliasesForSpecies`, `getAllAliases`: No callers.
    - `AliasManager.getLoadedIndex`, `isIndexLoaded`, `forceFlush`, `mergeUserAliasesIntoMaster`: No callers.
- **Telling System**:
    - `TellingBeheerToolset.updateRecordOpmerkingen`, `updateRecordAantal`, `deleteRecords`: Remnants of older editor logic.
    - `AfrondManager.getWorkInfoLiveData`, `getWorkInfo`: Unused helpers.
    - `TellingViewModel.clearTiles`, `clearPartials`, `clearFinals`, `addPendingRecord`: Unused methods.
    - `TegelBeheer.logTilesState`: Unused debug method.

### Unused Dependencies (Fixed)
The following libraries have been removed from `build.gradle.kts`:
- `org.jetbrains.kotlin:kotlin-reflect`: Removed (Saved ~2MB).
- `commons-codec:commons-codec`: Removed.
- `org.apache.commons:commons-text`: Removed.

---

## 2. Performance Optimizations

### Redundant Memory Usage (Alias System)
The app currently loads the same Alias data into memory **three times** in three different formats across three classes (`AliasManager`, `AliasRepository`, `AliasMatcher`). This consumes significantly more RAM than necessary and adds disk I/O overhead at startup.
- **Recommendation:** Consolidate these into a single optimized Alias Cache managed by `AliasManager`.

### Triple Redundant Levenshtein Code
The Levenshtein algorithm is implemented **four times** as private methods in separate classes.
- **Recommendation:** Move to a single shared `LevenshteinUtils` object for better maintenance and consistency.

### Background Preloading
Startup preloading in `VT5App` is already quite good, using background scopes to avoid blocking the UI. However, consolidating the Alias loaders would further improve startup speed.

---

## 3. APK Size Reduction (Compactness)

### Minification & Shrinking (Enabled)
- **Enable R8:** `isMinifyEnabled = true` and `isShrinkResources = true` have been set in `build.gradle.kts` for release builds. This will strip unused code and resources, leading to a much smaller APK.

### Unused Libraries (Fixed)
- Removed `kotlin-reflect`, `commons-codec`, and `commons-text` from the build configuration.

### Image Optimization
- `vt5.png` (**299 KB**) can be converted to **WebP** to save space without losing quality.

---

## 4. Maintenance & Refactoring

### Large Classes ("God Objects")
- `TellingScherm.kt` (>3000 lines) and `TellingBeheerScherm.kt` (>1400 lines) are oversized. They handle too many responsibilities (UI, State, I/O, Network).
- **Recommendation:** Extract logic into separate controllers/use-cases (e.g., `MasterClientController`, `VoiceRecognitionHandler`, `TellingStorageUseCase`).

### Dead Code Cleanup
- The "Storage Mode" logic should be cleaned up. Since "Parallel" is the only supported mode now, the supporting logic for other modes and the unused UI strings should be removed to keep the codebase clean.

---

## 5. "Afronden" Performance Analysis

### Current Bottlenecks (High Impact)
The process of finishing a count ("Afronden") is currently slow due to several factors:
1.  **Redundant DB Operations:** The app fetches all records from Room, and after a successful upload, it writes them **back to Room one by one** in a loop. For a count with many records, this causes hundreds of unnecessary DB transactions.
2.  **Sequential Slow IO (SAF):** Multiple slow SAF (Storage Access Framework) writes happen sequentially (Pretty JSON backup, Audit file, Final archive). These block the UI transition.
3.  **Fixed UI Delays:** There is a hardcoded 2-second delay for the success dialog in `TellingScherm` before showing the follow-up options.
4.  **Network Overhead:** The transition to a new count requires two sequential network requests (Finalize old -> Start new), which compounds if the connection is weak.

### Recommendations for Optimization
- **Batch DB Operations:** Replace the record-by-record loop in `TellingAfrondHandler` with a single batch operation, or remove it entirely if the records were already sourced from Room.
- **Incremental Payload Building:** Maintain a draft `ServerTellingEnvelope` in memory (e.g., in `TellingViewModel`) and update it with every new observation. This makes the payload "ready-to-send" at the moment of completion.
- **Background Post-Processing:** Move non-critical SAF writes (auditing, historical archiving) to a background scope that doesn't block the user's transition to the next screen.
- **Reduce UI Latency:** Shorten the fixed success delay or replace the blocking dialog with a non-blocking notification (Snackbar).
- **Parallelize "Start New":** Pre-calculate/pre-fetch the necessary metadata for the next count while the previous one is still finalizing.

---

## 6. Database Strategy for Active Counts

### Analysis of Temporary Tables
The suggestion to use separate tables for active counts (e.g., `ActiveWaarneming`) has been evaluated against the current indexed single-table approach.

**Current State:**
The `waarnemingen` table already has an **index** on `tellingid`. In SQLite, this means that retrieving records for the current session does NOT require a linear scan of the entire database. It is a logarithmic lookup, which remains extremely fast even with tens of thousands of rows.

### Pros and Cons of Temporary Tables

| Feature | Pros (Separate Active Tables) | Cons (Separate Active Tables) |
| :--- | :--- | :--- |
| **Speed** | Minimal performance gain in fetching (since index alreayd exists). | Performance hit during "Afronden" when moving data between tables. |
| **Complexity** | Clearly separates "draft" work from "archived" history. | **High:** Dual schema requires duplicate entities, DAOs, and complex logic to handle kaar. |
| **Reliability** | "Afronden" could be a single transaction move. | High risk of data loss if the "move" operation fails or is interrupted. |
| **Maintenance** | Easier to manually clear a "stuck" session. | Schema migrations become more complex with every DB update. |

### Recommendation
Instead of adding tables (which increases complexity and risk of breaking changes), we should focus on:
1.  **Batch Processing:** Ensure "Afronden" uses a single database transaction instead of a loop.
2.  **State Consolidation:** Use the `TellingViewModel` to keep the "Active Session" in memory as a draft payload, backed by the existing Room tables.
3.  **Refined Indexing:** The current indexing is alreayd sufficient for high-speed lookups.

---

## 7. Concrete Optimization Plan for "Afronden"

To eliminate the delays when finishing a count, the following concrete steps are proposed:

### 1. Continuous Payload Sync (In-Memory)
- **Action:** Maintain a `ServerTellingEnvelope` object in `TellingViewModel`.
- **Mechanism:** Every time a record is added or changed, the in-memory envelope is updated instantly.
- **Benefit:** At the moment of "Afronden", there is no need to query the database or rebuild the list. The payload is "hot" and ready to send.

### 2. Immediate Async JSON Backup
- **Action:** Continue using `active_telling.json` but ensure it is written asynchronously on every change.
- **Benefit:** If the app crashes, the latest state is always on disk, but the user never feels the I/O lag during active counting or finalization.

### 3. Background Post-Processing (Fire & Forget)
- **Action:** Move non-essential operations after a successful server response to a background scope:
    - **Batch Room Update:** One transaction to mark all records as "geupload".
    - **Archive Move:** Move/rename the JSON from `counts/` to `exports/`.
    - **Audit Log:** Write the server response log in the background.
- **Benefit:** The UI can transition to the "New Count" dialog immediately after the network call finishes.

### 4. UI Streamlining
- **Action:** Remove the fixed 2-second `SUCCESS_DIALOG_DELAY_MS`. Replace the blocking success dialog with a non-blocking notification (e.g., a brief Snackbar or a fast-fading overlay) and show the "Follow-up" options immediately.
- **Benefit:** Saves 2 seconds of human waiting time per count.

### 5. Parallel "New Count" Preparation
- **Action:** While the previous count is finishing its background tasks, allow the `MetadataScherm` to pre-load essentials (sites/codes) so it is ready the moment the user enters.


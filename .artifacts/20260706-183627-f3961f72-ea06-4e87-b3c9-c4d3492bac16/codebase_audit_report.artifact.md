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

### Unused Dependencies (High Size Impact)
The following libraries are in `build.gradle.kts` but are NOT imported or used by any code:
- `org.jetbrains.kotlin:kotlin-reflect`: A very heavy library (~2MB) that is not needed for the current project setup.
- `commons-codec:commons-codec`: App uses standard Java `MessageDigest`.
- `org.apache.commons:commons-text`: App uses internal Levenshtein implementations.

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

### Minification & Shrinking
- **Enable R8:** Setting `isMinifyEnabled = true` and `isShrinkResources = true` in `build.gradle.kts` will strip unused code and resources, leading to a much smaller APK.

### Unused Libraries
- Removing `kotlin-reflect`, `commons-codec`, and `commons-text` as mentioned above will reduce the APK size by several megabytes.

### Image Optimization
- `vt5.png` (**299 KB**) can be converted to **WebP** to save space without losing quality.

---

## 4. Maintenance & Refactoring

### Large Classes ("God Objects")
- `TellingScherm.kt` (>3000 lines) and `TellingBeheerScherm.kt` (>1400 lines) are oversized. They handle too many responsibilities (UI, State, I/O, Network).
- **Recommendation:** Extract logic into separate controllers/use-cases (e.g., `MasterClientController`, `VoiceRecognitionHandler`, `TellingStorageUseCase`).

### Dead Code Cleanup
- The "Storage Mode" logic should be cleaned up. Since "Parallel" is the only supported mode now, the supporting logic for other modes and the unused UI strings should be removed to keep the codebase clean.


# Incremental Batch CSV Import with Auto-Restarts

Modify the batch import system to process exactly **one** pair (one year for one telpost) at a time, followed by an automatic app restart. This ensures maximum memory stability for the 160,000+ observations. The system will track progress in `SharedPreferences` and resume automatically upon restart until all 104 files (52 pairs) are processed.

## Proposed Changes

### Core Import Logic

#### [CsvImportManager.kt](file:///C:/AndroidApps/VoiceTally/app/src/main/java/com/yvesds/vt5/core/import/CsvImportManager.kt)

- Add a method `getPendingImportPairs()` that returns a list of all recognized header/data pairs in `VT5/imports`.
- Refactor the existing logic to easily import a specific pair given its filenames.

---

### UI and State Management

#### [DatabaseBeheerScherm.kt](file:///C:/AndroidApps/VoiceTally/app/src/main/java/com/yvesds/vt5/core/database/ui/DatabaseBeheerScherm.kt)

- **State Persistence**: Use `SharedPreferences` to store:
    - `pref_batch_import_active` (Boolean)
    - `pref_batch_import_total_pairs` (Int)
    - `pref_batch_import_processed_pairs` (Int)
- **Automatic Resume**: In `onCreate`, check if `pref_batch_import_active` is true. If so, automatically trigger the next pair import.
- **Incremental Import Loop**:
    1. Scan folder and identify all pairs.
    2. Import the *next* pair in the list (index based on `processed_pairs`).
    3. Update `processed_pairs` counter.
    4. If more pairs remain, show a brief message and **restart the app immediately**.
    5. If all pairs are done, clear the flags and show a final summary.

---

### UI Feedback

- During each step, show a progress dialog: "Batch Import: Verwerken 12 van 52 (Telpost 74, 2003)... App herstart hierna automatisch."

## Verification Plan

### Manual Verification
1. Place 4 CSV files (2 pairs) in `VT5/imports`.
2. Open **Database Management** and click **Batch Import**.
3. Verify that the first pair is imported.
4. Verify that the app restarts automatically.
5. Verify that upon restart, the app automatically opens **Database Management** and begins importing the second pair.
6. Verify that after the second pair, the import finishes and no further restarts occur.
7. Check the database to ensure all records from both pairs are present.

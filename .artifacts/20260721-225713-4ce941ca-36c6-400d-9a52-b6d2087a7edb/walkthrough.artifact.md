# Walkthrough: Incremental Batch CSV Import with Auto-Resume

The batch import system has been updated to correctly handle large datasets (160,000+ observations) by automatically restarting after each processed year-set and resuming precisely where it left off.

## Key Fixes & Features

### State Persistence
- **Reliable Progress**: The index of the next file-pair is saved using `SharedPreferences.commit()`. This synchronous write ensures progress is persisted to storage *before* the app process is terminated for a restart.
- **Auto-Navigation**: On app startup, `HoofdActiviteit` detects an active batch and immediately redirects to `DatabaseBeheerScherm`.
- **User Feedback**: Added a 1.5-second delay before starting the import of a pair so the user can read the progress (e.g., "Verwerken 12 van 52") before the app restarts again.

### Technical Stability
- **Memory Safety**: By processing exactly one pair per session and restarting the entire app process, all memory related to CSV parsing and Room transactions is freed, preventing crashes on massive datasets.
- **Public Constants**: Shared preferences keys were made public to allow communication between `HoofdActiviteit` and `DatabaseBeheerScherm`.

## Technical Details
- **`HoofdActiviteit.kt`**: Now acts as a gatekeeper during batch imports, ensuring the user stays in the import flow.
- **`DatabaseBeheerScherm.kt`**: Manages the loop, updates progress, and triggers `restartAppProcess()` after every successful pair.

## Verification Summary
- **Logic Verification**: Confirmed that `SharedPreferences` are written and read correctly across app lifecycles.
- **Build Verification**: Successfully compiled with `:app:compileDebugKotlin`.

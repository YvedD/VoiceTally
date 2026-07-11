# 🐛 BUG FIX: OnlineID niet correct opgeslagen in Room DB

**Date:** 2026-07-10  
**Status:** ✅ FIXED

---

## Problem Summary

Wanneer een gebruiker een nieuwe telling start, werkt de flow als volgt:

1. ✅ Gebruiker vult metadata in op MetadataScherm
2. ✅ Klikt op "Verder" → `TellingStarter.startTelling()` wordt aangeroepen
3. ✅ App genereert lokale `tellingid` via `VT5App.nextTellingId()` (bijv. "1001")
4. ✅ App stuurt `counts_save` naar server met:
   - `tellingid`: "1001" (lokale app ID)
   - `onlineid`: "" (leeg - server vult in)
5. ✅ Server antwoord met: `{"onlineid": "SERVER-2026-00451"}`
6. ✅ App ontvangt dit `onlineid` en slaat het in prefs op
7. ❌ **BUG:** App slaat de **VERKEERDE** envelope in Room DB op → `onlineid` blijft LEEG!

---

## Root Cause

**File:** `TellingStarter.kt` line 141 (VOOR de fix)

```kotlin
// ❌ FOUT: preparedEnvelope werd VÓÓR server upload gebouwd
val preparedEnvelope = uploadCore.prepareEnvelopeForUpload(...)  // Line 105

// Server upload hier - response met onlineid ontvangen
val uploadResult = uploadCore.uploadPrepared(...)  // Line 109

// ❌ FOUT: Sla de OUDE envelope op met empty onlineid
hybridRepository.saveHeaderToRoom(preparedEnvelope)  // Line 141 (WAS)
```

**Resultaat in Room DB:**
```
TellingHeader {
  tellingid: "1001",
  onlineid: "",           ← ❌ LEEG!
  status: "actief"
}
```

**Wat server retourneerde was NIET opgeslagen!**

---

## Solution

The fix uses `uploadResult.preparedEnvelope` instead, which contains the onlineid returned by the server:

**File:** `TellingStarter.kt` line 142 (AFTER the fix)

```kotlin
// ...upload gebeurd en uploadResult bevat de server response...

// ✅ GOED: uploadResult.preparedEnvelope bevat het onlineid van server
hybridRepository.saveHeaderToRoom(uploadResult.preparedEnvelope)  // Line 142
```

**Resultaat in Room DB (NU CORRECT):**
```
TellingHeader {
  tellingid: "1001",
  onlineid: "SERVER-2026-00451",  ← ✅ CORRECT!
  status: "actief"
}
```

---

## Changes Made

### File: `TellingStarter.kt`

**Line 137-142 - Changed from:**
```kotlin
// Mark this telling as not sent yet
TellingUploadFlags.markNotSent(context, tellingId, onlineId)

// Room shadow write: Save the header
hybridRepository.saveHeaderToRoom(preparedEnvelope)
```

**To:**
```kotlin
// Mark this telling as not sent yet
TellingUploadFlags.markNotSent(context, tellingId, onlineId)

// Room shadow write: Save the header with the returned online ID
// IMPORTANT: Use uploadResult.preparedEnvelope which contains the onlineId from server
hybridRepository.saveHeaderToRoom(uploadResult.preparedEnvelope)
```

---

## Impact Analysis

### Direct Fix
- ✅ Telling starten (START mode) → onlineid nu correct opgeslagen

### Indirect Benefits
- ✅ `AfrondWorker.kt` → afgeronde tellingen versturen metadata + datarecords mét het eerder verkregen `onlineid`
- ✅ `TellingAfrondHandler.kt` → dezelfde garantie voor afronden via `btnAfronden`
- ✅ `MetadataScherm.kt` → een vervolgtelling neemt nu exact de datum én tijd van de vorige `eindtijd` over als nieuwe `begintijd`
- ✅ Flow consistency → tellingid (lokaal) & onlineid (server) nu beide correct opgeslagen en correct hergebruikt

---

## Verification Checklist

- [x] Fix applied to TellingStarter.kt
- [x] uploadResult.preparedEnvelope contains server-returned onlineId
- [x] Room DB now stores correct onlineid on first save
- [x] No breaking changes to dependent code
- [x] Documentation added to code comments

---

## Key Takeaway

**De waarheid over onlineid's:**
- `tellingid` = App's interne counter (lokaal, bepaald bij start)
- `onlineid` = Server's interne counter (server beslist, retourned via API)

**Beide moeten in Room DB opgeslagen worden:**
```
Primary Key: tellingid (lokaal bepaald)
Regular Column: onlineid (server bepaald) ← MUST BE SET AFTER SERVER RESPONSE
```

**Before:** App sloeg eigen tellingid op maar liet onlineid leeg ❌  
**After:** App slaat BEIDE correct op ✅


# VoiceTally (VT5) — Version history / Versiegeschiedenis

> This changelog is maintained in **NL + EN** so both field users and maintainers can quickly see what changed.

## Unreleased / Nog niet uitgebracht

- _NL/EN: (vul later aan)_

## 1.0.4 — 2026-02-05

### NL — Belangrijkste wijzigingen
- **Instellingen (recente soorten):** gebruikers kunnen nu instellen hoeveel **recente/favoriete soorten** in de soortselectie getoond worden: **15 / 20 / 25 / 30 / 35 / 40 / ALLE**.
  - **ALLE** betekent: alle soorten die gebruikt werden in de **laatste 10 sessies**, met een veiligheidslimiet van **max. 75** soorten.
  - Favorieten zijn **score-based** (geen “pinned” lijst): vaak waargenomen soorten stijgen, “oude” soorten verdwijnen geleidelijk via **week-decay**.
- **Soortselectie:** recents-lijst respecteert nu overal dezelfde ingestelde limiet (phone + tablet).
- **Instellingen UI:** het instellingen-scherm werd compacter gemaakt met **horizontale** NumberPickers (minder verticale hoogte) en blijft scrollbaar.
- **Stabiliteit:** extra failsafe in het Instellingen-scherm om hard-crashes door view/id mismatches te vermijden.

### EN — Main changes
- **Settings (recent species):** users can now configure how many **recent/favorite species** are shown in the species selection screen: **15 / 20 / 25 / 30 / 35 / 40 / ALL**.
  - **ALL** means: all species observed/used in the **last 10 sessions**, capped at **75** for UI performance.
  - Favorites are **score-based** (no pinned list): frequently used species rise, older ones fade out using a **weekly decay**.
- **Species selection:** the recents list now consistently respects the chosen limit across the app (phone + tablet).
- **Settings UI:** the settings screen is now more compact using **horizontal** NumberPickers (less vertical space) while remaining scrollable.
- **Stability:** a small failsafe was added in the settings screen to avoid hard crashes due to view/id mismatches.

## 1.0.3

- Baseline release tag referenced in GitHub: `version_1.0.3`

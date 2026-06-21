# Rollback Punten: Room Database Refactoring

Dit bestand houdt de 'ankerpunten' bij die tijdens de Room-migratie zijn aangemaakt. Bij elk ankerpunt hoort een Git-tag of commit-hash waarmee we de app exact kunnen herstellen naar die staat.

## Ankerpunten

### 1. Start Ankerpunt (Pre-Refactoring)
*   **Datum**: 18 juni 2026
*   **Status**: Branch `room_refactoring` aangemaakt. Alle planningen en indexpagina's zijn voltooid.
*   **Code Status**: Originele staat (geen codewijzigingen doorgevoerd).
*   **Git Tag/Hash**: `pre-room-refactoring`
*   **Beschrijving**: Dit is het absolute nulpunt. Als er iets misgaat tijdens de eerste stappen van de refactoring, keren we terug naar dit punt.

---

## Hoe uit te voeren (Rollback instructies)

Mocht een rollback nodig zijn naar een specifiek punt, dan kan dit via de terminal (na goedkeuring):
`git checkout <tag_naam>`

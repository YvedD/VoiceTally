# Modernisering BirdNet-GO Verbinding

Dit plan beschrijft de overstap naar een modernere en efficiëntere manier om de Raspberry Pi met BirdNET-GO te vinden en ermee te communiceren. We stappen af van de zware "subnet scan" en gebruiken voortaan **Network Service Discovery (NSD)** en een robuustere **SSE-client**.

## User Review Required

> [!IMPORTANT]
> Voor een optimale werking moet de Raspberry Pi zo geconfigureerd zijn dat hij zijn aanwezigheid op het netwerk bekendmaakt (zie "Manual Verification" voor details over Avahi).

## Proposed Changes

### BirdNet Component

#### [MODIFY] [BirdNetDiscovery.kt](file:///C:/Eigen%20bestanden%20Yves/Programeren/Android/VoiceTally/app/src/main/java/com/yvesds/vt5/features/birdnet/BirdNetDiscovery.kt)
*   Introductie van `NsdManager` om de Raspberry Pi passief te ontdekken op het netwerk.
*   Behoud van de `birdnet.local` fallback voor situaties waarin service discovery vertraagd is.
*   Verwijdering van de batterij-intensieve subnet scan.

#### [MODIFY] [BirdNetSseClient.kt](file:///C:/Eigen%20bestanden%20Yves/Programeren/Android/VoiceTally/app/src/main/java/com/yvesds/vt5/features/birdnet/BirdNetSseClient.kt)
*   Refactoring naar een `Flow`-gebaseerde architectuur voor betere integratie met Kotlin Coroutines.
*   Verbeterde foutafhandeling en status-updates.

#### [MODIFY] [TellingScherm.kt](file:///C:/Eigen%20bestanden%20Yves/Programeren/Android/VoiceTally/app/src/main/java/com/yvesds/vt5/features/telling/TellingScherm.kt)
*   Aanpassing van de ticker-logica om gebruik te maken van de nieuwe `Flow` van de SSE-client.
*   Automatische start van discovery bij het openen van het scherm.

## Verification Plan

### Automated Tests
*   Geen specifieke nieuwe unit tests, bestaande flow moet behouden blijven.

### Manual Verification
*   **Raspberry Pi Configuratie:** Zorg dat `avahi-daemon` draait op de Pi.
*   **Test scenario:** Start de app, activeer BirdNet via het logo, en controleer of `birdnet.local` direct wordt opgepikt zonder handmatig te hoeven scannen.
*   **Simulatie:** Verbreek de verbinding (Pi uit) en controleer of de app netjes "herverbinden" toont en de draad weer oppakt zodra de Pi terug is.

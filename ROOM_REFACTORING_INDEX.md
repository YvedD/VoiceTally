# Index: Room Database Refactoring Plan

Dit bestand dient als centraal overzicht voor het stapsgewijze migratieplan naar de Room database. Klik op een van de onderstaande links om het gedetailleerde plan voor die specifieke stap te bekijken.

## Hoofddocumentatie
*   [**Volledige Analyse & Strategie**](file:///C:/Users/ydsds/AppData/Local/Google/AndroidStudio2026.1.1/projects/voicetally.428511a0/.artifacts/20260618-093225-55b902f3-d563-4a00-ada3-a14fa4eb047e/room_migration_analysis.artifact.md)

---

## Fase 1: Voorbereiding & Database Compatibiliteit
*   [**Stap 1.1**: ObservationEntity Uitbreiding](file:///C:/Users/ydsds/AppData/Local/Google/AndroidStudio2026.1.1/projects/voicetally.428511a0/.artifacts/20260618-093225-55b902f3-d563-4a00-ada3-a14fa4eb047e/step_1.1_observation_entity.artifact.md)
*   [**Stap 1.2**: Repository Mapping Logica](file:///C:/Users/ydsds/AppData/Local/Google/AndroidStudio2026.1.1/projects/voicetally.428511a0/.artifacts/20260618-093225-55b902f3-d563-4a00-ada3-a14fa4eb047e/step_1.2_repository_mapping.artifact.md)

## Fase 2: ViewModel & Reactieve Flows
*   [**Stap 2.1**: ViewModel Dependency Injection](file:///C:/Users/ydsds/AppData/Local/Google/AndroidStudio2026.1.1/projects/voicetally.428511a0/.artifacts/20260618-093225-55b902f3-d563-4a00-ada3-a14fa4eb047e/step_2.1_viewmodel_injection.artifact.md)
*   [**Stap 2.2**: Lazy Database Flow in ViewModel](file:///C:/Users/ydsds/AppData/Local/Google/AndroidStudio2026.1.1/projects/voicetally.428511a0/.artifacts/20260618-093225-55b902f3-d563-4a00-ada3-a14fa4eb047e/step_2.2_viewmodel_lazy_flow.artifact.md)
*   [**Stap 2.3**: Database CRUD in ViewModel](file:///C:/Users/ydsds/AppData/Local/Google/AndroidStudio2026.1.1/projects/voicetally.428511a0/.artifacts/20260618-093225-55b902f3-d563-4a00-ada3-a14fa4eb047e/step_2.3_viewmodel_crud.artifact.md)

## Fase 3: UI Integratie
*   [**Stap 3.1**: UI LiveData Koppeling](file:///C:/Users/ydsds/AppData/Local/Google/AndroidStudio2026.1.1/projects/voicetally.428511a0/.artifacts/20260618-093225-55b902f3-d563-4a00-ada3-a14fa4eb047e/step_3.1_scherm_livedata.artifact.md)
*   [**Stap 3.2**: Opslag Delegatie in UI](file:///C:/Users/ydsds/AppData/Local/Google/AndroidStudio2026.1.1/projects/voicetally.428511a0/.artifacts/20260618-093225-55b902f3-d563-4a00-ada3-a14fa4eb047e/step_3.2_scherm_storage_delegation.artifact.md)

## Fase 4: Initialisatie & Herstel
*   [**Stap 4.1**: Sessie Herstel via Initializer](file:///C:/Users/ydsds/AppData/Local/Google/AndroidStudio2026.1.1/projects/voicetally.428511a0/.artifacts/20260618-093225-55b902f3-d563-4a00-ada3-a14fa4eb047e/step_4.1_initializer_recovery.artifact.md)

## Fase 5: Afronding & Upload
*   [**Stap 5.1**: Afronding Data Retrieval](file:///C:/Users/ydsds/AppData/Local/Google/AndroidStudio2026.1.1/projects/voicetally.428511a0/.artifacts/20260618-093225-55b902f3-d563-4a00-ada3-a14fa4eb047e/step_5.1_afrond_handler_data.artifact.md)

## Fase 6: Opschonen & Verwijderen Legacy
*   [**Stap 6.1**: Legacy Persistence Opschonen](file:///C:/Users/ydsds/AppData/Local/Google/AndroidStudio2026.1.1/projects/voicetally.428511a0/.artifacts/20260618-093225-55b902f3-d563-4a00-ada3-a14fa4eb047e/step_6.1_cleanup_persistence.artifact.md)
*   [**Stap 6.2**: RecordsBeheer Verwijderen](file:///C:/Users/ydsds/AppData/Local/Google/AndroidStudio2026.1.1/projects/voicetally.428511a0/.artifacts/20260618-093225-55b902f3-d563-4a00-ada3-a14fa4eb047e/step_6.2_cleanup_recordsbeheer.artifact.md)

---
*Opmerking: Deze bestanden zijn fysiek opgeslagen in de project-specifieke artefactenmap:*
`C:/Users/ydsds/AppData/Local/Google/AndroidStudio2026.1.1/projects/voicetally.428511a0/.artifacts/20260618-093225-55b902f3-d563-4a00-ada3-a14fa4eb047e/`

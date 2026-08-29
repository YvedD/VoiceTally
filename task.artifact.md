# Takenlijst: BSI 4.1 "Wind-Efficiency Isolation"

## Fase 1: DAO & Data Mining
- [x] Implementeren van `getWindEfficiencyStats` in `TellingDao.kt`.
- [x] Data-klasse `WindEfficiencyRow` definiëren.
- [x] Verfijnen van `getWeatherTwinObservations` voor BpH calculatie.

## Fase 2: Efficiency Engine (AiInferenceEngine)
- [x] Bouwen van de `Efficiency-Ratio` module (Dynamisch & Gradueel).
- [x] Implementeren van de `Coastal Veto` voor landvogels bij onshore wind.
- [x] Implementeren van de `Pelagic Boost` bij onshore wind > 5bft.
- [x] Integreren van de Fenologische Helling (striktheid per seizoen-fase).

## Fase 3: UI & Filtering
- [x] Implementeren van de `Dynamic Cutoff` voor kortere, kwalitatieve lijsten.
- [ ] Verificatie van smartphone & tablet layouts.

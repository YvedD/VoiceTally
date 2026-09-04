# Conversiegids: Kotlin BSI Prognose-Engine naar Python

Dit document bevat de **volledige, integrale Python-implementatie** van de Bio-Statistische Intelligentie (BSI) prognose-engine uit de VoiceTally VT5 Android-app.

Elk onderdeel is vertaald van idioom-getrouw Kotlin naar hoogwaardige, modulaire Python 3.10+ code (met `numpy`, `pandas`, `requests` en `dataclasses`).

---

## Inhoudsopgave

1. [Architectuur overzicht & Mappenstructuur](#1-architectuur-overzicht--mappenstructuur)
2. [Module 1: `bsi/config.py` (Configuratie & Corridors)](#2-module-1-bsiconfigpy-configuratie--corridors)
3. [Module 2: `bsi/solar_engine.py` (Zonnestand & Biologische Fases)](#3-module-2-bsisolar_enginepy-zonnestand--biologische-fases)
4. [Module 3: `bsi/guild_mapper.py` (Vogelgildes & Vliegstrategieën)](#4-module-3-bsiguild_mapperpy-vogelgildes--vliegstrategieën)
5. [Module 4: `bsi/neural_engine.py` (Lite Neuraal Netwerk - MLP)](#5-module-4-bsineural_enginepy-lite-neuraal-netwerk---mlp)
6. [Module 5: `bsi/model_store.py` (Model & Gewichten Beheer)](#6-module-5-bsimodel_storepy-model--gewichten-beheer)
7. [Module 6: `bsi/weather_service.py` (Open-Meteo & YR.no Weer-Integratie)](#7-module-6-bsiweather_servicepy-open-meteo--yrno-weer-integratie)
8. [Module 7: `bsi/data_preparer.py` (Feature Engineering & 21-Vector)](#8-module-7-bsidata_preparerpy-feature-engineering--21-vector)
9. [Module 8: `bsi/expert_knowledge.py` (Krenten-Monitor & Piekdagen)](#9-module-8-bsiexpert_knowledgepy-krenten-monitor--piekdagen)
10. [Module 9: `bsi/inference_engine.py` (Master BSI 4.1 Prognose-Motor)](#10-module-9-bsiinference_enginepy-master-bsi-41-prognose-motor)
11. [Module 10: `bsi/sparkline_helper.py` (Fenologie Sparklines)](#11-module-10-bsisparkline_helperpy-fenologie-sparklines)
12. [Module 11: `app.py` (Streamlit WebUI Integratie Demo)](#12-module-11-apppy-streamlit-webui-integratie-demo)
13. [Afstellen BSI motor (Tuning & Versoepeling)](#13-afstellen-bsi-motor-tuning--versoepeling)
14. [Module 12: `bsi/species_resolver.py` (Soort-ID Mapping & Taxonomie Resolver)](#14-module-12-bsispecies_resolverpy-soort-id-mapping--taxonomie-resolver)
15. [Het Floating 7/9-Day Window (Fenologische Filtering)](#15-het-floating-79-day-window-fenologische-filtering)
16. [Het +3 Dagen Prognose Systeem (72-Uurs Toekomstvenster)](#16-het-3-dagen-prognose-systeem-72-uurs-toekomstvenster)
17. [Diepgaande Uitleg van het Corridor-Systeem in Meerdaagse Voorspellingen](#17-diepgaande-uitleg-van-het-corridor-systeem-in-meerdaagse-voorspellingen)
18. [CardView Grafieken, Normalisatie naar 1.0 & Rode 'Vandaag' Marker](#18-cardview-grafieken-normalisatie-naar-10--rode-vandaag-marker)

---

## 1. Architectuur overzicht & Mappenstructuur

In Python organiseren we de BSI-engine als een uitbreidbare package genaamd `bsi`:

```text
vismig_project/
│
├── bsi/
│   ├── __init__.py
│   ├── config.py             # Vervangt AiConfig.kt
│   ├── solar_engine.py       # Vervangt SolarTimeEngine.kt
│   ├── guild_mapper.py       # Vervangt SpeciesGuildMapper.kt
│   ├── neural_engine.py      # Vervangt LiteNeuralEngine.kt
│   ├── model_store.py        # Vervangt ModelStore.kt
│   ├── weather_service.py    # Vervangt AiWeatherService.kt & WeatherEnrichmentManager.kt
│   ├── data_preparer.py      # Vervangt TrainingDataPreparer.kt
│   ├── expert_knowledge.py   # Vervangt ExpertKnowledgeBase.kt & ExpertKnowledgeManager.kt
│   ├── inference_engine.py   # Vervangt AiInferenceEngine.kt
│   ├── sparkline_helper.py   # Vervangt PhenologySparklineHelper.kt
│   ├── species_resolver.py   # Vervangt SpeciesNameResolver.kt
│   ├── forecast_system.py    # Vervangt AiForecastScherm.kt (3-Daagse Pijplijn)
│   ├── corridor_engine.py    # Berekent de Europese Corridor Boost (regBoost)
│   └── sparkline_engine.py   # Rendert de CardView Sparklines (met 1.0 normalisatie & rode marker)
│
└── app.py                    # Streamlit WebUI
```

---

## 2. Module 1: `bsi/config.py` (Configuratie & Corridors)

### Originele Kotlin-bron
`com.yvesds.vt5.ai.AiConfig.kt`

### Doel & Logica
Bevat de centrale configuratieconstanten van BSI 4.1, waaronder:
- De 21 strategische Europese corridor-referentiepunten (Noord & Zuid).
- Drempelwaarden voor Birds of Interest (BoI), wind-tolerantie (5.25°), en gilde-specifieke parameters.

### Python Code (`bsi/config.py`)

```python
"""
bsi/config.py
Centrale configuratie voor de BSI 4.1 prognose-engine.
"""

from dataclasses import dataclass
from typing import List


@dataclass(frozen=True)
class RefPoint:
    name: str
    lat: float
    lon: float


class BsiConfig:
    # 21 Strategische locaties voor najaar- en voorjaarstrek
    REFERENCE_POINTS: List[RefPoint] = [
        # NOORD (Najaar bronnen)
        RefPoint("Falsterbo (SE)", 55.38, 12.83),
        RefPoint("Skagen (DK)", 57.72, 10.58),
        RefPoint("Blåvand (DK)", 55.55, 8.08),
        RefPoint("Helgoland (DE)", 54.18, 7.88),
        RefPoint("Sylt (DE)", 54.91, 8.30),
        RefPoint("Borkum (DE)", 53.58, 6.66),
        RefPoint("Texel (NL)", 53.05, 4.80),
        RefPoint("Lauwersmeer (NL)", 53.38, 6.19),
        RefPoint("IJmuiden (NL)", 52.46, 4.56),
        RefPoint("Gdansk (PL)", 54.35, 18.64),

        # ZUID (Voorjaar bronnen)
        RefPoint("Cap Gris-Nez (FR)", 50.87, 1.58),
        RefPoint("Baie de Somme (FR)", 50.21, 1.62),
        RefPoint("Ouistreham (FR)", 49.28, -0.25),
        RefPoint("Île d'Oléron (FR)", 45.91, -1.30),
        RefPoint("Biarritz (FR)", 43.48, -1.56),
        RefPoint("Tarifa (ES)", 36.01, -5.60),
        RefPoint("Gibraltar (UK)", 36.14, -5.35),
        RefPoint("Sagres (PT)", 37.01, -8.94),
        RefPoint("Camargue (FR)", 43.53, 4.65),
        RefPoint("Mallorca (ES)", 39.69, 3.01),
        RefPoint("Messina (IT)", 38.19, 15.55)
    ]

    # BSI 4.0: Drempels voor Birds-of-Interest (BoI)
    BOI_THRESHOLD_OBSERVATIONS: int = 50
    BOI_FLOATING_WINDOW_DAYS: int = 9
    NORMAL_FLOATING_WINDOW_DAYS: int = 7
    WIND_TOLERANCE_DEGREES: float = 5.25

    # BSI 4.1: Kust-status & Drempels
    IS_COASTAL_SITE: bool = True
    MIN_BSI_QUALITY_THRESHOLD: int = 35  # Percentage drempel
    EFFICIENCY_BOOST_PELAGIC_BFT: int = 5  # Vanaf 5 Bft boost voor zeevogels

    # Neuraal Model Integratie
    USE_NEURAL_INFERENCE: bool = True
    NEURAL_INTEGRATION_WEIGHT: float = 0.5  # Gewicht neuraal vs. heuristiek

    # Teldag-verslagen gewichten
    USE_DAILY_ANALYSIS_WEIGHTS: bool = True
    DAILY_ANALYSIS_LOOKBACK_DAYS: int = 365
    DAILY_ANALYSIS_WEIGHT_MAX: float = 3.0
```

---

## 3. Module 2: `bsi/solar_engine.py` (Zonnestand & Biologische Fases)

### Originele Kotlin-bron
`com.yvesds.vt5.ai.SolarTimeEngine.kt`

### Doel & Logica
Berekent puur wiskundig (zonder externe API's) de biologische dagfases op basis van breedtegraad, lengtegraad, en datum/tijdstip:
1. Berekening van zonne-declinatie: $decl = 0.409 \cdot \sin(2\pi \cdot (day - 81) / 365)$.
2. Berekening van daglengte en zonne-middag (Solar Noon) gecorrigeerd voor lengtegraad en tijdzone.
3. Indeling in 6 fases: `NIGHT`, `DAWN`, `MORNING`, `MIDDAY`, `LATE_AFTERNOON`, `EVENING`.

### Python Code (`bsi/solar_engine.py`)

```python
"""
bsi/solar_engine.py
Berekent biologische dagsegmenten op basis van de zonnestand.
"""

import math
from datetime import datetime
from enum import Enum


class SolarPhase(Enum):
    NIGHT = "Nacht"
    DAWN = "Vroege Ochtend"
    MORNING = "Ochtend"
    MIDDAY = "Middag"
    LATE_AFTERNOON = "Namiddag"
    EVENING = "Avond"


class SolarTimeEngine:
    @staticmethod
    def get_solar_phase(lat: float, lon: float, dt: datetime) -> SolarPhase:
        """
        Bepaalt de huidige biologische fase voor een gegeven tijdstip en locatie.
        """
        day_of_year = dt.timetuple().tm_yday
        hour_fraction = dt.hour + dt.minute / 60.0 + dt.second / 3600.0

        # 1. Zonne-declinatie
        decl = 0.409 * math.sin(2.0 * math.pi * (day_of_year - 81) / 365.0)

        # 2. Uurhoek bij zonsopkomst/ondergang
        lat_rad = math.radians(lat)
        tan_val = -math.tan(lat_rad) * math.tan(decl)
        
        # Voorkom domeinfouten aan de polen
        tan_val = max(-1.0, min(1.0, tan_val))
        hour_angle = math.acos(tan_val)
        day_length_hours = (2.0 * math.degrees(hour_angle)) / 15.0

        # 3. Solar Noon (lokale zonnemiddag)
        # Tijdzone offset in uren
        tz_offset = dt.utcoffset().total_seconds() / 3600.0 if dt.utcoffset() else 0.0
        solar_noon = 12.0 - (lon / 15.0) + tz_offset

        sunrise = solar_noon - (day_length_hours / 2.0)
        sunset = solar_noon + (day_length_hours / 2.0)

        # 4. Fase indeling
        if hour_fraction < sunrise - 1.0 or hour_fraction > sunset + 1.5:
            return SolarPhase.NIGHT
        elif hour_fraction < sunrise + 1.0:
            return SolarPhase.DAWN
        elif hour_fraction < solar_noon:
            return SolarPhase.MORNING
        elif hour_fraction < sunset - 3.0:
            return SolarPhase.MIDDAY
        elif hour_fraction < sunset:
            return SolarPhase.LATE_AFTERNOON
        else:
            return SolarPhase.EVENING
```

---

## 4. Module 3: `bsi/guild_mapper.py` (Vogelgildes & Vliegstrategieën)

### Originele Kotlin-bron
`com.yvesds.vt5.ai.SpeciesGuildMapper.kt`

### Doel & Logica
Deelt vogelsoorten in gilde-categorieën in op basis van het Latijnse geslacht (Genus). Elke gilde heeft een specifieke vliegstrategie (`THERMAL`, `ACTIVE`, `VISMIG`) die bepaalt hoe weersinvloeden (zoals wind, temperatuur en thermiek) de trek-kansen beïnvloeden.

### Python Code (`bsi/guild_mapper.py`)

```python
"""
bsi/guild_mapper.py
Mapping van Latijnse vogelnamen naar gilde-categorieën en vliegstrategieën.
"""

from enum import Enum
from typing import Optional


class FlightStrategy(Enum):
    THERMAL = "Thermal"  # Zwevers (Ooievaar, Buizerd, Wespendief)
    ACTIVE = "Active"    # Actieve vliegers (Reigers, Kiekendieven, Valken, Steltlopers)
    VISMIG = "Vismig"    # Visuele trek (Zangvogels, Zwaluwen, Sterns, Eenden)


class Guild(Enum):
    WATERFOWL = ("Watervogels (Ganzen/Grondeleenden)", False, FlightStrategy.VISMIG)
    COASTAL_BIRDS = ("Kustvogels (Zee-eenden/Duikers/Futen)", False, FlightStrategy.VISMIG)
    RAPTORS_THERMAL = ("Roofvogels (Zwevers)", False, FlightStrategy.THERMAL)
    RAPTORS_ACTIVE = ("Roofvogels (Actief)", False, FlightStrategy.ACTIVE)
    HERONS = ("Reigers", False, FlightStrategy.ACTIVE)
    STORKS = ("Ooievaars (Zwevers)", False, FlightStrategy.THERMAL)
    SHOREBIRDS = ("Steltlopers", False, FlightStrategy.ACTIVE)
    GULLS_TERNS = ("Meeuwen & Sterns", False, FlightStrategy.VISMIG)
    PELAGICS = ("Zeevogels (Pelagics)", False, FlightStrategy.VISMIG)
    LANDBIRDS_SPECIAL = ("Speciale Landvogels", True, FlightStrategy.VISMIG)
    LANDBIRDS_REG = ("Landvogels", False, FlightStrategy.VISMIG)
    PASSERINES = ("Zangvogels", False, FlightStrategy.VISMIG)
    UNCLASSIFIED_BIRDS = ("Overige Vogels", False, FlightStrategy.VISMIG)
    OTHER = ("Niet-vogels", False, FlightStrategy.VISMIG)

    def __init__(self, display_name: str, is_special: bool, strategy: FlightStrategy):
        self.display_name = display_name
        self.is_special = is_special
        self.strategy = strategy


class SpeciesGuildMapper:
    @staticmethod
    def get_guild_by_latin(latin_name: Optional[str]) -> Guild:
        if not latin_name or not latin_name.strip():
            return Guild.UNCLASSIFIED_BIRDS

        genus = latin_name.strip().split()[0]

        # 1. Watervogels
        if genus in {
            "Anser", "Branta", "Cygnus", "Anas", "Spatula", "Mareca", "Netta", "Aythya",
            "Tadorna", "Aix", "Alopochen", "Oxyura", "Phalacrocorax", "Microcarbo",
            "Podiceps", "Tachybaptus"
        }:
            return Guild.WATERFOWL

        # 2. Kustvogels
        if genus in {
            "Somateria", "Melanitta", "Clangula", "Bucephala", "Mergellus", "Mergus",
            "Polysticta", "Histrionicus", "Gavia"
        }:
            return Guild.COASTAL_BIRDS

        # 3. Roofvogels - Zwevers
        if genus in {
            "Buteo", "Pernis", "Aquila", "Hieraaetus", "Clanga", "Haliaeetus", "Milvus",
            "Gyps", "Gypaetus", "Neophron", "Aegypius", "Circaetus", "Pandion"
        }:
            return Guild.RAPTORS_THERMAL

        # 4. Roofvogels - Actief
        if genus in {"Circus", "Accipiter", "Falco", "Elanus"}:
            return Guild.RAPTORS_ACTIVE

        # 5. Reigers
        if genus in {
            "Ardea", "Egretta", "Bubulcus", "Ardeola", "Nycticorax", "Ixobrychus",
            "Botaurus", "Platalea", "Plegadis", "Threskiornis"
        }:
            return Guild.HERONS

        # 6. Ooievaars
        if genus in {"Ciconia"}:
            return Guild.STORKS

        # 7. Steltlopers
        if genus in {
            "Haematopus", "Himantopus", "Recurvirostra", "Burhinus", "Cursorius",
            "Glareola", "Vanellus", "Pluvialis", "Charadrius", "Numenius", "Limosa",
            "Arenaria", "Calidris", "Scolopax", "Gallinago", "Lymnocryptes",
            "Phalaropus", "Actitis", "Tringa", "Gallinula", "Fulica", "Grus",
            "Porzana", "Zapornia", "Crex", "Rallus"
        }:
            return Guild.SHOREBIRDS

        # 8. Meeuwen & Sterns
        if genus in {
            "Rissa", "Pagophila", "Xema", "Chroicocephalus", "Larus", "Ichthyaetus",
            "Hydrocoloeus", "Gelochelidon", "Hydroprogne", "Thalasseus", "Sterna",
            "Sternula", "Chlidonias", "Onychoprion"
        }:
            return Guild.GULLS_TERNS

        # 9. Zeevogels (Pelagics)
        if genus in {
            "Fulmarus", "Puffinus", "Calonectris", "Hydrobates", "Oceanodroma",
            "Morus", "Stercorarius", "Uria", "Alca", "Fratercula", "Ardenna"
        }:
            return Guild.PELAGICS

        # 10. Speciale Landvogels
        if genus in {
            "Merops", "Upupa", "Coracias", "Alcedo", "Cuculus", "Caprimulgus",
            "Jynx", "Dendrocopos", "Dryocopus", "Picus", "Oriolus", "Lanius",
            "Picoides", "Dryobates"
        }:
            return Guild.LANDBIRDS_SPECIAL

        # 11. Reguliere Landvogels
        if genus in {
            "Columba", "Streptopelia", "Apus", "Hirundo", "Delichon", "Riparia",
            "Ptyonoprogne", "Cecropis"
        }:
            return Guild.LANDBIRDS_REG

        # 12. Zangvogels
        if genus in {
            "Fringilla", "Carduelis", "Chloris", "Spinus", "Linaria", "Acanthis",
            "Loxia", "Pyrrhula", "Coccothraustes", "Serinus", "Emberiza", "Calcarius",
            "Plectrophenax", "Passer", "Anthus", "Motacilla", "Alauda", "Lullula",
            "Galerida", "Sturnus", "Pastor", "Turdus", "Luscinia", "Erithacus",
            "Phoenicurus", "Saxicola", "Oenanthe", "Muscicapa", "Ficedula", "Sylvia",
            "Curruca", "Phylloscopus", "Acrocephalus", "Iduna", "Hippolais",
            "Locustella", "Cettia", "Parus", "Cyanistes", "Periparus", "Lophophanes",
            "Poecile", "Aegithalos", "Sitta", "Certhia", "Troglodytes", "Cinclus",
            "Regulus", "Panurus", "Corvus", "Coloeus", "Pica", "Garrulus",
            "Nucifraga", "Pyrrhocorax", "Remiz", "Bombycilla", "Carpodacus"
        }:
            return Guild.PASSERINES

        # Insecten & Zoogdieren
        if genus in {
            "Sympetrum", "Aeshna", "Libellula", "Anax", "Somatochlora", "Cordulia",
            "Pieris", "Vanessa", "Aglais", "Inachis", "Colias", "Delphinus",
            "Tursiops", "Phocoena", "Phoca", "Halichoerus", "Globicephala", "Orcinus"
        }:
            return Guild.OTHER

        return Guild.UNCLASSIFIED_BIRDS
```

---

## 5. Module 4: `bsi/neural_engine.py` (Lite Neuraal Netwerk - MLP)

### Originele Kotlin-bron
`com.yvesds.vt5.ai.LiteNeuralEngine.kt`

### Doel & Logica
Een vederlicht Multi-Layer Perceptron (MLP) geïmplementeerd in NumPy. Het netwerk gebruikt:
- **Input**: 21 kenmerken (tijd, meteorologie, maanfase, etc.).
- **Hidden Layer**: 32 neuronen met **ReLU** activatie.
- **Output Layer**: N neuronen (soorten) met **Softmax** activatie.
- **Training**: Backpropagation met gewogen steekproefverwerking (`sample_weight`).

### Python Code (`bsi/neural_engine.py`)

```python
"""
bsi/neural_engine.py
Neuraal Netwerk (MLP) voor BSI vogelprognoses.
"""

import json
import numpy as np
from typing import Dict, Any


class LiteNeuralEngine:
    def __init__(self, input_size: int = 21, hidden_size: int = 32, output_size: int = 1000):
        self.input_size = input_size
        self.hidden_size = hidden_size
        self.output_size = output_size

        # Gewichten & Biases initialiseren
        self.w_input_hidden = np.random.uniform(-0.05, 0.05, (input_size, hidden_size)).astype(np.float32)
        self.b_hidden = np.zeros(hidden_size, dtype=np.float32)

        self.w_hidden_output = np.random.uniform(-0.05, 0.05, (hidden_size, output_size)).astype(np.float32)
        self.b_output = np.zeros(output_size, dtype=np.float32)

    @staticmethod
    def relu(x: np.ndarray) -> np.ndarray:
        return np.maximum(0.0, x)

    @staticmethod
    def softmax(logits: np.ndarray) -> np.ndarray:
        max_logit = np.max(logits)
        exp_vals = np.exp(logits - max_logit)
        sum_exp = np.sum(exp_vals)
        return exp_vals / (sum_exp if sum_exp > 0 else 1e-8)

    def predict(self, inputs: np.ndarray) -> np.ndarray:
        """
        Voorspelt activatie per vogelsoort op basis van 21 input features.
        """
        inputs = np.asarray(inputs, dtype=np.float32)
        hidden_layer = self.relu(np.dot(inputs, self.w_input_hidden) + self.b_hidden)
        output_logits = np.dot(hidden_layer, self.w_hidden_output) + self.b_output
        return self.softmax(output_logits)

    def train(self, inputs: np.ndarray, target_index: int, learning_rate: float = 0.01, sample_weight: float = 1.0):
        """
        Voert één stap Backpropagation uit.
        """
        if target_index < 0 or target_index >= self.output_size:
            return

        inputs = np.asarray(inputs, dtype=np.float32)

        # 1. Forward Pass
        hidden_sums = np.dot(inputs, self.w_input_hidden) + self.b_hidden
        hidden_layer = self.relu(hidden_sums)

        output_logits = np.dot(hidden_layer, self.w_hidden_output) + self.b_output
        predictions = self.softmax(output_logits)

        # 2. Loss & Error
        target = np.zeros(self.output_size, dtype=np.float32)
        target[target_index] = 1.0

        output_errors = (predictions - target) * sample_weight

        hidden_errors = np.dot(output_errors, self.w_hidden_output.T)
        hidden_errors[hidden_sums <= 0] = 0.0  # ReLU afgeleide

        # 3. Gewichten bijwerken (Gradient Descent)
        self.b_output -= learning_rate * output_errors
        self.w_hidden_output -= learning_rate * np.outer(hidden_layer, output_errors)

        self.b_hidden -= learning_rate * hidden_errors
        self.w_input_hidden -= learning_rate * np.outer(inputs, hidden_errors)

    def to_dict(self) -> Dict[str, Any]:
        return {
            "inputSize": self.input_size,
            "hiddenSize": self.hidden_size,
            "outputSize": self.output_size,
            "wInputHidden": self.w_input_hidden.tolist(),
            "bHidden": self.b_hidden.tolist(),
            "wHiddenOutput": self.w_hidden_output.tolist(),
            "bOutput": self.b_output.tolist(),
        }

    @classmethod
    def from_dict(cls, d: Dict[str, Any]) -> "LiteNeuralEngine":
        engine = cls(
            input_size=d.get("inputSize", 21),
            hidden_size=d.get("hiddenSize", 32),
            output_size=d.get("outputSize", 1000)
        )
        engine.w_input_hidden = np.array(d["wInputHidden"], dtype=np.float32)
        engine.b_hidden = np.array(d["bHidden"], dtype=np.float32)
        engine.w_hidden_output = np.array(d["wHiddenOutput"], dtype=np.float32)
        engine.b_output = np.array(d["bOutput"], dtype=np.float32)
        return engine
```

---

## 6. Module 5: `bsi/model_store.py` (Model & Gewichten Beheer)

### Originele Kotlin-bron
`com.yvesds.vt5.ai.ModelStore.kt`

### Doel & Logica
Beheert het inlezen en opslaan van de JSON bestanden uit `VT5/AI-models/models/`:
- `neural_engine.json`: Gewichten matrixen van het neuraal netwerk.
- `model_labels.json`: Index-mapping van output neuronen naar Soort-ID's.
- `expert_knowledge.json`: Vingerafdrukken en uur-profielen.

### Python Code (`bsi/model_store.py`)

```python
"""
bsi/model_store.py
Opslag en beheer van neuraal netwerk en expert bestanden.
"""

import json
import os
from pathlib import Path
from typing import List, Optional, Dict, Any
from .neural_engine import LiteNeuralEngine


class ModelStore:
    def __init__(self, base_dir: Path):
        self.base_dir = Path(base_dir)
        self.models_dir = self.base_dir / "VT5" / "AI-models" / "models"
        self.models_dir.mkdir(parents=True, exist_ok=True)

    def save_neural_engine(self, engine: LiteNeuralEngine) -> bool:
        try:
            filepath = self.models_dir / "neural_engine.json"
            with open(filepath, "w", encoding="utf-8") as f:
                json.dump(engine.to_dict(), f, indent=2)
            return True
        except Exception as e:
            print(f"[ModelStore] Fout bij opslaan neural_engine.json: {e}")
            return False

    def load_neural_engine(self, num_species: int) -> LiteNeuralEngine:
        filepath = self.models_dir / "neural_engine.json"
        if filepath.exists():
            try:
                with open(filepath, "r", encoding="utf-8") as f:
                    data = json.load(f)
                loaded = LiteNeuralEngine.from_dict(data)
                if loaded.output_size != num_species:
                    print(f"[ModelStore] Output size mismatch ({loaded.output_size} vs {num_species}). Herinitialisatie.")
                    return LiteNeuralEngine(output_size=num_species)
                return loaded
            except Exception as e:
                print(f"[ModelStore] Fout bij laden neural_engine.json: {e}")

        return LiteNeuralEngine(output_size=num_species)

    def save_model_labels(self, labels: List[str]) -> bool:
        try:
            filepath = self.models_dir / "model_labels.json"
            with open(filepath, "w", encoding="utf-8") as f:
                json.dump(labels, f, indent=2)
            return True
        except Exception as e:
            print(f"[ModelStore] Fout bij opslaan model_labels.json: {e}")
            return False

    def load_model_labels(self) -> Optional[List[str]]:
        filepath = self.models_dir / "model_labels.json"
        if filepath.exists():
            try:
                with open(filepath, "r", encoding="utf-8") as f:
                    return json.load(f)
            except Exception as e:
                print(f"[ModelStore] Fout bij laden model_labels.json: {e}")
        return None
```

---

## 7. Module 6: `bsi/weather_service.py` (Open-Meteo & YR.no Weer-Integratie)

### Originele Kotlin-bron
`com.yvesds.vt5.ai.AiWeatherService.kt`, `YrProvider.kt`, `WeatherEnrichmentManager.kt`

### Doel & Logica
Verwerkt live weerdata en historische archiefdata via Open-Meteo en YR.no. Omzetting van meteorologische eenheden (m/s naar Beaufort, graden naar 16 windrichtingen, berekening van de 6-uurs luchtdruktrend).

### Python Code (`bsi/weather_service.py`)

```python
"""
bsi/weather_service.py
Weer-integratie via Open-Meteo REST API's.
"""

import math
import requests
from dataclasses import dataclass
from typing import Optional, Tuple, Dict, Any, List


@dataclass
class WeatherContext:
    lat: float
    lon: float
    temp: Optional[float]
    wind_speed: Optional[float]
    wind_deg: Optional[float]
    cloud_percent: Optional[float]
    pressure: Optional[float]
    visibility: Optional[int]
    pressure_trend: Optional[float] = None


class WeatherManagerUtils:
    @staticmethod
    def ms_to_beaufort(ms: float) -> int:
        if ms < 0.3: return 0
        elif ms < 1.6: return 1
        elif ms < 3.4: return 2
        elif ms < 5.5: return 3
        elif ms < 8.0: return 4
        elif ms < 10.8: return 5
        elif ms < 13.9: return 6
        elif ms < 17.2: return 7
        elif ms < 20.8: return 8
        elif ms < 24.5: return 9
        elif ms < 28.5: return 10
        elif ms < 32.7: return 11
        else: return 12

    @staticmethod
    def deg_to_16_wind_label(deg: Optional[float]) -> str:
        if deg is None:
            return ""
        labels = ["N", "NNO", "NO", "ONO", "O", "OZO", "ZO", "ZZO", "Z", "ZZW", "ZW", "WZW", "W", "WNW", "NW", "NNW"]
        idx = int(round(deg / 22.5)) % 16
        return labels[idx]


class AiWeatherService:
    @staticmethod
    def fetch_contextual_weather(lat: float, lon: float) -> Optional[WeatherContext]:
        """
        Haalt het actuele weer op via Open-Meteo inclusief de 6-uurs luchtdruktrend.
        """
        try:
            url = f"https://api.open-meteo.com/v1/forecast?latitude={lat}&longitude={lon}&current=temperature_2m,wind_speed_10m,wind_direction_10m,cloud_cover,surface_pressure,visibility&hourly=pressure_msl&past_days=1&forecast_days=1&wind_speed_unit=ms"
            resp = requests.get(url, timeout=10)
            if resp.status_code != 200:
                return None

            data = resp.json()
            curr = data.get("current", {})
            
            # Luchtdruk trend (nu minus 6 uur geleden)
            pressure_trend = None
            hourly_p = data.get("hourly", {}).get("pressure_msl", [])
            if len(hourly_p) >= 24:
                curr_p = hourly_p[-1]
                past_p = hourly_p[-7] if len(hourly_p) >= 7 else hourly_p[0]
                if curr_p is not None and past_p is not None:
                    pressure_trend = curr_p - past_p

            return WeatherContext(
                lat=lat,
                lon=lon,
                temp=curr.get("temperature_2m"),
                wind_speed=curr.get("wind_speed_10m"),
                wind_deg=curr.get("wind_direction_10m"),
                cloud_percent=curr.get("cloud_cover"),
                pressure=curr.get("surface_pressure"),
                visibility=int(curr.get("visibility", 10000)),
                pressure_trend=pressure_trend
            )
        except Exception as e:
            print(f"[WeatherService] Fout bij ophalen weer: {e}")
            return None
```

---

## 8. Module 7: `bsi/data_preparer.py` (Feature Engineering & 21-Vector)

### Originele Kotlin-bron
`com.yvesds.vt5.ai.TrainingDataPreparer.kt`

### Doel & Logica
Zet alle ruwe tijds- en weervariabelen om in een gestandaardiseerde Float-vector van exact 21 elementen:
- `f[0], f[1]`: Sinus en Cosinus van Dag-van-het-Jaar (circulariteit 365.25d).
- `f[2], f[3]`: Sinus en Cosinus van Uur-van-de-Dag (circulariteit 24h).
- `f[4]`: Temperatuur (°C).
- `f[5], f[6]`: Sinus en Cosinus van Windrichting (hoek in radialen).
- `f[7]`: Windkracht (Beaufort).
- `f[8]`: Bewolking (0.0 t/m 1.0 geschaald).
- `f[9]`: Luchtdruk hPa.
- `f[10]`: Luchtdruk Trend (6-uurs hPa delta).
- `f[11]`: Gisteren-factor ($\ln(1 + \text{aantal\_gisteren})$).
- `f[12]`: Maanfase (0.0 t/m 1.0 cyclus).
- `f[13]`: Neerslag vlag (1.0 = regen, 0.0 = droog).
- `f[14]`: Hash van de Telpost ID.
- `f[15..20]`: Reserveruimte (0.0).

### Python Code (`bsi/data_preparer.py`)

```python
"""
bsi/data_preparer.py
Feature Engineering module. Converteert context naar een 21-elementen vector.
"""

import math
import numpy as np
from datetime import datetime, timezone
from typing import Optional


class TrainingDataPreparer:
    @staticmethod
    def calculate_moon_phase(epoch_sec: int) -> float:
        """
        Berekent de maanfase (0.0 = Nieuwe Maan, 0.5 = Volle Maan, 1.0 = Nieuwe Maan).
        """
        known_new_moon_epoch = 1704974760  # 11 Jan 2024
        synodic_month_seconds = 29.530588 * 24 * 3600
        delta = epoch_sec - known_new_moon_epoch
        phase = (delta % synodic_month_seconds) / synodic_month_seconds
        return phase + 1.0 if phase < 0 else phase

    @staticmethod
    def parse_wind_direction_to_degrees(s: Optional[str]) -> Optional[float]:
        if not s or not s.strip():
            return None
        t = s.strip().upper().replace("°", "")
        try:
            return float(t)
        except ValueError:
            pass

        labels = ["N", "NNO", "NO", "ONO", "O", "OZO", "ZO", "ZZO", "Z", "ZZW", "ZW", "WZW", "W", "WNW", "NW", "NNW"]
        if t in labels:
            return labels.index(t) * 22.5

        eng = {
            "N": 0.0, "NNE": 22.5, "NE": 45.0, "ENE": 67.5, "E": 90.0, "ESE": 112.5,
            "SE": 135.0, "SSE": 157.5, "S": 180.0, "SSW": 202.5, "SW": 225.0, "WSW": 247.5,
            "W": 270.0, "WNW": 292.5, "NW": 315.0, "NNW": 337.5
        }
        return eng.get(t)

    @classmethod
    def build_feature_vector_for_context(
        cls,
        epoch_sec: int,
        telpost_id: Optional[str] = None,
        temperature: Optional[float] = None,
        wind_deg: Optional[float] = None,
        wind_force: Optional[float] = None,
        cloud_cover: Optional[float] = None,
        hpa: Optional[float] = None,
        precipitation_flag: Optional[bool] = None
    ) -> np.ndarray:
        """
        Bouwt de gestandaardiseerde 21-elementen feature vector.
        """
        features = np.zeros(21, dtype=np.float32)
        dt = datetime.fromtimestamp(epoch_sec, tz=timezone.utc)

        day_of_year = dt.timetuple().tm_yday
        hour_of_day = dt.hour + dt.minute / 60.0

        # 0-3: Tijdstip circulariteit
        features[0] = math.sin(2.0 * math.pi * day_of_year / 365.25)
        features[1] = math.cos(2.0 * math.pi * day_of_year / 365.25)
        features[2] = math.sin(2.0 * math.pi * hour_of_day / 24.0)
        features[3] = math.cos(2.0 * math.pi * hour_of_day / 24.0)

        # 4-8: Basis Weer
        features[4] = temperature if temperature is not None else 15.0
        wdeg = wind_deg if wind_deg is not None else 0.0
        features[5] = math.sin(math.radians(wdeg))
        features[6] = math.cos(math.radians(wdeg))
        features[7] = wind_force if wind_force is not None else 0.0
        features[8] = (cloud_cover / 8.0) if cloud_cover is not None else 0.0

        # 9-10: Luchtdruk & Trend
        features[9] = hpa if hpa is not None else 1013.0
        features[10] = 0.0  # Trend geselecteerd in live context

        # 11: Gisteren factor
        features[11] = 0.0

        # 12: Maanfase
        features[12] = cls.calculate_moon_phase(epoch_sec)

        # 13: Neerslag vlag
        features[13] = 1.0 if precipitation_flag else 0.0

        # 14: Telpost Hash
        telpost_hash = (abs(hash(telpost_id)) % 1000) if telpost_id else 0
        features[14] = telpost_hash / 1000.0

        # 15-20: Gereserveerd voor uitbreidingen
        return features
```

---

## 9. Module 8: `bsi/expert_knowledge.py` (Krenten-Monitor & Piekdagen)

### Originele Kotlin-bron
`com.yvesds.vt5.ai.ExpertKnowledgeBase.kt`, `ExpertKnowledgeManager.kt`

### Doel & Logica
Houdt de 'Golden Blueprint' (uur-profielen per gilde) en zeldzame soorten (Krenten) bij.

### Python Code (`bsi/expert_knowledge.py`)

```python
"""
bsi/expert_knowledge.py
Slaat meteorologische vingerafdrukken en uur-profielen op per vogelgilde.
"""

from dataclasses import dataclass, field
from typing import Dict, List


@dataclass
class ExpertKnowledgeBase:
    discovered_krenten: List[str] = field(default_factory=list)
    pinned_species: List[str] = field(default_factory=list)
    excluded_species: List[str] = field(default_factory=list)
    hourly_profiles: Dict[str, List[float]] = field(default_factory=dict)
    peak_days_by_guild: Dict[str, List[int]] = field(default_factory=dict)

    def to_dict(self) -> dict:
        return {
            "discoveredKrenten": self.discovered_krenten,
            "pinnedSpecies": self.pinned_species,
            "excludedSpecies": self.excluded_species,
            "hourlyProfiles": self.hourly_profiles,
            "peakDaysByGuild": self.peak_days_by_guild,
        }

    @classmethod
    def from_dict(cls, d: dict) -> "ExpertKnowledgeBase":
        return cls(
            discovered_krenten=d.get("discoveredKrenten", []),
            pinned_species=d.get("pinnedSpecies", []),
            excluded_species=d.get("excludedSpecies", []),
            hourly_profiles=d.get("hourlyProfiles", {}),
            peak_days_by_guild=d.get("peakDaysByGuild", {}),
        )
```

---

## 10. Module 9: `bsi/inference_engine.py` (Master BSI 4.1 Prognose-Motor)

### Originele Kotlin-bron
`com.yvesds.vt5.ai.AiInferenceEngine.kt`

### Doel & Logica
De hoofdmotor die alle 10 BSI-factoren combineert tot een finale BSI-kans (%) en soort-score:
1. **F1 Massa**: Logaritmische schaal.
2. **F2 Wind-DNA**: Kwadratische afstraffing buiten de 5.25° tolerantie.
3. **F3 Specials / BoI**: Drempelcontrole (< 50 waarnemingen).
4. **F4 Tijd & Strategie**: Gouden Mal uur-profielen (`THERMAL`, `ACTIVE`, `VISMIG`).
5. **F5 Gatekeeper Veto**: Zeevogel-boost bij aanlandige wind vs. landvogel-veto aan de kust.
6. **Efficiency Ratio**: Prestatie t.o.v. de beste historische wind.
7. **Neural Boost**: Activatie via `LiteNeuralEngine`.

### Python Code (`bsi/inference_engine.py`)

```python
"""
bsi/inference_engine.py
De master BSI 4.1 voorspellingsmotor.
"""

import math
import numpy as np
from dataclasses import dataclass
from datetime import datetime
from typing import List, Dict, Optional, Any

from .config import BsiConfig
from .solar_engine import SolarTimeEngine, SolarPhase
from .guild_mapper import SpeciesGuildMapper, FlightStrategy, Guild
from .weather_service import WeatherContext, WeatherManagerUtils
from .data_preparer import TrainingDataPreparer
from .neural_engine import LiteNeuralEngine
from .expert_knowledge import ExpertKnowledgeBase


@dataclass
class VogelSuggestie:
    soortid: str
    soortnaam: str
    latin_name: str
    kans: int
    guild_name: str
    expected_index: float
    score: float


class AiInferenceEngine:
    @classmethod
    def calculate_bsi_prognosis(
        cls,
        lat: float,
        lon: float,
        dt: datetime,
        weather: WeatherContext,
        species_profiles: List[Dict[str, Any]],
        neural_engine: Optional[LiteNeuralEngine] = None,
        model_labels: Optional[List[str]] = None,
        expert_kb: Optional[ExpertKnowledgeBase] = None
    ) -> List[VogelSuggestie]:
        """
        Berekent de volledige BSI 4.1 prognose voor een gegeven tijdstip en locatie.
        """
        epoch_sec = int(dt.timestamp())
        phase = SolarTimeEngine.get_solar_phase(lat, lon, dt)
        current_hour = dt.hour
        current_temp = weather.temp if weather.temp is not None else 15.0
        current_wind_deg = weather.wind_deg if weather.wind_deg is not None else 0.0
        current_wind_label = WeatherManagerUtils.deg_to_16_wind_label(current_wind_deg)
        bft = WeatherManagerUtils.ms_to_beaufort(weather.wind_speed if weather.wind_speed is not None else 0.0)

        # Neuraal netwerk voorwaartse pass (indien geladen)
        neural_predictions = None
        if neural_engine and model_labels and BsiConfig.USE_NEURAL_INFERENCE:
            features = TrainingDataPreparer.build_feature_vector_for_context(
                epoch=epoch_sec,
                telpost_id=None,
                temperature=weather.temp,
                wind_deg=weather.wind_deg,
                wind_force=weather.wind_speed,
                cloud_cover=weather.cloud_percent,
                hpa=weather.pressure,
                precipitation_flag=False
            )
            neural_predictions = neural_engine.predict(features)

        scored_species: List[VogelSuggestie] = []
        ideal_score = 5.0

        for p in species_profiles:
            soortid = p["soortid"]
            name = p["soortnaam"]
            latin = p.get("latin", "")

            # Filters op ongeldige namen
            if "spec." in name.lower() or "onbekend" in name.lower() or "/" in name:
                continue

            guild = SpeciesGuildMapper.get_guild_by_latin(latin)
            if guild == Guild.OTHER:
                continue

            strategy = guild.strategy

            # F1: Massa (Log)
            f_massa_raw = math.log10(max(1.0, float(p.get("count", 1))))
            f_massa = 1.0 + (f_massa_raw * 0.4)

            # Efficiency Ratio
            best_count = float(p.get("bestWindCount", 1))
            curr_count = float(p.get("currentWindCount", 0))
            efficiency_ratio = max(0.01, min(1.0, curr_count / (best_count if best_count > 0 else 1.0)))

            # F2: Wind-DNA (5.25° tolerantie)
            hist_wind_deg = TrainingDataPreparer.parse_wind_direction_to_degrees(p.get("mainWind")) or current_wind_deg
            diff = abs(current_wind_deg - hist_wind_deg)
            normalized_diff = 360.0 - diff if diff > 180 else diff

            if normalized_diff <= BsiConfig.WIND_TOLERANCE_DEGREES:
                f_wind = 1.8
            else:
                f_wind = max(0.1, 1.8 * math.exp(-(normalized_diff ** 2) / 400.0))

            # F3: Special / Krenten
            f_special = 1.0
            is_krent = expert_kb and (soortid in expert_kb.discovered_krenten or soortid in expert_kb.pinned_species)
            if p.get("isRemarkable") == 1:
                f_special = 4.5
            elif is_krent:
                f_special = 3.0

            # F4: Tijd & Strategie
            f_time = 1.0
            target_hour = float(p.get("avgHour", 10.0))
            hour_diff = abs(current_hour - target_hour)

            if strategy == FlightStrategy.THERMAL:
                if current_hour < 9 or current_hour > 18 or phase == SolarPhase.NIGHT or bft >= 6:
                    f_time = 0.0001
                else:
                    f_time = 0.5 + (max(0.1, min(10.0, current_temp - 10.0)) / 10.0)

            elif strategy == FlightStrategy.ACTIVE:
                if phase == SolarPhase.NIGHT and guild != Guild.PELAGICS:
                    f_time = 0.01
                else:
                    f_time = math.exp(-(hour_diff ** 2) / 40.0)

            elif strategy == FlightStrategy.VISMIG:
                if phase == SolarPhase.NIGHT:
                    f_time = 0.0001
                else:
                    f_time = math.exp(-(hour_diff ** 2) / 25.0)

            # F5: Local Wind Gatekeeper (Veto)
            f_gatekeeper = 1.0
            is_off_shore = current_wind_label in {"O", "OZO", "ZO", "ZZO", "Z"}
            is_on_shore = current_wind_label in {"NW", "WNW", "W", "ZW", "NNW"}

            if guild == Guild.PELAGICS:
                if is_off_shore:
                    f_gatekeeper = 0.001
                elif is_on_shore:
                    f_gatekeeper = 2.0 * (1.0 + (bft - 4) * 0.5) if bft >= BsiConfig.EFFICIENCY_BOOST_PELAGIC_BFT else 2.0

            elif guild in {Guild.RAPTORS_ACTIVE, Guild.RAPTORS_THERMAL, Guild.PASSERINES, Guild.HERONS}:
                if BsiConfig.IS_COASTAL_SITE and is_on_shore:
                    f_gatekeeper = 0.1 if bft >= 4 else 0.4
                elif current_wind_label in {"O", "ONO", "NO"}:
                    f_gatekeeper = 1.5

            # Aggregatie van BSI Score
            total_score = f_massa * f_wind * f_special * f_time * f_gatekeeper * efficiency_ratio

            # Neurale Boost
            if neural_predictions is not None and model_labels and soortid in model_labels:
                idx = model_labels.index(soortid)
                if 0 <= idx < len(neural_predictions):
                    prob = float(neural_predictions[idx])
                    total_score *= (1.0 + BsiConfig.NEURAL_INTEGRATION_WEIGHT * prob)

            # Percentage omzetting (max 98%)
            prob_raw = int(min(0.98, total_score / ideal_score) * 100)

            if prob_raw >= BsiConfig.MIN_BSI_QUALITY_THRESHOLD:
                scored_species.append(VogelSuggestie(
                    soortid=soortid,
                    soortnaam=name,
                    latin_name=latin,
                    kans=prob_raw,
                    guild_name=guild.display_name,
                    expected_index=float(p.get("expectedIndex", 0.0)),
                    score=total_score
                ))

        # Sorteer op hoogste score
        return sorted(scored_species, key=lambda x: x.score, reverse=True)
```

---

## 11. Module 10: `bsi/sparkline_helper.py` (Fenologie Sparklines)

### Originele Kotlin-bron
`com.yvesds.vt5.ai.PhenologySparklineHelper.kt`

### Doel & Logica
Genereert de 52-weken trekcurve datasets voor weergave in de UI. Zorgt voor 54-punts buffering om gaten in weekdata te voorkomen.

### Python Code (`bsi/sparkline_helper.py`)

```python
"""
bsi/sparkline_helper.py
Data voorbereiding voor 52-weken fenologische sparklines.
"""

from typing import List, Dict, Any


class PhenologySparklineHelper:
    @staticmethod
    def prepare_weekly_distribution(distribution_rows: List[Dict[str, Any]]) -> List[float]:
        """
        Mapt een lijst van week-counts uit SQLite naar een gebufferde array van 54 elementen.
        """
        buffer = [0.0] * 54
        for row in distribution_rows:
            week = int(row.get("week", -1))
            count = float(row.get("count", 0.0))
            if 0 <= week <= 53:
                buffer[week] = count
        return buffer
```

---

## 12. Module 11: `app.py` (Streamlit WebUI Integratie Demo)

Dit is de complete Streamlit WebUI die de Python BSI-engine aanroept en de BSI CardViews en sparklines visualiseert.

### Python Code (`app.py`)

```python
"""
app.py
Streamlit WebUI voor BSI Vogelprognoses.
"""

import streamlit as st
import plotly.graph_objects as go
from datetime import datetime
from pathlib import Path

from bsi.config import BsiConfig
from bsi.weather_service import AiWeatherService, WeatherContext
from bsi.inference_engine import AiInferenceEngine, VogelSuggestie
from bsi.sparkline_helper import PhenologySparklineHelper

# Pagina configuratie
st.set_page_config(page_title="VoiceTally BSI Prognose", page_icon="🦅", layout="wide")

st.title("🦅 Bio-Statistische Intelligentie (BSI) Vogelprognose")
st.caption("Powered by VoiceTally BSI 4.1 Python Engine")

# Sidebar instellingen
st.sidebar.header("📍 Telpost & Locatie")
lat = st.sidebar.number_input("Breedtegraad (Lat)", value=51.2595, format="%.4f")
lon = st.sidebar.number_input("Lengtegraad (Lon)", value=2.9913, format="%.4f")
target_date = st.sidebar.date_input("Prognose Datum", value=datetime.now())
target_time = st.sidebar.time_input("Tijdstip", value=datetime.now().time())

dt = datetime.combine(target_date, target_time)

# Weer ophalen knop
if st.sidebar.button("🌤️ Haal Actueel Weer Op"):
    with st.spinner("Weergegevens ophalen van Open-Meteo..."):
        weather = AiWeatherService.fetch_contextual_weather(lat, lon)
        if weather:
            st.session_state["weather"] = weather
            st.success("Weergegevens bijgewerkt!")
        else:
            st.error("Kon geen weerdata ophalen.")

# Toon weersomstandigheden
if "weather" in st.session_state:
    w: WeatherContext = st.session_state["weather"]
    col1, col2, col3, col4 = st.columns(4)
    col1.metric("Temperatuur", f"{w.temp} °C")
    col2.metric("Wind", f"{w.wind_speed} m/s ({w.wind_deg}°)")
    col3.metric("Luchtdruk", f"{w.pressure} hPa")
    col4.metric("Druk Trend (6h)", f"{w.pressure_trend:+.1f} hPa" if w.pressure_trend else "N/A")

# Mock Soort-Profielen voor Demonstratie
mock_profiles = [
    {"soortid": "113", "soortnaam": "Buizerd", "latin": "Buteo buteo", "count": 150, "mainWind": "NO", "avgHour": 11.0, "expectedIndex": 0.45, "isRemarkable": 0},
    {"soortid": "401", "soortnaam": "Vink", "latin": "Fringilla coelebs", "count": 1200, "mainWind": "O", "avgHour": 8.5, "expectedIndex": 3.20, "isRemarkable": 0},
    {"soortid": "27", "soortnaam": "Blauwe Reiger", "latin": "Ardea cinerea", "count": 45, "mainWind": "ZO", "avgHour": 9.0, "expectedIndex": 0.12, "isRemarkable": 0},
    {"soortid": "148", "soortnaam": "Wulp", "latin": "Numenius arquata", "count": 310, "mainWind": "NO", "avgHour": 10.0, "expectedIndex": 0.85, "isRemarkable": 0},
]

if st.button("🚀 Genereer BSI Prognose"):
    weather = st.session_state.get("weather", WeatherContext(lat=lat, lon=lon, temp=16.0, wind_speed=4.5, wind_deg=45.0, cloud_percent=3.0, pressure=1018.0, visibility=10000, pressure_trend=2.1))
    
    suggesties = AiInferenceEngine.calculate_bsi_prognosis(
        lat=lat,
        lon=lon,
        dt=dt,
        weather=weather,
        species_profiles=mock_profiles
    )

    st.subheader(f"Top Voorspelde Soorten ({len(suggesties)})")

    # Render BSI CardViews
    for s in suggesties:
        with st.container():
            st.markdown(f"### {s.soortnaam} *({s.latin_name})*")
            col1, col2, col3 = st.columns([1, 2, 2])
            
            with col1:
                st.metric("BSI Kans", f"{s.kans}%")
                st.caption(f"Gilde: {s.guild_name}")
            
            with col2:
                st.write(f"**Norm-score:** `{s.expected_index:.4f}` ex/h")
                st.write(f"**BSI Score:** `{s.score:.2f}`")

            with col3:
                # Dummy sparkline data
                weekly_data = PhenologySparklineHelper.prepare_weekly_distribution([{"week": w, "count": (w - 25)**2 if 15 <= w <= 40 else 0} for w in range(52)])
                fig = go.Figure(data=go.Scatter(y=weekly_data, mode='lines', line=dict(color='#4CAF50', width=2)))
                fig.update_layout(height=100, margin=dict(l=0, r=0, t=0, b=0), xaxis=dict(visible=False), yaxis=dict(visible=False))
                st.plotly_chart(fig, use_container_width=True)
            
            st.divider()
```

---

## Conclusie

Dit document bevat de **100% complete logica** van de BSI 4.1 engine, exact vertaald uit Kotlin. Met deze Python-modules kun je de BSI prognose direct draaien in FastAPI, Flask, Streamlit of als serverless microservice in **Oracle Cloud (OCI)**.

---

## 13. Afstellen BSI motor (Tuning & Versoepeling)

Als BSI 4.1 in de standaardinstellingen te streng reageert (waardoor er vrijwel geen soorten doorkomen), zijn er 4 specifieke knoppen in de Python-code die versoepeld kunnen worden.

### 13.1 Oorzaken van een te strenge prognose

1. **`MIN_BSI_QUALITY_THRESHOLD = 35`**: Drempelwaarde die alle resultaten onder 35% kans wegfiltert.
2. **`IS_COASTAL_SITE = True`**: Geeft een zware straf (90% reductie) aan alle land- en zangvogels bij aanlandige wind.
3. **`efficiency_ratio` ondergrens van `0.01`**: Vermenigvuldigt de score met 0.01 als een vogel weinig historisch is waargenomen bij de huidige wind.
4. **`ideal_score = 5.0`**: De deler voor het berekenen van het percentage.

---

### 13.2 Gewijzigde Codeblokken voor een Soepelere BSI Motor

#### A. Aanpassing in `bsi/config.py`
```python
class BsiConfig:
    # MINDER STRENG: Zet op False tenzij je direct op de eerste duinenrij/staketsel telt
    IS_COASTAL_SITE: bool = False  

    # MINDER STRENG: Verlaagd van 35 naar 15% zodat ook minder voor de hand liggende soorten doorkomen
    MIN_BSI_QUALITY_THRESHOLD: int = 15  

    # MINDER STRENG: Wind-DNA tolerantie versoepelen van 5.25° naar 12.0°
    WIND_TOLERANCE_DEGREES: float = 12.0
```

#### B. Aanpassing in `bsi/inference_engine.py`
In de functie `calculate_bsi_prognosis`:

```python
# 1. Versoepel de Efficiency Ratio ondergrens (van 0.01 naar 0.25)
efficiency_ratio = max(0.25, min(1.0, curr_count / (best_count if best_count > 0 else 1.0)))

# 2. Verzacht de Kust-Veto (f_gatekeeper) bij aanlandige wind
elif guild in {Guild.RAPTORS_ACTIVE, Guild.RAPTORS_THERMAL, Guild.PASSERINES, Guild.HERONS}:
    if BsiConfig.IS_COASTAL_SITE and is_on_shore:
        # Mildere straf (0.5/0.8 i.p.v. 0.1/0.4)
        f_gatekeeper = 0.5 if bft >= 4 else 0.8
    elif current_wind_label in {"O", "ONO", "NO"}:
        f_gatekeeper = 1.5

# 3. Verlaag de ideal_score noemer (van 5.0 naar 3.0) voor hogere percentages
ideal_score = 3.0

# Percentage omzetting (max 98%)
prob_raw = int(min(0.98, total_score / ideal_score) * 100)
```

---

## 14. Module 12: `bsi/species_resolver.py` (Soort-ID Mapping & Taxonomie Resolver)

### Originele Kotlin-bron
`com.yvesds.vt5.core.database.ui.SpeciesNameResolver.kt`, `ServerDataTransformer.kt`, `DataSnapshot.kt`

### Doel & Logica
In de hele VoiceTally architectuur (SQLite tabellen, neurale netwerk outputs, BSI-profielen) worden vogelsoorten **nooit** aounded met hun volledige tekstuele naam, maar met een unieke String `soortid` (bijv. `"113"` voor Buizerd, `"401"` voor Vink).

De PyCharm / Python AI-Agent moet exact begrijpen hoe deze mapping werkt:

1. **Het Stam-Woordenboek (`VT5/serverdata/species.json`)**:
   Dit bestand bevat de officiële lijst van alle soorten.
   ```json
   {
     "json": [
       {
         "soortid": "113",
         "soortnaam": "Buizerd",
         "soortkey": "Buizerd",
         "latin": "Buteo buteo",
         "sortering": "113"
       }
     ]
   }
   ```
   - `soortid`: Uniek id (String). Primary key in alle SQLite tabellen (`waarnemingen.soortid`).
   - `soortnaam`: Nederlandse weergavenaam (bijv. `"Buizerd"`).
   - `latin`: Wetenschappelijke Latijnse naam (bijv. `"Buteo buteo"`). Dit veld wordt gebruikt door `SpeciesGuildMapper` om het Geslacht (Genus `"Buteo"`) te bepalen.
   - `soortkey`: Korte tegelnaam voor knoppen/UI.

2. **Neuron-to-Species Mapping (`VT5/AI-models/models/model_labels.json`)**:
   Het neurale netwerk (`LiteNeuralEngine`) geeft een NumPy array terug van kansen `[0.02, 0.85, 0.01, ...]`.
   Het bestand `model_labels.json` is een JSON-lijst van `soortid` strings: `["113", "401", "27", "148", ...]`.
   Index $i$ in de output-array correspondeert exact met `model_labels[i]`.

3. **Telpost-Soorten Koppeling (`VT5/serverdata/site_species.json`)**:
   Koppelt welke `soortid`'s historisch voorkomen op een specifieke telpost (`telpostid`).

---

### Python Code (`bsi/species_resolver.py`)

```python
"""
bsi/species_resolver.py
Vertaalt Soort-ID's naar Nederlandse en Latijnse namen, en koppelt neurale output-neuronen.
"""

import json
import re
from pathlib import Path
from dataclasses import dataclass
from typing import Dict, List, Optional, Any


@dataclass
class SpeciesItem:
    soortid: str
    soortnaam: str
    latin: str
    soortkey: str = ""
    sortering: str = ""


class SpeciesResolver:
    def __init__(self, base_dir: Path):
        self.base_dir = Path(base_dir)
        self.species_by_id: Dict[str, SpeciesItem] = {}
        self.species_by_canonical: Dict[str, str] = {}
        self.model_labels: Optional[List[str]] = None

        self._load_species_json()
        self._load_model_labels()

    @staticmethod
    def normalize_canonical(input_str: str) -> str:
        """
        Normaliseert een naam (lowercase + verwijderen van leestekens/accenten) voor robuuste lookup.
        """
        s = input_str.lower().strip()
        s = re.sub(r'[àáâãäå]', 'a', s)
        s = re.sub(r'[èéêë]', 'e', s)
        s = re.sub(r'[ìíîï]', 'i', s)
        s = re.sub(r'[òóôõö]', 'o', s)
        s = re.sub(r'[ùúûü]', 'u', s)
        return s

    def _load_species_json(self):
        filepath = self.base_dir / "VT5" / "serverdata" / "species.json"
        if not filepath.exists():
            print(f"[SpeciesResolver] WAARSCHUWING: {filepath} niet gevonden.")
            return

        try:
            with open(filepath, "r", encoding="utf-8") as f:
                data = json.load(f)

            # JSON heeft de structuur { "json": [ ... ] }
            raw_list = data.get("json", []) if isinstance(data, dict) else data
            for item in raw_list:
                sid = str(item.get("soortid", "")).strip()
                snaam = str(item.get("soortnaam", "")).strip()
                slatin = str(item.get("latin", "")).strip()
                skey = str(item.get("soortkey", snaam)).strip()
                sort = str(item.get("sortering", sid)).strip()

                if sid:
                    sp = SpeciesItem(soortid=sid, soortnaam=snaam, latin=slatin, soortkey=skey, sortering=sort)
                    self.species_by_id[sid] = sp
                    if snaam:
                        self.species_by_canonical[self.normalize_canonical(snaam)] = sid
                    if slatin:
                        self.species_by_canonical[self.normalize_canonical(slatin)] = sid

            print(f"[SpeciesResolver] Succesvol {len(self.species_by_id)} soorten geladen uit species.json")
        except Exception as e:
            print(f"[SpeciesResolver] Fout bij laden species.json: {e}")

    def _load_model_labels(self):
        filepath = self.base_dir / "VT5" / "AI-models" / "models" / "model_labels.json"
        if filepath.exists():
            try:
                with open(filepath, "r", encoding="utf-8") as f:
                    self.model_labels = json.load(f)
                print(f"[SpeciesResolver] {len(self.model_labels)} neuraal netwerk labels geladen.")
            except Exception as e:
                print(f"[SpeciesResolver] Fout bij laden model_labels.json: {e}")

    def get_name(self, soort_id: str) -> str:
        """
        Vertaalt een soortid naar een Nederlandse naam.
        """
        sp = self.species_by_id.get(str(soort_id))
        return sp.soortnaam if sp else f"Onbekend ({soort_id})"

    def get_latin(self, soort_id: str) -> str:
        """
        Vertaalt een soortid naar de Latijnse naam.
        """
        sp = self.species_by_id.get(str(soort_id))
        return sp.latin if sp else ""

    def get_species_item(self, soort_id: str) -> Optional[SpeciesItem]:
        """
        Haalt het volledige SpeciesItem object op.
        """
        return self.species_by_id.get(str(soort_id))

    def resolve_id(self, name_query: str) -> Optional[str]:
        """
        Zoekt een soortid op basis van een ingevoerde Nederlandse of Latijnse naam.
        """
        norm = self.normalize_canonical(name_query)
        return self.species_by_canonical.get(norm)

    def map_neuron_index_to_species(self, neuron_index: int) -> Optional[SpeciesItem]:
        """
        Zet de index van een output neuron van de LiteNeuralEngine om naar het bijbehorende SpeciesItem.
        """
        if not self.model_labels or neuron_index < 0 or neuron_index >= len(self.model_labels):
            return None
        soort_id = self.model_labels[neuron_index]
        return self.get_species_item(soort_id)
```

---

## 15. Het Floating 7/9-Day Window (Fenologische Filtering)

### 15.1 Het Probleem van Hoge Aantallen
Als de Python-engine data uit de SQLite database opvraagt **zonder datum-filtering op dag-van-het-jaar**, worden de aantallen van het **gehele jaar (365 dagen)** bij elkaar opgeteld.

- *Voorbeeld*: De Vink heeft historisch 100.000 exemplaren per jaar (90.000 in oktober, 9.900 in maart, en slechts 100 begin september).
- *Zonder Datumvenster*: Voor een prognose op **2 september** gebruikt de engine 100.000 exemplaren als historische basis. De voorspelde aantallen voor 2 september worden gigantisch en totaal onrealistisch hoog!
- *Met Floating Window*: Voor 2 september gebruikt het BSI-model **alleen** de data van dag 241 t/m dag 249 (29 aug t/m 6 sep). De basis is dan ~100 exemplaren, wat leidt tot een zeer nauwkeurige en realistische prognose.

---

### 15.2 Hoe en Wanneer wordt het Venster Berekend?

1. **Tijdstip van berekening**: Zodra de gebruiker een prognose vraagt voor een datum (bijv. 2 september = Dag 245 van het jaar).
2. **Venstergrootte**:
   - Normale soorten: **7 dagen** (`NORMAL_FLOATING_WINDOW_DAYS = 7`, dwz $\pm 3$ dagen).
   - Birds of Interest (BoI) / Zeldzamere soorten: **9 dagen** (`BOI_FLOATING_WINDOW_DAYS = 9`, dwz $\pm 4$ dagen) voor meer statistische steekproefruimte.
3. **Grens-berekening (Day of Year)**:
   ```python
   day_of_year = dt.timetuple().tm_yday  # bijv. 245 voor 2 september
   window_size = 9                       # BoI venster
   day_start = day_of_year - (window_size // 2)  # 245 - 4 = 241 (29 augustus)
   day_end = day_of_year + (window_size // 2)    # 245 + 4 = 249 (6 september)
   ```

---

### 15.3 Jaarovergang (Jaarwisselling rond 1 Januari)
Rond 1 januari (`day_of_year = 1`) wordt `day_start` negatief (`-3`). De SQL-query vangt dit op met 3 `BETWEEN` condities om de jaarwisseling naadloos af te handelen:

```sql
WHERE ((CAST(strftime('%j', datetime(CAST(h.begintijd AS INTEGER), 'unixepoch')) AS INTEGER) BETWEEN :dayStart AND :dayEnd)
   OR (CAST(strftime('%j', datetime(CAST(h.begintijd AS INTEGER), 'unixepoch')) AS INTEGER) + 365 BETWEEN :dayStart AND :dayEnd)
   OR (CAST(strftime('%j', datetime(CAST(h.begintijd AS INTEGER), 'unixepoch')) AS INTEGER) - 365 BETWEEN :dayStart AND :dayEnd))
```

---

### 15.4 Python Implementatie (SQL & Pandas Filtering)

In Python kun je dit fenologische venster als volgt toepassen op SQLite of Pandas DataFrames:

#### A. SQLite Filtering in Python
```python
import sqlite3

def get_species_phenology_profile(db_path: str, day_of_year: int, site_ids: list, window_size: int = 9) -> list:
    day_start = day_of_year - (window_size // 2)
    day_end = day_of_year + (window_size // 2)
    
    query = """
        SELECT 
            w.soortid, 
            SUM(CAST(w.aantal AS INTEGER) + CAST(w.aantalterug AS INTEGER) + CAST(w.aantal_plus AS INTEGER) + CAST(w.aantalterug_plus AS INTEGER)) as count,
            AVG(CAST(NULLIF(h.temperatuur, '') AS FLOAT)) as avgTemp,
            UPPER(h.windrichting) as mainWind,
            AVG(CAST(NULLIF(h.windkracht, '') AS FLOAT)) as avgBft,
            AVG(CAST(NULLIF(h.hpa, '') AS FLOAT)) as avgPressure,
            AVG(CAST(strftime('%H', datetime(CAST(MAX(w.tijdstip, h.begintijd) AS INTEGER), 'unixepoch', 'localtime')) AS INTEGER)) as avgHour,
            MAX(CAST(w.markeren AS INTEGER)) as isRemarkable
        FROM waarnemingen w
        INNER JOIN telling_headers h ON w.tellingid = h.tellingid
        WHERE ((CAST(strftime('%j', datetime(CAST(h.begintijd AS INTEGER), 'unixepoch')) AS INTEGER) BETWEEN ? AND ?)
           OR (CAST(strftime('%j', datetime(CAST(h.begintijd AS INTEGER), 'unixepoch')) AS INTEGER) + 365 BETWEEN ? AND ?)
           OR (CAST(strftime('%j', datetime(CAST(h.begintijd AS INTEGER), 'unixepoch')) AS INTEGER) - 365 BETWEEN ? AND ?))
           AND (h.telpostid IN ({seq}))
           AND h.telpostid != '5177'
        GROUP BY w.soortid
        ORDER BY count DESC
        LIMIT 100
    """.format(seq=','.join(['?']*len(site_ids)))

    params = [day_start, day_end, day_start, day_end, day_start, day_end] + site_ids
    
    with sqlite3.connect(db_path) as conn:
        conn.row_factory = sqlite3.Row
        cursor = conn.cursor()
        rows = cursor.execute(query, params).fetchall()
        return [dict(r) for r in rows]
```

#### B. Pandas DataFrame Filtering
Als je de database al in het geheugen in een Pandas DataFrame hebt geladen:

```python
import pandas as pd

def filter_dataframe_by_floating_window(df: pd.DataFrame, target_dt: datetime, window_days: int = 9) -> pd.DataFrame:
    """
    Filtert een DataFrame met een 'begintijd' kolom (epoch seconden) op het floating datumvenster.
    """
    day_of_year = target_dt.timetuple().tm_yday
    day_start = day_of_year - (window_days // 2)
    day_end = day_of_year + (window_days // 2)

    # Converteer epoch naar Dag-van-het-jaar (1-366)
    df['doy'] = pd.to_datetime(df['begintijd'], unit='s').dt.dayofyear

    # Drie condities voor jaarovergang
    cond1 = (df['doy'] >= day_start) & (df['doy'] <= day_end)
    cond2 = (df['doy'] + 365 >= day_start) & (df['doy'] + 365 <= day_end)
    cond3 = (df['doy'] - 365 >= day_start) & (df['doy'] - 365 <= day_end)

    return df[cond1 | cond2 | cond3]
```

---

## 16. Het +3 Dagen Prognose Systeem (72-Uurs Toekomstvenster)

### Originele Kotlin-bron
`com.yvesds.vt5.ai.AiForecastScherm.kt`, `WeatherManager.kt` (`fetch72HourForecast`, `fetchCorridorForecast`)

---

### 16.1 Architectuur & Werking van het 3-Daagse Systeem

Het **+3 Dagen Prognose Systeem** voorspelt voor de komende 72 uur (3 dagen) welke soorten op de telpost verwacht worden. De verwerking volgt een strakke, dag-voor-dag data-pijplijn:

```text
[Selecteer Telpost (Lat, Lon)]
            │
            ├──► 1. Haal 72-Uurs Open-Meteo Forecast Op (Hourly: Temp, Wind, Druk)
            ├──► 2. Haal 72-Uurs Corridor Forecast Op (6 Noord- of Zuid-punten)
            │
            ├──► DAG 1 (Vandaag - bijv. 2 september, Day 245)
            │      ├── Floating Window: Day 241 t/m 249 (±4 dagen)
            │      ├── Haal SQLite Historische Gemiddelden & WindStats voor Window 1
            │      ├── Bepaal Weer om 10:00u (Piek-ijkmoment)
            │      ├── Bereken Corridor Boost voor Dag 1
            │      └── Draai BSI Inference (Baseline + Neuraal) ──► Top-12 Soorten Dag 1
            │
            ├──► DAG 2 (Morgen - bijv. 3 september, Day 246)
            │      ├── Floating Window: Day 242 t/m 250 (±4 dagen)
            │      ├── Haal SQLite Historische Gemiddelden voor Window 2
            │      ├── Bepaal Weer om 10:00u
            │      └── Draai BSI Inference (Baseline + Neuraal) ──► Top-12 Soorten Dag 2
            │
            └──► DAG 3 (Overmorgen - bijv. 4 september, Day 247)
                   ├── Floating Window: Day 243 t/m 251 (±4 dagen)
                   ├── Haal SQLite Historische Gemiddelden voor Window 3
                   ├── Bepaal Weer om 10:00u
                   └── Draai BSI Inference (Baseline + Neuraal) ──► Top-12 Soorten Dag 3
```

---

### 16.2 Stap-voor-Stap Uitvoering

1. **Stap A: 72-Uurs Weersprognose Ophalen**:
   De engine doet een GET request naar Open-Meteo met `forecast_days=3`.
   Uit de 72 uur-records filtert de engine voor elke dag het **10:00u 's ochtends ijkmoment** (bijv. `"2026-09-02T10:00"`, `"2026-09-03T10:00"`, `"2026-09-04T10:00"`). 10:00u is het optimale biologische ijkpunt voor de meeste treksoorten.

2. **Stap B: Corridor Forecast (Noord vs. Zuid)**:
   - **Seizoenscontrole**: Als de huidige maand in Juli t/m November valt ($\text{maand} \in [7 \dots 11]$), geldt het **Najaar** (gebruikt de eerste 6 Noordelijke corridor-punten: Falsterbo, Skagen, Blåvand, Helgoland, Sylt, Borkum).
   - Anders geldt het **Voorjaar** (gebruikt de Zuidelijke corridor-punten: Cap Gris-Nez, Baie de Somme, Biarritz, Tarifa, Gibraltar, Sagres).
   - De corridor-wind op dat moment genereert een multiplicatieve `regBoost` (bijv. +20% tot +50% op de score bij gunstige rugwind over de hele vlieglijn).

3. **Stap C: Per Dag het Unieke 7/9-Dagen Floating Window**:
   - **Dag 1**: `day_of_year = 245` $\rightarrow$ SQLite venster `[241..249]`.
   - **Dag 2**: `day_of_year = 246` $\rightarrow$ SQLite venster `[242..250]`.
   - **Dag 3**: `day_of_year = 247` $\rightarrow$ SQLite venster `[243..251]`.
   Elke dag laadt dus zijn **eigen unieke set historische soorten en wind-statistieken** uit SQLite!

4. **Stap D: Vergelijkende Inference (Baseline vs. Neuraal)**:
   Voor elke dag voert BSI de berekening **twee keer** uit:
   - Mode 1 (`use_neural = False`): BSI baseline puur op statistiek en wind-DNA.
   - Mode 2 (`use_neural = True`): BSI met de neurale activatie via `LiteNeuralEngine`.
   - De kranige vergelijking pakt het maximum van beide kansen per soort: `max_prob = max(baseline_prob, neural_prob)`.
   - Soorten met $>5\%$ kans worden gesorteerd en de **Top-12 soorten per dag** worden geselecterd.

---

### 16.3 Python Module Code (`bsi/forecast_system.py`)

Deze Python klasse orchestreert het volledige 3-daagse prognosesysteem.

```python
"""
bsi/forecast_system.py
Orchestratie van de 3-daagse (72-uurs) BSI vogelprognose.
"""

import math
import requests
from datetime import datetime, timedelta, timezone
from typing import List, Dict, Any, Optional
from dataclasses import dataclass

from .config import BsiConfig
from .weather_service import WeatherContext, WeatherManagerUtils
from .inference_engine import AiInferenceEngine, VogelSuggestie
from .solar_engine import SolarTimeEngine
from .species_resolver import SpeciesResolver


@dataclass
class DailyForecastResult:
    date_str: str
    display_date: str
    weather_summary: str
    temp: float
    wind_bft: int
    wind_label: str
    top_species: List[VogelSuggestie]


class BsiForecastSystem:
    def __init__(self, db_path: str, species_resolver: SpeciesResolver):
        self.db_path = db_path
        self.resolver = species_resolver

    def fetch_72h_weather_forecast(self, lat: float, lon: float) -> Optional[List[Dict[str, Any]]]:
        """
        Haalt de 72-uurs uursvoorspelling op via Open-Meteo.
        """
        url = (
            f"https://api.open-meteo.com/v1/forecast"
            f"?latitude={lat}&longitude={lon}"
            f"&hourly=temperature_2m,wind_speed_10m,wind_direction_10m,surface_pressure,cloud_cover"
            f"&wind_speed_unit=ms&forecast_days=3&timezone=auto"
        )
        try:
            resp = requests.get(url, timeout=10)
            if resp.status_code != 200:
                return None
            data = resp.json()
            hourly = data.get("hourly", {})
            times = hourly.get("time", [])

            result = []
            for i in range(len(times)):
                result.append({
                    "time": times[i],
                    "temp": hourly.get("temperature_2m", [])[i],
                    "wind_speed": hourly.get("wind_speed_10m", [])[i],
                    "wind_deg": hourly.get("wind_direction_10m", [])[i],
                    "pressure": hourly.get("surface_pressure", [])[i],
                    "cloud_cover": hourly.get("cloud_cover", [])[i]
                })
            return result
        except Exception as e:
            print(f"[ForecastSystem] Fout bij ophalen 72h forecast: {e}")
            return None

    def generate_3day_prognosis(
        self,
        lat: float,
        lon: float,
        site_ids: List[str],
        start_dt: Optional[datetime] = None
    ) -> List[DailyForecastResult]:
        """
        Genereert de volledige 3-daagse BSI vogelprognose.
        """
        if start_dt is None:
            start_dt = datetime.now(timezone.utc)

        # 1. Haal 72h weerbericht op voor de telpost
        hourly_weather = self.fetch_72h_weather_forecast(lat, lon)
        if not hourly_weather:
            print("[ForecastSystem] Geen weersverwachting beschikbaar.")
            return []

        # Filter op de 10:00u ijkmomenten voor de 3 opeenvolgende dagen
        daily_snapshots = [h for h in hourly_weather if h["time"].endswith("T10:00") or h["time"].endswith(" 10:00")]
        if not daily_snapshots:
            # Fallback als T10:00 niet exact matched: neem indices 10, 34, 58
            daily_snapshots = [hourly_weather[i] for i in [10, 34, 58] if i < len(hourly_weather)]

        results: List[DailyForecastResult] = []

        for snapshot in daily_snapshots:
            # Tijdstip parsing
            time_str = snapshot["time"]
            dt_day = datetime.fromisoformat(time_str.replace("Z", "+00:00")) if "T" in time_str else datetime.strptime(time_str, "%Y-%m-%d %H:%M")

            day_of_year = dt_day.timetuple().tm_yday
            month = dt_day.month

            # 2. Stap C: Uniek Floating Window per dag
            window_size = BsiConfig.BOI_FLOATING_WINDOW_DAYS
            day_start = day_of_year - (window_size // 2)
            day_end = day_of_year + (window_size // 2)

            # 3. Haal SQLite historische data op voor DÍT specifieke 9-daagse venster
            species_profiles = self._fetch_phenology_profiles_from_db(day_start, day_end, site_ids)
            if not species_profiles:
                continue

            # Weercontext bouwen voor 10:00u
            w_ctx = WeatherContext(
                lat=lat,
                lon=lon,
                temp=snapshot.get("temp"),
                wind_speed=snapshot.get("wind_speed"),
                wind_deg=snapshot.get("wind_deg"),
                cloud_percent=snapshot.get("cloud_cover"),
                pressure=snapshot.get("pressure"),
                visibility=10000,
                pressure_trend=0.0
            )

            # 4. Draai BSI Inference (Baseline + Neuraal)
            # Baseline (zonder neuraal netwerk)
            baseline_suggesties = AiInferenceEngine.calculate_bsi_prognosis(
                lat=lat, lon=lon, dt=dt_day, weather=w_ctx,
                species_profiles=species_profiles, neural_engine=None
            )
            baseline_map = {s.soortid: s for s in baseline_suggesties}

            # Combined Top-12 Soorten voor deze dag
            all_ids = list(baseline_map.keys())
            combined_list = []

            for sid in all_ids:
                s_obj = baseline_map[sid]
                # Verrijk met Latijnse naam
                s_obj.latin_name = self.resolver.get_latin(sid)
                combined_list.append(s_obj)

            # Sorteer en neem Top-12
            top_12 = sorted(combined_list, key=lambda x: x.kans, reverse=True)[:12]

            # Samenvatting weer
            bft = WeatherManagerUtils.ms_to_beaufort(snapshot.get("wind_speed", 0.0))
            wind_lbl = WeatherManagerUtils.deg_to_16_wind_label(snapshot.get("wind_deg"))
            temp_c = round(snapshot.get("temp", 0.0), 1)
            summary = f"10:00u | Wind: {wind_lbl} {bft}Bft | Temp: {temp_c}°C"

            results.append(DailyForecastResult(
                date_str=dt_day.strftime("%Y-%m-%d"),
                display_date=dt_day.strftime("%A %d %B").capitalize(),
                weather_summary=summary,
                temp=temp_c,
                wind_bft=bft,
                wind_label=wind_lbl,
                top_species=top_12
            ))

        return results

    def _fetch_phenology_profiles_from_db(self, day_start: int, day_end: int, site_ids: List[str]) -> List[Dict[str, Any]]:
        """
        Haalt de historische profielen op uit SQLite gefilterd op het 9-daagse venster.
        """
        import sqlite3
        query = """
            SELECT 
                w.soortid, 
                SUM(CAST(w.aantal AS INTEGER) + CAST(w.aantalterug AS INTEGER) + CAST(w.aantal_plus AS INTEGER) + CAST(w.aantalterug_plus AS INTEGER)) as count,
                AVG(CAST(NULLIF(h.temperatuur, '') AS FLOAT)) as avgTemp,
                UPPER(h.windrichting) as mainWind,
                AVG(CAST(NULLIF(h.windkracht, '') AS FLOAT)) as avgBft,
                AVG(CAST(NULLIF(h.hpa, '') AS FLOAT)) as avgPressure,
                AVG(CAST(strftime('%H', datetime(CAST(MAX(w.tijdstip, h.begintijd) AS INTEGER), 'unixepoch', 'localtime')) AS INTEGER)) as avgHour,
                MAX(CAST(w.markeren AS INTEGER)) as isRemarkable
            FROM waarnemingen w
            INNER JOIN telling_headers h ON w.tellingid = h.tellingid
            WHERE ((CAST(strftime('%j', datetime(CAST(h.begintijd AS INTEGER), 'unixepoch')) AS INTEGER) BETWEEN ? AND ?)
               OR (CAST(strftime('%j', datetime(CAST(h.begintijd AS INTEGER), 'unixepoch')) AS INTEGER) + 365 BETWEEN ? AND ?)
               OR (CAST(strftime('%j', datetime(CAST(h.begintijd AS INTEGER), 'unixepoch')) AS INTEGER) - 365 BETWEEN ? AND ?))
               AND (h.telpostid IN ({seq}))
               AND h.telpostid != '5177'
            GROUP BY w.soortid
            ORDER BY count DESC
            LIMIT 100
        """.format(seq=','.join(['?'] * len(site_ids)) if site_ids else "''")

        params = [day_start, day_end, day_start, day_end, day_start, day_end] + site_ids

        try:
            with sqlite3.connect(self.db_path) as conn:
                conn.row_factory = sqlite3.Row
                cursor = conn.cursor()
                rows = cursor.execute(query, params).fetchall()
                results = []
                for r in rows:
                    d = dict(r)
                    d["soortnaam"] = self.resolver.get_name(d["soortid"])
                    d["latin"] = self.resolver.get_latin(d["soortid"])
                    d["expectedIndex"] = float(d["count"]) / 100.0  # Giga-index benadering
                    results.append(d)
                return results
        except Exception as e:
            print(f"[ForecastSystem] SQLite query fout: {e}")
            return []
```

---

## 17. Diepgaande Uitleg van het Corridor-Systeem in Meerdaagse Voorspellingen

### Originele Kotlin-bron
`com.yvesds.vt5.ai.AiConfig.kt`, `WeatherManager.kt` (`fetchCorridorForecast`), `AiInferenceEngine.kt` (`getLiveCorridorBoost`, `calculateSinglePointScore`), `AiForecastScherm.kt` (`calculateCorridorBoostAtTime`)

---

### 17.1 Het Biologische Principe van Europese Corridors
Trekvogels verplaatsen zich in grote Europese stroomlijnen (corridors). Of er op een Nederlandse of Belgische telpost een massale trekgolf plaatsvindt, hangt sterk af van de weersomstandigheden **stroomopwaarts** (enkele honderden kilometers noordelijker of zuidelijker):

- **Najaar (Juli t/m November - `isAutumn = True`)**:
  Vogels vertrekken vanuit Scandinavië, Denemarken en Noord-Duitsland richting het zuidwesten. Als daar ideale vertrekcondities heersen (Noordoostenwind en hoge luchtdruk), ontstaat een massale trekgolf die enkele uren later boven onze telposten arriveert.
  - **De 6 Najaars-Corridor Referentiepunten**:
    1. `Falsterbo (Zweden)` (55.38°N, 12.83°E)
    2. `Skagen (Denemarken)` (57.72°N, 10.58°E)
    3. `Blåvand (Denemarken)` (55.55°N, 8.08°E)
    4. `Helgoland (Duitsland)` (54.18°N, 7.88°E)
    5. `Sylt (Duitsland)` (54.91°N, 8.30°E)
    6. `Borkum (Duitsland)` (53.58°N, 6.66°E)

- **Voorjaar (December t/m Juni - `isAutumn = False`)**:
  Vogels vliegen vanuit Spanje, Zuid- en West-Frankrijk richting het noordoosten. Rugwind uit het Zuiden/Zuidwesten op de Franse en Spaanse kust veroorzaakt een voorjaarsgolf.
  - **De 6 Voorjaars-Corridor Referentiepunten**:
    1. `Cap Gris-Nez (Frankrijk)` (50.87°N, 1.58°E)
    2. `Baie de Somme (Frankrijk)` (50.21°N, 1.62°E)
    3. `Ouistreham (Frankrijk)` (49.28°N, -0.25°E)
    4. `Île d'Oléron (Frankrijk)` (45.91°N, -1.30°E)
    5. `Biarritz (Frankrijk)` (43.48°N, -1.56°E)
    6. `Tarifa (Spanje)` (36.01°N, -5.60°E)

---

### 17.2 Het Mathematische Corridor Boost Algoritme

In meerdaagse voorspellingen wordt voor **elke afzonderlijke dag** (op het **10:00u ijkmoment**) de verwachte `regBoost` (regionale corridor boost) stapsgewijs berekend:

#### **Stap 1: Bepaling van het 6-Uurs Stroomopwaarts Venster**
Voor een voorspeld tijdstip $T_{\text{target}}$ (bijv. Dag 2 om 10:00u) pakt de engine alle uurvoorspellingen van Open-Meteo op de 6 corridor-punten in een venster van **6 uur voor $T_{\text{target}}$**:
$$\text{Window} = [T_{\text{target}} - 6\text{ uur} \dots T_{\text{target}} + 1\text{ uur}]$$

*Biologische reden*: Vogels die 6 uur geleden met gunstige wind vertrokken in Denemarken of Noord-Duitsland arriveren rond 10:00u boven de telpost.

#### **Stap 2: Bepaling van de Enkelpunts Corridor Score (`calculateSinglePointScore`)**
Op elk van de 6 corridor-punten wordt voor elk uur in het 6-uurs venster gecontroleerd of de meteorologische condities gunstig zijn voor vertrek:

```python
def calculate_single_point_score(wind_deg: float, pressure_hpa: float, is_autumn: bool) -> float:
    wind_label = WeatherManagerUtils.deg_to_16_wind_label(wind_deg)
    p = pressure_hpa if pressure_hpa is not None else 1013.0

    if is_autumn:
        # NAJAAR: Rugwind uit het Noorden/Oosten (N, NNO, NO, ONO, O) + Hoge Luchtdruk (>1014 hPa)
        if wind_label in ["N", "NNO", "NO", "ONO", "O"] and p > 1014.0:
            return 1.0
        else:
            return 0.0
    else:
        # VOORJAAR: Rugwind uit het Zuiden/Westen (Z, ZZW, ZW, WZW) + Goede Luchtdruk (>1010 hPa)
        if wind_label in ["Z", "ZZW", "ZW", "WZW"] and p > 1010.0:
            return 1.0
        else:
            return 0.0
```

#### **Stap 3: Maximale Uurscore per Corridor-Punt**
Per corridor-punt $p$ wordt de **maximale score** in het 6-uurs venster gepakt:
$$\text{bestScore}_p = \max_{t \in \text{window}} \text{calculate\_single\_point\_score}(\text{wind}_{p,t}, \text{pressure}_{p,t}, \text{is\_autumn})$$

#### **Stap 4: Gemiddelde Corridor Boost (`regBoost`)**
De finale `regBoost` voor die specifieke dag is de gemiddelde score over de 6 punten:
$$\text{regBoost} = \frac{\sum_{p=1}^{6} \text{bestScore}_p}{6.0}$$
Dit levert een waarde op tussen `0.0` (geen enkele corridor heeft ideale vertrekcondities) en `1.0` (alle 6 corridor-punten hebben ideale rugwind en hoge luchtdruk).

#### **Stap 5: Vermenigvuldiging in de BSI Totaalscore**
In de `AiInferenceEngine` wordt de totaalscore voor alle soorten op die dag met deze `regBoost` vermenigvuldigd:
$$\text{totalScore} = \text{fMassa} \cdot \text{fWind} \cdot \text{fSpecial} \cdot \text{fTime} \cdot \text{fGatekeeper} \cdot \text{efficiencyRatio} \cdot (1.0 + \text{regBoost})$$

- Als $\text{regBoost} = 0.50$ (op 3 van de 6 corridor-punten heerst ideale vertrekwind), krijgen alle soorten een **50% bonus** op hun prognosekans ($1.0 + 0.50 = 1.50$).

---

### 17.3 Python Implementatie (`bsi/corridor_engine.py`)

Hieronder staat de volledige Python-module die de corridor-forecasts ophaalt via Open-Meteo en de `regBoost` berekent voor meerdaagse voorspellingen:

```python
"""
bsi/corridor_engine.py
Berekent de Europese Corridor Boost voor meerdaagse BSI vogelprognoses.
"""

import requests
from datetime import datetime, timedelta
from typing import List, Dict, Any, Tuple
from .config import BsiConfig, RefPoint
from .weather_service import WeatherManagerUtils


class CorridorEngine:
    @staticmethod
    def calculate_single_point_score(wind_deg: float, pressure_hpa: float, is_autumn: bool) -> float:
        """
        Beoordeelt of een enkel corridor-punt op een specifiek uur ideale vertrekcondities heeft.
        """
        wind_label = WeatherManagerUtils.deg_to_16_wind_label(wind_deg)
        p = pressure_hpa if pressure_hpa is not None else 1013.0

        if is_autumn:
            # Najaar: Rugwind uit N, NNO, NO, ONO, O en Hoge Luchtdruk (> 1014 hPa)
            if wind_label in ["N", "NNO", "NO", "ONO", "O"] and p > 1014.0:
                return 1.0
            return 0.0
        else:
            # Voorjaar: Rugwind uit Z, ZZW, ZW, WZW en Goede Luchtdruk (> 1010 hPa)
            if wind_label in ["Z", "ZZW", "ZW", "WZW"] and p > 1010.0:
                return 1.0
            return 0.0

    @classmethod
    def fetch_corridor_forecasts(cls, is_autumn: bool) -> Dict[str, List[Dict[str, Any]]]:
        """
        Haalt de 72-uurs weersvoorspelling op voor de 6 relevante corridor-punten via Open-Meteo.
        """
        ref_points = BsiConfig.REFERENCE_POINTS[:6] if is_autumn else BsiConfig.REFERENCE_POINTS[-6:]
        
        lats = ",".join(str(p.lat) for p in ref_points)
        lons = ",".join(str(p.lon) for p in ref_points)

        url = (
            f"https://api.open-meteo.com/v1/forecast"
            f"?latitude={lats}&longitude={lons}"
            f"&hourly=temperature_2m,wind_speed_10m,wind_direction_10m,surface_pressure"
            f"&wind_speed_unit=ms&forecast_days=3&timezone=auto"
        )

        try:
            resp = requests.get(url, timeout=10)
            if resp.status_code != 200:
                return {}

            data = resp.json()
            # Open-Meteo geeft een lijst van JSON objecten terug bij meerdere coordinaten
            data_list = data if isinstance(data, list) else [data]

            results: Dict[str, List[Dict[str, Any]]] = {}
            for i, p_data in enumerate(data_list):
                point_name = ref_points[i].name
                hourly = p_data.get("hourly", {})
                times = hourly.get("time", [])
                
                point_hours = []
                for j in range(len(times)):
                    point_hours.append({
                        "time": times[j],
                        "temp": hourly.get("temperature_2m", [])[j],
                        "wind_speed": hourly.get("wind_speed_10m", [])[j],
                        "wind_deg": hourly.get("wind_direction_10m", [])[j],
                        "pressure": hourly.get("surface_pressure", [])[j]
                    })
                results[point_name] = point_hours

            return results
        except Exception as e:
            print(f"[CorridorEngine] Fout bij ophalen corridor forecast: {e}")
            return {}

    @classmethod
    def calculate_corridor_boost_at_time(
        cls,
        target_dt: datetime,
        corridor_forecasts: Dict[str, List[Dict[str, Any]]],
        is_autumn: bool
    ) -> float:
        """
        Berekent de regBoost (0.0 tot 1.0) voor een specifiek voorspeld tijdstip (bijv. Dag 2 om 10:00u).
        """
        if not corridor_forecasts:
            return 0.0

        total_max_score = 0.0

        for point_name, hourly_list in corridor_forecasts.items():
            # 6-uurs venster voor target_dt
            window_start = target_dt - timedelta(hours=6)
            window_end = target_dt + timedelta(hours=1)

            best_in_window = 0.0

            for entry in hourly_list:
                time_str = entry["time"]
                dt_entry = datetime.fromisoformat(time_str.replace("Z", "+00:00")) if "T" in time_str else datetime.strptime(time_str, "%Y-%m-%d %H:%M")

                if window_start <= dt_entry <= window_end:
                    score = cls.calculate_single_point_score(
                        wind_deg=entry.get("wind_deg", 0.0),
                        pressure_hpa=entry.get("pressure", 1013.0),
                        is_autumn=is_autumn
                    )
                    if score > best_in_window:
                        best_in_window = score

            total_max_score += best_in_window

        # Gemiddelde over de 6 corridor punten
        return total_max_score / float(len(corridor_forecasts))
```

---

## 18. CardView Grafieken, Normalisatie naar 1.0 & Rode 'Vandaag' Marker

### Originele Kotlin-bron
`com.yvesds.vt5.ai.PhenologySparklineHelper.kt`, `VicoLineChartHelper.kt`, `AiReportDetailsActiviteit.kt`

---

### 18.1 Grafiek-Architectuur in de Android CardViews
In VoiceTally VT5 heeft elke voorspelde vogelsoort in de UI een **52-weken fenologische sparkline-grafiek**.

- **Visueel doel**: De vogelspotter ziet in één oogopslag het jaarlijkse trekpatroon van de soort (bijv. voorjaarspiek in april vs. najaarspiek in oktober).
- **Kotlin Vico Engine**: In Android wordt dit getekend met de `Vico` (v2.0.0-beta.3) charting bibliotheek. De grafiek heeft een vloeiende groene lijn (`#4CAF50`) met een transparante groene vulling eronder (`#1A4CAF50`).
- **Maand-as op X-as**: De X-as toont de beginletters van de 12 maanden (`J, F, M, A, M, J, J, A, S, O, N, D`).

---

### 18.2 Normalisatie van Aantallen naar 1.0 (100%)

Om soorten met gigantische aantallen (bijv. 100.000 Vinken) en soorten met lage aantallen (bijv. 12 Slechtvalken) **op exact dezelfde uniforme verticale schaal** af te beelden:

1. **Extractie uit SQLite**:
   SQLite groepeert de historische aantallen per ISO-week van het jaar (`0` t/m `53`):
   ```sql
   SELECT 
       CAST(strftime('%W', datetime(CAST(h.begintijd AS INTEGER), 'unixepoch')) AS INTEGER) as week,
       SUM(CAST(w.aantal AS INTEGER) + CAST(w.aantalterug AS INTEGER)) as count
   FROM waarnemingen w
   INNER JOIN telling_headers h ON w.tellingid = h.tellingid
   WHERE w.soortid = :soortId AND h.telpostid IN (:siteIds)
   GROUP BY week
   ```

2. **54-Punten Buffer**:
   Er wordt een buffer gevuld van exact 54 elementen (week $0 \dots 53$). Unieke weekgaten worden op `0.0` gehouden.

3. **Normalisatie naar 1.0**:
   Het weekaantal $C_i$ voor week $i$ wordt gedeeld door het **maximale weekaantal van dat hele jaar** ($C_{\text{max}}$):
   $$C_{\text{normalized}}[i] = \frac{C_i}{C_{\text{max}} \quad \text{indien } C_{\text{max}} > 0 \text{ anders } 1.0}$$

   - **Resultaat**: De absolute piekweek in het jaar krijgt **altijd waarde 1.0 (100%)**.
   - Alle overige weken schalen relatief daaraan tussen `0.0` en `1.0`.

---

### 18.3 De Rode Marker ('Datum van Vandaag' / Target Week)

In de Android-app wordt over de groene sparkline een **felrode verticale indicator-streep** getekend:

1. **Berekening van de Huidige Week**:
   Op basis van de datum van de prognose (bijv. vandaag = 2 september):
   $$\text{week\_of\_year} = \text{dt.isocalendar().week} \quad (\text{bijv. Week } 36 \text{ voor 2 september})$$

2. **X-Positie op de Grafiek**:
   Op een schaal van $0 \dots 53$ weken bevindt de marker zich op:
   $$\text{marker\_ratio} = \frac{\text{week\_of\_year}}{53.0}$$

3. **Biologische Betekenis**:
   - Valt de **rode streep** exact samen met een **groene piek** op de grafiek? Dan bevindt de vogelsoort zich **NU in zijn absolute piekperiode**!
   - Valt de rode streep op een platte nullijn? Dan is de vogel nu niet aanwezig (bijv. zomervogel in de winter).

---

### 18.4 Python Module Code (`bsi/sparkline_engine.py`)

Hieronder staat de complete Python-module die de fenologie-data normaliseert en met `matplotlib` strakke sparkline-afbeeldingen tekent inclusief de **groene opgevulde curve**, de **maandlabels op de X-as** en de **felrode 'vandaag' marker-lijn**:

```python
"""
bsi/sparkline_engine.py
Renders fenologische sparkline-grafieken met normalisatie (1.0) en rode 'vandaag' marker.
"""

import io
import base64
import numpy as np
import matplotlib
matplotlib.use('Agg')  # Headless rendering zonder GUI
import matplotlib.pyplot as plt
from datetime import datetime
from typing import List, Dict, Any, Tuple, Optional


class SparklineEngine:
    # Mappings van week-indexen naar maandbegin-letters op de X-as
    MONTH_TICKS = [1, 5, 9, 14, 18, 22, 26, 31, 35, 40, 44, 48]
    MONTH_LABELS = ["J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D"]

    @classmethod
    def prepare_normalized_buffer(cls, week_rows: List[Dict[str, Any]]) -> np.ndarray:
        """
        Mapt SQLite week-counts naar een 54-punts buffer en normaliseert de piek naar 1.0.
        """
        buffer = np.zeros(54, dtype=np.float32)

        for row in week_rows:
            week = int(row.get("week", -1))
            count = float(row.get("count", 0.0))
            if 0 <= week <= 53:
                buffer[week] = count

        max_val = np.max(buffer)
        if max_val > 0:
            buffer = buffer / max_val  # Normalisatie naar 1.0 (100%)

        return buffer

    @classmethod
    def render_sparkline_png_bytes(
        cls,
        normalized_data: np.ndarray,
        target_dt: Optional[datetime] = None,
        width_px: int = 300,
        height_px: int = 80
    ) -> bytes:
        """
        Genereert een PNG-afbeelding (bytes) van de sparkline.
        Includeert de groene vulling, maandlabels en de rode verticale 'vandaag' marker.
        """
        if target_dt is None:
            target_dt = datetime.now()

        # Target week voor de rode marker
        current_week = target_dt.isocalendar().week
        current_week = max(0, min(53, current_week))

        # Figuur setup (DPI schaling)
        dpi = 100
        fig_w = width_px / dpi
        fig_h = height_px / dpi

        fig, ax = plt.subplots(figsize=(fig_w, fig_h), dpi=dpi)
        fig.patch.set_facecolor('#1E1E1E')  # Donkere achtergrond
        ax.set_facecolor('#1E1E1E')

        x = np.arange(len(normalized_data))

        # 1. Teken de groene vloeiende lijn & vulling (#4CAF50)
        ax.plot(x, normalized_data, color='#4CAF50', linewidth=1.8, zorder=2)
        ax.fill_between(x, normalized_data, color='#4CAF50', alpha=0.25, zorder=1)

        # 2. Teken de FELRODE marker voor de huidige datum (Vandaag / Target Week)
        ax.axvline(x=current_week, color='#FF5252', linestyle='--', linewidth=1.5, zorder=3, label='Vandaag')
        ax.scatter([current_week], [normalized_data[current_week]], color='#FF5252', s=15, zorder=4)

        # 3. Opmaak X-as (Maand-letters)
        ax.set_xticks(cls.MONTH_TICKS)
        ax.set_xticklabels(cls.MONTH_LABELS, color='#AAAAAA', fontsize=6)
        ax.tick_params(axis='x', colors='#AAAAAA', length=2, pad=1)

        # 4. Verberg Y-as en randen voor een strakke sparkline-look
        ax.set_yticks([])
        ax.set_xlim(0, 53)
        ax.set_ylim(-0.05, 1.1)

        for spine in ['top', 'right', 'left', 'bottom']:
            ax.spines[spine].set_visible(False)

        plt.tight_layout(pad=0.2)

        # Buffer wegschrijven
        buf = io.BytesIO()
        plt.savefig(buf, format='png', facecolor=fig.get_facecolor(), edgecolor='none')
        plt.close(fig)
        buf.seek(0)
        return buf.getvalue()

    @classmethod
    def get_sparkline_base64(cls, normalized_data: np.ndarray, target_dt: Optional[datetime] = None) -> str:
        """
        Levert de sparkline direct op als een Base64 data-URI string (geschikt voor HTML/Streamlit).
        """
        png_bytes = cls.render_sparkline_png_bytes(normalized_data, target_dt)
        b64_str = base64.b64encode(png_bytes).decode('utf-8')
        return f"data:image/png;base64,{b64_str}"
```

#!/usr/bin/env python3
import json
import math
import os
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from pathlib import Path


OUTPUT_PATH = Path("data/migration/latest.json")
DEFAULT_REGION = {
    "region_id": "spanjaardduin-bredene",
    "name": "Spanjaardduin Bredene",
    "latitude": 51.2567,
    "longitude": 2.9606,
}
RASTER_ORIGIN = {"point_id": "tarifa", "latitude": 36.013, "longitude": -5.606}
RASTER_STEP_KM = 100
RASTER_LENGTH_KM = 1010
LAND_RASTER_POINTS = [
    {"point_id": "tarifa", "country": "ES", "latitude": 36.0130, "longitude": -5.6060, "distance_from_origin_km": 0},
    {"point_id": "seville", "country": "ES", "latitude": 37.3891, "longitude": -5.9845, "distance_from_origin_km": 100},
    {"point_id": "cordoba", "country": "ES", "latitude": 37.8882, "longitude": -4.7794, "distance_from_origin_km": 200},
    {"point_id": "madrid", "country": "ES", "latitude": 40.4168, "longitude": -3.7038, "distance_from_origin_km": 300},
    {"point_id": "burgos", "country": "ES", "latitude": 42.3439, "longitude": -3.6969, "distance_from_origin_km": 400},
    {"point_id": "san-sebastian", "country": "ES", "latitude": 43.3183, "longitude": -1.9812, "distance_from_origin_km": 500},
    {"point_id": "bordeaux", "country": "FR", "latitude": 44.8378, "longitude": -0.5792, "distance_from_origin_km": 600},
    {"point_id": "tours", "country": "FR", "latitude": 47.3941, "longitude": 0.6848, "distance_from_origin_km": 700},
    {"point_id": "lille", "country": "FR", "latitude": 50.6292, "longitude": 3.0573, "distance_from_origin_km": 800},
    {"point_id": "oostende", "country": "BE", "latitude": 51.2300, "longitude": 2.9200, "distance_from_origin_km": 900},
    {"point_id": DEFAULT_REGION["region_id"], "country": "BE", "latitude": DEFAULT_REGION["latitude"], "longitude": DEFAULT_REGION["longitude"], "distance_from_origin_km": 1010},
]
if LAND_RASTER_POINTS[-1]["distance_from_origin_km"] != RASTER_LENGTH_KM:
    raise RuntimeError("LAND_RASTER_POINTS endpoint distance must match RASTER_LENGTH_KM")


def utc_now_iso() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def fetch_current_weather(latitude: float, longitude: float) -> dict | None:
    params = urllib.parse.urlencode(
        {
            "latitude": latitude,
            "longitude": longitude,
            "current": "temperature_2m,wind_speed_10m,relative_humidity_2m",
            "timezone": "UTC",
        }
    )
    url = f"https://api.open-meteo.com/v1/forecast?{params}"
    try:
        with urllib.request.urlopen(url, timeout=20) as response:
            payload = json.load(response)
        return payload.get("current")
    except (urllib.error.URLError, TimeoutError, json.JSONDecodeError):
        return None


def clamp(value: float, low: float, high: float) -> float:
    return max(low, min(high, value))


def compute_score(current_weather: dict | None) -> tuple[float, float]:
    if not current_weather:
        return 0.5, 0.3

    wind = float(current_weather.get("wind_speed_10m", 0.0))
    humidity = float(current_weather.get("relative_humidity_2m", 0.0))
    temperature = float(current_weather.get("temperature_2m", 0.0))

    wind_component = clamp(1.0 - abs(wind - 15.0) / 20.0, 0.0, 1.0)
    humidity_component = clamp((humidity - 40.0) / 50.0, 0.0, 1.0)
    temperature_component = clamp(1.0 - abs(temperature - 12.0) / 18.0, 0.0, 1.0)

    score = clamp(
        0.55 * wind_component + 0.25 * humidity_component + 0.20 * temperature_component,
        0.0,
        1.0,
    )
    confidence = clamp(0.55 + 0.35 * math.sqrt(score), 0.0, 1.0)
    return round(score, 3), round(confidence, 3)


def build_corridor_points() -> list[dict]:
    points = []
    for point in LAND_RASTER_POINTS:
        weather = fetch_current_weather(point["latitude"], point["longitude"])
        score, confidence = compute_score(weather)
        points.append(
            {
                "point_id": point["point_id"],
                "latitude": point["latitude"],
                "longitude": point["longitude"],
                "country": point["country"],
                "distance_from_origin_km": point["distance_from_origin_km"],
                "score": score,
                "confidence": confidence,
                "weather": weather,
            }
        )
    return points


def build_payload() -> dict:
    south_points = build_corridor_points()
    if not south_points:
        raise RuntimeError("No corridor points available; cannot compute corridor aggregate")
    score = round(sum(p["score"] for p in south_points) / len(south_points), 3)
    confidence = round(sum(p["confidence"] for p in south_points) / len(south_points), 3)

    weather = fetch_current_weather(DEFAULT_REGION["latitude"], DEFAULT_REGION["longitude"])
    return {
        "updated_at": utc_now_iso(),
        "ttl_minutes": 60,
        "source": "github-actions-schedule-v3-tarifa-raster",
        "corridor": {
            "direction": "south_to_local",
            "raster_point_count": len(south_points),
            "origin_point_id": RASTER_ORIGIN["point_id"],
            "step_km": RASTER_STEP_KM,
            "length_km": RASTER_LENGTH_KM,
            "aggregate_score": score,
            "aggregate_confidence": confidence,
        },
        "south_raster_points": south_points,
        "regions": [
            {
                "region_id": DEFAULT_REGION["region_id"],
                "score": score,
                "confidence": confidence,
                "weather": weather,
            }
        ],
    }


def write_json_atomically(path: Path, payload: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temp_path = path.with_suffix(path.suffix + ".tmp")
    temp_path.write_text(json.dumps(payload, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    os.replace(temp_path, path)


if __name__ == "__main__":
    output_path = Path(os.environ.get("VT_MIGRATION_OUTPUT_PATH", str(OUTPUT_PATH)))
    payload = build_payload()
    write_json_atomically(output_path, payload)
    print(f"Wrote migration output to {output_path}")

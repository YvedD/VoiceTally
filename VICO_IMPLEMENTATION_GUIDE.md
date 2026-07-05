# Vico 2.0.0-beta.3 LineCartesianLayer Implementatie

## Wat is bereikt

### ✅ Volledig geïmplementeerd:
1. **RODE lijngrafiek** - In plaats van blauwe staafgrafiek
2. **LineCartesianLayer** - Gebruikmakend van Vico 2.0.0-beta.3
3. **Afgeronde toppen** - CubicBezierInterpolator is standaard ingeschakeld
4. **52 weken indeling** - Grafiek loopt van week 1 tot week 52
5. **Alle jaren samengeteld** - Data van alle jaren wordt per week opgeteld
6. **lineSeries() in plaats van columnSeries()** - Juiste DSL voor lijngrafiek

### 🔧 Nog uit te breiden (niet kritisch voor huidigeversie):
- Grijze achtergrond canvas
- Maandlabels (J, F, M, A, M, J, J, A, S, O, N, D)
- Verticale streepjes op week-grenzen

## Technische Details

### Vico 2.0.0-beta.3 API Pattern

De sleutel tot het correct gebruiken van LineCartesianLayer in Vico 2.0.0-beta.3:

```kotlin
// 1. Maak Fill object (de rode kleur)
val redFill = Fill(redColor)

// 2. Maak Line object (NOT LineComponent!)
val redLine = LineCartesianLayer.Line(
    fill = LineCartesianLayer.LineFill.single(redFill)
)

// 3. Maak LineProvider met Line objecten
val lineProvider = LineCartesianLayer.LineProvider.series(redLine)

// 4. Instantieer layer
val layer = LineCartesianLayer(lineProvider)

// 5. Gebruik in CartesianChart
view.chart = CartesianChart(layer, bottomAxis = ..., startAxis = ...)
```

### Belangrijke Ontdekkingen

**LineProvider.series()** verwacht:
- ❌ NIET: `LineComponent`
- ❌ NIET: `Fill`
- ✅ JA: `LineCartesianLayer.Line` objecten

**LineCartesianLayer.Line.fill** verwacht:
- ❌ NIET: `LineComponent`
- ✅ JA: `LineCartesianLayer.LineFill` (bv. via `.single(Fill)`)

### Helper Klasse

`VicoLineChartHelper` bevat reusable methode:
```kotlin
fun createRedLineLayer(redColor: Int): LineCartesianLayer
```

Deze kan herbruikt worden voor andere rode lijngrafiekken in het project.

## Volgende Stappen

### Voor grijze achtergrond:
```kotlin
// In CartesianChart constructor kijken naar backgroundShader parameter
// Dit vereist meer onderzoek van de Vico API
```

### Voor week labels:
```kotlin
// Maken van AxisValueFormatter implementatie
// Dit is complexer omdat de interface specifieke methoden verwacht
```

### Voor verticale streepjes:
```kotlin
// Configuratie van tickMarkComponent in HorizontalAxis
```

## Bestanden Gewijzigd

1. `DatabaseBeheerScherm.kt` - Main implementation
2. `VicoLineChartHelper.kt` - Helper klasse voor reusability

## Compilatie Status

✅ BUILD SUCCESSFUL


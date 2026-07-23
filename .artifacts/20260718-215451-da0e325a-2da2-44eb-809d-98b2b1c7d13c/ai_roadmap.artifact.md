# Roadmap: On-Device AI Integratie voor VoiceTally (VT5)

Dit document beschrijft de visie en technische route voor het implementeren van een zelfvoorzienend AI-systeem binnen de VoiceTally app, waarbij de afhankelijkheid van externe Python-machines wordt geëlimineerd en de privacy/gebruikerservaring centraal staat.

## 1. Visie & Doelstellingen

*   **Autonomie:** Het genereren van AI-modellen (`.tflite`) en labels gebeurt volledig op het toestel van de gebruiker op basis van de lokale Room DB.
*   **Gebruikerscontrole:** AI-functionaliteit is opt-out (of opt-in). Pas na activatie door de gebruiker worden resources (CPU, opslag) aangesproken.
*   **Beheersbaarheid:** APK-omvang blijft < 30MB door slim gebruik van shared runtimes en het vermijden van zware gebundelde modellen.
*   **Stabiliteit:** De huidige "ongemakkelijke" flow met handmatige bestandsoverdracht verdwijnt.

---

## 2. Analyse Huidige Situatie

De huidige code bevat al een fundament (`AiManager`, `ModelManager`, `TrainingDataPreparer`), maar dit is momenteel een "skeleton" die afhankelijk is van externe input of stubs produceert.

### Valstrikken (Pitfalls)
1.  **Training Complexiteit:** Volledige training van een neuraal netwerk (bijv. met TensorFlow) op Android is complex en zwaar.
2.  **Resource Verbruik:** Modeltraining kan de batterij snel leegtrekken en het toestel traag maken als dit niet op de achtergrond (WorkManager) gebeurt.
3.  **Data Kwaliteit:** AI werkt alleen goed met voldoende data. Bij weinig lokale waarnemingen zal het model onbetrouwbaar zijn.

##Opmerkingen door Yves (de ontwikkelaar van de app): 
1. De app moet de mogelijkheid bieden om 'on-demand' de .tflite aan te maken of te verbeteren / te updaten - dit mag absoluut niet gebeuren tijdens het hoofdgebruik van de app voor migratie tellingen.
   Dit kan perfect gebeuren op het moment dat het doeltoestel opgeladen word en niet gebruikt word voor de migratie waarnemingen.
2. De Room Db beschik op dit moment reeds zo'n 154900+ waarnemigen over 4486 tellingsblokken, verspreid tussen 2003 en 2026, dat zou al meer dan voldoende moeten zijn om mee aan de slag te gaan.

> [!TIP]
> **Beheer van resources:**
> We gebruiken de Android **WorkManager** met strikte `Constraints`:
> - `setRequiresCharging(true)`: De training start pas als het toestel aan de lader ligt.
> - `setRequiresDeviceIdle(true)`: De training start pas als de gebruiker het toestel niet gebruikt.
> Dit garandeert dat de app razendsnel blijft tijdens het tellen.
---

## 3. Voorgestelde Flows & Mogelijkheden

### Optie A: De "Lightweight On-Device Training" Flow (Aanbevolen)
In plaats van een volledig nieuw model te trainen, gebruiken we een voorgestraind basismodel (Transfer Learning) dat we lokaal "finetunen" met de data uit de Room DB.

*   **Pro:** Snel, accuraat zelfs met minder data, modelbestanden blijven klein.
*   **Contra:** Vereist een initieel (klein) basismodel in de assets.

##Opmerkingen door Yves (de ontwikkelaar van de app): 
1. De ontwikkelaar wil liefst werken met een systeem dat deeplearning en zelflerend is. Meer uitleg omtrent het bedoelde basismodel is echter wel nodig (waar word dit basismodel aangemaakt, hoe komt het op het doeltoestel terecht, wat zijn de skills van dit systeem.)

> [!NOTE]
> **Antwoord van de AI Assistent:**
> Voor "Deep Learning" op een smartphone gebruiken we vaak een **Feature Extractor**.
> - **Wat is het?** Een vooraf getraind neuraal netwerk (bijv. MobileNet of een custom Dense model) dat al "begrijpt" hoe het patronen in data moet herkennen (tijdstippen, weersomstandigheden, vogel-migratie trends).
> - **Waar komt het vandaan?** Dit basismodel maken we éénmalig (bijv. in Python) en stoppen we in de `assets` map van de app. Het is klein (vaak < 2MB).
> - **Skills:** Het basismodel fungeert als "brein" dat de ruwe data uit je Room DB omzet in abstracte kenmerken. De "training" op het toestel voegt daar een specifieke laag aan toe die leert welke vogelsoorten *bij de huidige gebruiker* het meest waarschijnlijk zijn op basis van die kenmerken. Dit heet **Transfer Learning**.

### Optie B: De "Decision Tree / Random Forest" Flow
Voor numerieke data (weer, tijdstip, locatie) zijn klassieke algoritmes vaak efficiënter dan neurale netwerken op een smartphone.

*   **Pro:** Extreem lichtgewicht, geen TFLite nodig voor training (alleen voor inferentie), zeer snelle creatie op het toestel.
*   **Contra:** Minder "sexy" dan deep learning, maar vaak effectiever voor tabel-data.

---

## 4. Technische Roadmap

### Fase 1: Gebruikersinteractie & DataStore (Direct uitvoerbaar)
*   **Instelling toevoegen:** Een toggle "AI-meldingen inschakelen" in `InstellingenScherm`.
*   **DataStore Integratie:** Bewaar deze voorkeur in `AppDataStore`.
*   **Lifecycle Management:** Zorg dat `AiManager` alleen actie onderneemt (zoals de `AiUpdateWorker` starten) als deze instelling op `true` staat.

##Opmerkingen door Yves (de ontwikkelaar van de app): Goedgekeurde fase 1.

### Fase 2: On-Device Model Generatie
*   **Labels.json:** Automatisch genereren op basis van de unieke `soortid`'s in de lokale database.
*   **Model Creatie:** Implementeren van een echte trainer in `Trainer.kt` (bijv. via [TensorFlow Lite Model Maker for Android](https://www.tensorflow.org/lite/models/modify/model_maker) of een lichtere bibliotheek voor regressie/classificatie).
*   **Verificatie:** Het model moet na creatie direct gevalideerd worden tegen een subset van de lokale data.

##Opmerkingen door Yves (de ontwikkelaar van de app): Goedgekeurde fase 2.

### Fase 3: APK & Runtime Optimalisatie
*   **Google Play Services TFLite:** Overstappen van de gebundelde TFLite runtime naar de gedeelde runtime via Google Play Services. Dit haalt ~15MB van de APK af.
*   **Model Compressie:** Gebruik van 8-bit quantisatie voor de gegenereerde modellen om ze < 1MB te houden.

##Opmerkingen door Yves (de ontwikkelaar van de app): 
1. De app word NIET AANGEBODEN VIA PLAYSTORE, wat zijn dan de mogelijkheden voor een gedeelde runtime?
2. De app moet zo klein mogelijk aangeboden worden - voorstel goedgekeurd.

> [!IMPORTANT]
> **Antwoord van de AI Assistent over de Play Store:**
> Als de app niet via de Play Store wordt aangeboden, is de "shared runtime" via Play Services nog steeds mogelijk op de meeste toestellen (omdat Google Play Services als systeem-app aanwezig is). Echter, de automatische updates van die runtime werken dan minder soepel.
>
> **Alternatief voor < 30MB:**
> Sinds we de **ABI-filter** hebben toegevoegd (alleen ARM64/ARMv7), is de APK-grootte al drastisch gedaald. We kunnen waarschijnlijk de volledige TFLite runtime *in de app* houden en alsnog onder de 30MB blijven, zolang we het basismodel klein houden. Dit is de meest robuuste oplossing voor side-loading.
---

## 5. Voor- en Nadelen Overzicht

| Kenmerk | Huidige Flow (Python) | Nieuwe Flow (On-Device) |
| :--- | :--- | :--- |
| **Gebruiksgemak** | Laag (expert nodig) | Hoog (één klik) |
| **Privacy** | Matig (data-export nodig) | Maximaal (data blijft lokaal) |
| **APK Grootte** | ~20MB+ (door libs) | < 15MB (via Play Services) |
| **Model Accuratesse** | Hoog (veel rekenkracht) | Goed (specifiek voor de gebruiker) |

---

## 6. Volgende Stappen

1.  **Goedkeuring Roadmap:** Bevestiging van deze koers.
2.  **Schoonmaak AI-stubs:** Verwijderen van de huidige "stub" logica in `Trainer.kt` en `ModelManager.kt`.
3.  **Implementatie Instelling:** Toevoegen van de UI-schakelaar en DataStore-koppeling.

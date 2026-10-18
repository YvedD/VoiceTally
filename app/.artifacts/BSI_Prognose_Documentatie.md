# BSI Prognose & AI Evaluatie: Technische Documentatie

Dit document beschrijft tot in detail hoe de AI-prognoses en de Bird Sightings Index (BSI) binnen VoiceTally (versie 4.1+) worden berekend. 

---

## 1. Welke weergegevens worden opgehaald en wat wordt ermee gedaan?
Bij het opstarten van de prognose (via de `AiInferenceEngine`) wordt het huidige weer (`Current` object) opgevraagd. 

**De opgehaalde parameters:**
*   **Windrichting (graden & label):** Bijv. `225.0` graden (komt overeen met label `ZW`).
*   **Windsnelheid (10m):** Gebruikt om de Beaufort-schaal te berekenen.
*   **Temperatuur (2m):** De huidige luchttemperatuur in graden Celsius.
*   **Luchtdruk (hPa):** Huidige luchtdruk op zeeniveau.
*   *(Bij neuraal model: ook cloud cover en neerslagvlaggen).*

**Wat wordt er met deze data gedaan?**
1.  **Weather Twins (Historische wind-match):** De app zoekt in de database naar momenten in het verleden (binnen de 35km cluster en dezelfde tijd van het jaar) waar de wind **exact** in dezelfde hoek stond (met een uiterst scherpe tolerantie van +/- 5.25 graden).
2.  **Thermiek & Strategie Boosts:** Voor thermiekvliegers (bijv. roofvogels) wordt de temperatuur gebruikt als vermenigvuldiger: hogere temperatuur = exponentieel hogere score (`0.5 + ((currentTemp - 10.0) / 10.0)`). Tevens wordt gecheckt of de windkracht niet te hoog is (≥ 6 Bft breekt thermiek).
3.  **Local Wind Gatekeeper (Veto's):** Specifieke soortgroepen (Gildes) krijgen harde "veto's". Bijvoorbeeld: Pelagische soorten (zeevogels) krijgen een snoeiharde straf (`factor 0.001`) als de windrichting aanlandig in plaats van aflandig is voor jouw specifieke locatie.

---

## 2. Hoe wordt exact bepaald hoe een soort in de prognose terecht komt?
De selectie van "mogelijke soorten" voor vandaag is niet willekeurig.

1.  **Het Tijdvenster (Floating Window):** De AI berekent de huidige dag van het jaar (bijv. dag 250). Er wordt een venster rond deze dag gelegd. Voor "Birds of Interest" (Krenten) is dit een venster van 9 dagen, voor gewone soorten 7 dagen.
2.  **Fenologisch Profiel:** Alle vogelsoorten die *historisch gezien* (binnen jouw cluster) in dit venster van 7-9 dagen zijn gezien, worden opgehaald (`getSpeciesPhenologyProfile`).
3.  **Filteren:** Soorten met "spec.", "onbekend" of "/" in de naam worden onmiddellijk gediskwalificeerd. Ook soorten die niet tot een bekend Gilde (zoals Pelagics, Raptors, Vismig, etc.) behoren, worden genegeerd.
4.  **Score = Kans:** Alle overgebleven soorten worden door de "5-Factoren molen" gehaald (zie hieronder). De uiteindelijke totale score bepaalt het percentage of de sterrenwaarde.

---

## 3. Hoe wordt de BSI Score (en Kans) precies berekend?
De score voor een soort is de uitkomst van een vermenigvuldiging van vijf dynamische factoren: **Totale Kans = F1 × F2 × F3 × F4 × F5**.

*   **F1: Massa (Logaritmisch):** Gebaseerd op de historische aantallen. Hoe meer er vroeger geteld zijn, hoe hoger de basis-score. Dit wordt logaritmisch afgevlakt `1.0 + (log10(count) * 0.4)` zodat massa-soorten (zoals Vink) de zeldzaamheden niet wegdrukken.
*   **F2: High-Precision Wind-DNA:** Hoe dicht ligt de huidige windrichting bij de historisch *optimale* windrichting voor deze soort op deze locatie? Binnen 5.25° levert een maximale boost (1.8). Daarbuiten volgt een scherpe, kwadratische afstraffing (`exp(-(diff^2)/400)`).
*   **F3: Special / BoI Boost:** Een weging voor de zeldzaamheid. Staat de soort gemarkeerd als "Opmerkelijk"? Dan krijgt de score een immense vermenigvuldiger (`x 4.5`). Is het een "Krent" (Bird of Interest)? Dan krijgt hij `x 3.0`.
*   **F4: Temporal Blueprinting (Tijd & Strategie):** Het model kijkt naar de *klok*. Met behulp van een Gaussiaanse curve (Klokvormige curve) checkt het of het huidige uur (bijv. 10:00) overeenkomt met de historische piektijd van die soort. Zit je op het piekuur? Maximale punten. Tel je een nacht-trekker om 14:00? Gigantische afstraffing.
*   **F5: Local Gatekeeper:** De alles-of-niets veto gebaseerd op onshore/offshore wind of nacht-blokkades.

---

## 4. Hoe wordt de 'Norm Score' (BpH) van exemplaren per uur berekend?
Dit gebeurt via een klassieke **CPUE** (Catch Per Unit Effort) methode, in de code aangeduid als de `GigaBaseline`.

1.  **De Investering (Effort):** De app berekent hoeveel uren de telposten in het cluster in totaal zélf hebben geteld tijdens een specifieke periode (`clusterEffortHours`).
2.  **De Opbrengst (Catch):** Hoeveel vogels van soort X zijn er in diezelfde periode in dat cluster geteld.
3.  **De Norm (BpH):** `Totale Historische Aantallen / Totale Tel-uren = Baseline BpH` (Birds Per Hour). Dit is de norm-score.

**Het toekennen van sterren in het Evaluatierapport:**
Aan het eind van de dag deelt de app *jouw* prestatie door de norm. 
`Jouw BpH = Jouw getelde vogels / Jouw getelde uren vandaag`.
*   Jouw BpH is **200% (2.0x)** of meer van de norm: ⭐️⭐️⭐️⭐️⭐️
*   Jouw BpH is **120% (1.2x)** of meer van de norm: ⭐️⭐️⭐️⭐️
*   Jouw BpH is **80% (0.8x)** of meer van de norm: ⭐️⭐️⭐️
*   Jouw BpH is **40% (0.4x)** of meer van de norm: ⭐️⭐️
*   Lager dan 40%: ⭐️

---

## 5. Welke waarnemingen worden meegenomen in de berekeningen?
*   **In tijd:** Enkel waarnemingen die vallen binnen de "Floating Window" (de huidige kalenderdag +/- 3,5 tot 4,5 dagen), over **alle historische jaren** heen waarvoor data lokaal of op de server bekend is.
*   **In ruimte:** Alle tellingen die gedaan zijn door telposten die geografisch binnen het 35km-cluster vallen van jouw huidige ankerpunt (telpost).
*   **In status:** Enkel gevalideerde of opgeslagen tellingen (geen corrupte of onbekende soorten).

---

## 6. Welke invloed hebben de historische waarnemingen van de locatie zélf op de prognoses?
De hoofdlocatie fungeert als het geografische en landschappelijke **Anker**.
*   **Geografisch Anker:** De coördinaten van jouw huidige telpost bepalen het middelpunt van de 35km-radar.
*   **Landschappelijke ligging:** De locatie dicteert wat "Onshore" en "Offshore" winden zijn. Een Oostenwind is offshore in Nederland/België (kust), wat bepalend is voor Factor 5 (Gatekeeper) en het triggeren van stuwingstrek.
*   **Zwaartepunt in de GigaBaseline:** Omdat jouw locatie waarschijnlijk de meeste effort-uren heeft geleverd in je eigen archief, weegt jouw lokale geschiedenis automatisch het zwaarst in het berekenen van de BpH-norm en de piekdagen.

---

## 7. Welke invloed hebben de historische waarnemingen binnen de 35km-cluster op de prognoses?
De "Corridor-gedachte": Trekvogels vliegen niet in één rechte lijn over één telpost, maar maken gebruik van brede trek-corridors.

*   **Opvangen van 'Blinde Vlekken':** Als een zeldzame soort (bijv. een Steppekiekendief) drie dagen geleden 20km verderop is geteld op een andere post in jouw cluster, zal deze onmiddellijk als "Krent" met een hoge waarschijnlijkheid in jouw prognose verschijnen als de wind vandaag vergelijkbaar is.
*   **Robuustere Normering:** Door de tel-uren en aantallen van buur-posten op te tellen bij jouw eigen uren, wordt de "Norm Score" (BpH) statistisch veel betrouwbaarder. Dit voorkomt dat de app denkt dat een soort super zeldzaam is, simpelweg omdat jij toevallig die paar dagen vorig jaar niet in het veld stond, maar je buurman wel.
*   **Piekdag-bepaling (Gaussian Phenology):** Het bepalen van de absolute top van de trek-klokcurve (de Gaussische $\mu$) wordt berekend over de samengevoegde clusterdata, waardoor de "topweek" voor een vogel in jouw regio veel nauwkeuriger kan worden voorspeld.
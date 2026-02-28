# AI-Predictor voorstel (Belgische kust, voorjaar)

## 1) Doel en scope

Dit document werkt een **haalbaar en uitlegbaar pad** uit voor een AI-gestuurde migratieprognose voor een lokale telpost aan de Belgische kust, met focus op **voorjaarstrek**.

De output per dag (D+0 t/m D+4):
- migratie-index (0–100)
- klasse: laag / matig / goed / top
- betrouwbaarheidsniveau: laag / midden / hoog
- korte uitleg van de belangrijkste drijvers

---

## 2) Online bevindingen: welke zuidelijke locaties monitoren?

Op basis van online raadpleging (trektelnetwerken + gekende observatiepunten langs Kanaal/Pas-de-Calais) zijn deze zuidelijke aanvoerlocaties het meest relevant voor een Belgische kusttelpost in het voorjaar.

### 2.1 Kernlocaties (hoogste prioriteit)

1. **Cap Gris-Nez (Pas-de-Calais)**  
   - Klassieke bottleneck aan de Straat van Dover; zeer relevant voor doorstroom richting België.
   - In trektelnetwerken expliciet als actieve site aanwezig.

2. **Le Clipon / Jetée du Clipon (Loon-Plage, nabij Duinkerke)**  
   - Strategische kustpost dicht bij de Frans-Belgische grens.
   - Nuttig als “laatste zuidelijke check” vóór de Belgische kust.

3. **Cap Blanc-Nez (Pas-de-Calais)**  
   - Complementair met Cap Gris-Nez om kanaalstuwing en kustlijnen te volgen.

4. **Baie de Somme / Marquenterre (Picardië)**  
   - Relevante zuidelijkere bronzone waar veel doortrek/stopover plaatsvindt.
   - Functioneert als vroeg signaal voor potentieel noordwaarts transport.

### 2.2 Aanvullende locaties (middelmatige prioriteit)

5. **Le Hourdel (zuidpunt Baie de Somme)**  
6. **Oye-Plage / Platier d’Oye zone**  
7. **Wimereux-kustzone**

### 2.3 Praktisch monitoringsontwerp voor VT5

Voor robuuste voorspelling niet op één punt steunen, maar:
- **2 punten in Somme-zone** (bv. Marquenterre + Le Hourdel),
- **2 punten in Pas-de-Calais middenzone** (Cap Blanc-Nez + Cap Gris-Nez),
- **1 punt in grenszone** (Le Clipon of Oye-Plage).

Zo krijg je een **zuid -> noord corridorprofiel** i.p.v. enkel lokale momentopname.

---

## 3) Score-opbouw na inlezen zuidelijke locaties

### 3.1 Stap 1 — Feature extractie per zone

Per zone (Somme, Pas-de-Calais, Grens, Lokaal België) per dag:
- windrichting (°), windsnelheid, windstoten
- neerslag, zicht, luchtdruk
- temperatuur (optioneel dauwpunt/luchtvochtigheid)
- trends (Δ24u en Δ48u)

### 3.2 Stap 2 — Deel-scores

Bereken per dag vier deel-scores (0–100):

1. **Lokaal vliegweer** (gewicht 35%)  
   Hoe gunstig zijn condities op de telpost zelf?

2. **Aanvoerlijn-score** (gewicht 40%)  
   Zijn zuidelijke zones gunstig én coherent voor noordwaartse doorvoer?

3. **Trend/stabiliteit-score** (gewicht 15%)  
   Houden condities stand of stort het weerprofiel in?

4. **Contextscore** (gewicht 10%)  
   Seizoensfase, daglengte, historiek, enz.

**Totaalscore** = `0.35*Lokaal + 0.40*Aanvoer + 0.15*Trend + 0.10*Context`

### 3.3 Stap 3 — Corridorlogica (essentieel)

Aanvoer mag pas echt hoog scoren als:
- zuidelijke zones **opeenvolgend** gunstig zijn (Somme -> Pas-de-Calais -> Grens),
- wind in corridor niet structureel tegenwerkt,
- geen dominante “stopfactor” (zware neerslag/slecht zicht) op meerdere punten tegelijk.

Voorbeeld:
- Somme goed, Pas-de-Calais goed, grenszone goed => sterke corridorbonus.
- Somme goed maar Pas-de-Calais slecht => beperkte score (doorstroom waarschijnlijk geblokkeerd/versnipperd).

### 3.4 Stap 4 — Klasse en confidence

- 0–24 = laag  
- 25–49 = matig  
- 50–74 = goed  
- 75–100 = top

Confidence (laag/midden/hoog) op basis van:
- datacompleetheid (missende waarden?)
- ruimtelijke consistentie (zones spreken elkaar tegen?)
- modelconsensus (regelengine vs AI-correctielaag)

---

## 4) Belangrijke parameters (uitgebreide lijst)

### 4.1 Meteorologisch (verplicht)
- windrichting / windsnelheid / windstoten
- neerslagintensiteit + neerslagkans
- zicht (mist/haze als negatieve factor)
- luchtdruk + druktrend (stijgend/dalend)
- temperatuur

### 4.2 Synoptisch (sterk aanbevolen)
- frontpassages (koufront/warmtefront)
- stabiliteit over 6–12 uur vensters
- ruimtelijke gradiënt (grote verschillen tussen zones = lagere voorspelbaarheid)

### 4.3 Tijd/biologie (aanbevolen)
- dag in seizoen (vroeg/midden/laat voorjaar)
- daglengte en tijd t.o.v. zonsopgang
- optioneel: maanfase (voor nachtelijke bewegingen)

### 4.4 Observatiegedreven (aanbevolen)
- recente lokale intensiteit (laatste 1–3 dagen)
- soortgroep-specifieke gevoeligheid (zeetrek vs landtrek)
- telinspanning/waarnemersinspanning (bias-correctie)

---

## 5) AI-architectuur: uitlegbaar én uitbreidbaar

### 5.1 Fase A — Rule Engine (direct inzetbaar)
- Transparante regels + drempels.
- Altijd uitlegtekst genereren (“waarom deze score”).

### 5.2 Fase B — Hybride correctielaag
- Licht model (bijv. gradient boosting/regressie) dat Rule-score bijstuurt.
- Input: weerfeatures + corridorfeatures + Rule-score.
- Output: gecorrigeerde score + calibrated confidence.

### 5.3 Waarom deze aanpak?
- snel bruikbaar in praktijk
- uitlegbaar voor gebruikers
- later nauwkeuriger zonder black-box vanaf dag 1

---

## 6) Haalbaar implementatiepad

### Sprint 1 (2–3 weken)
- vaste locatielijst (5–7 zuidelijke punten) + lokale telpost
- data-inname actueel + 5-daagse forecast
- rule-based score + klasse + korte uitleg

### Sprint 2 (2–4 weken)
- logging van voorspelling vs waargenomen trek
- basis-kalibratie van gewichten per seizoen
- confidence zichtbaar in UI

### Sprint 3 (4+ weken)
- hybride AI-correctie trainen
- evaluatie rule-only vs hybrid
- drempels finetunen voor “top”-dagen

---

## 7) Validatie en kwaliteit

Minimale KPI’s:
- klasse-accuratesse (laag/matig/goed/top)
- false positives op “top”
- calibration error van confidence

Operationele regels:
- bij lage confidence geen “top”-advies forceren
- fallback op laatste betrouwbare run bij data-uitval

---

## 8) Conclusie voor jouw use-case

Voor een telpost aan de Belgische kust in het voorjaar is de beste start:
1. monitor een **zuidelijke corridor** met nadruk op **Cap Gris-Nez, Le Clipon, Cap Blanc-Nez en Baie de Somme/Marquenterre**,
2. bouw een **gewogen score** met lokale + aanvoer + trend + contextblokken,
3. start met **uitlegbare regels**, en voeg daarna een lichte AI-correctielaag toe.

Dat levert snel een bruikbare tool op, met een realistisch pad naar hogere nauwkeurigheid.

---

## 9) Addendum — opschaling naar volledig België + Nederland (incl. Rhône-route)

Deze uitbreiding gebruikt hetzelfde model, maar met **meerdere corridors tegelijk** i.p.v. enkel de zuidelijke kustaanvoer.

### 9.1 Uitgebreide monitorlocaties

#### A) Zuidwest-kustcorridor (Frankrijk -> België -> Nederland kust)
- Cap Blanc-Nez, Cap Gris-Nez, Le Clipon
- Wenduine / Spioenkop, Bredene (Duinbrug + Spanjaardduin), Zeebrugge/De Fonteintjes
- Breskens / Westerschelde, Westkapelle, Maasvlakte, IJmuiden, Camperduin
- Waddenketen (Texel, Vlieland, Terschelling) als noordelijke afvoercontrole

#### B) Binnenlandcorridor BE+NL
- België: Maatheide/Kristallijn (Mol), De Maten (Genk), Hobokense Polder-regio
- Nederland: Limburgse Maaszone (zoals De Hamert-omgeving), centrale oostelijke telposten (zoals Amerongse Bovenpolder-regio)

#### C) Rhône-gedreven aanvoer (mediterrane as -> Benelux)
- Rhône-as in Frankrijk (Camargue -> lagere/midden Rhône -> noordelijke uitwaaiering)
- Koppeling via noordoostelijk Frankrijk (Saône/Moezel/Maas-georiënteerde doorgang) richting België/Nederland
- Praktisch: monitor deze as als **upstream early-warning corridor** voor dagen D+2 t/m D+4

### 9.2 Score-opbouw voor BE+NL op nationaal niveau

Voor nationale dekking gebruik je vijf blokken:

1. **LocalScore (25%)**: lokaal vliegweer per telpostcluster  
2. **CoastalCorridorScore (25%)**: FR-BE-NL kuststuwing en doorvoer  
3. **InlandCorridorScore (20%)**: breedfront + rivierassen binnenland  
4. **RhoneCorridorScore (20%)**: mediterrane aanvoer die noordwaarts doorzet  
5. **ContextScore (10%)**: seizoensfase, historiek, effort-correctie

`TotalScore_BE_NL = 0.25*Local + 0.25*Coastal + 0.20*Inland + 0.20*RhoneCorridor + 0.10*Context`

### 9.3 Rhône-activatiefactor (belangrijk)

De Rhône-component telt vooral zwaar als meerdere voorwaarden tegelijk voldaan zijn:
- duidelijke trekvensters met gunstig weer op de Rhône-as (droger, voldoende zicht, bruikbare wind)
- geen dominante stopfactor in de overgangszone richting noordoost-Frankrijk
- aansluitende gunstige condities in België/Nederland op D+1 tot D+3

Modelmatig:
- bereken `RhoneRaw` (0–100) uit weer + waarnemingen op de as
- bereken `RhoneTransferFactor` (0–1) voor “kan de puls effectief Benelux bereiken?”
- `RhoneCorridorScore = RhoneRaw * RhoneTransferFactor`

Zo vermijd je vals-positieve “hoge” scores wanneer Rhône actief is, maar transport naar BE/NL meteorologisch geblokkeerd raakt.

### 9.4 Belangrijkste extra parameters voor de BE+NL versie

Aanvullend op de eerdere lijst:
- corridor-specifieke windprojectie (component in trekrichting)
- tijdvertraging per corridor (verwachte aankomstpiek in uren/dagen)
- kuststuwing-index (vooral BE-kust, Zeeland, Zuid-/Noord-Holland)
- binnenlandspreiding-index (breedfront vs kanaalvorming)
- upstream-downstream consistentiecheck (Rhône -> NO-Frankrijk -> BE/NL)

### 9.5 Praktische output voor operators

Per dag:
- nationale totaalscore + klasse
- deel-scores per corridor (kust / binnenland / Rhône)
- “route-driver” label (welke corridor drijft de verwachting)
- confidence met waarschuwing bij corridorconflict

---

## 10) Geraadpleegde online bronnen (selectie)

> Opmerking: sommige domeinen waren vanuit de sandbox niet rechtstreeks opvraagbaar via `web_fetch`, maar zijn wel opgehaald via zoekresultaat-analyse in `web_search`.

- Trektellen site-info Cap Gris-Nez: https://www.trektellen.nl/site/info/148  
- Trektellen site-info Le Clipon: https://www.trektellen.nl/site/info/7  
- Trektellen protocol zeetrektellingen: https://www.trektellen.nl/static/doc/Protocol_Zeetrektellingen_v2022.pdf  
- Trektellen documentenoverzicht: https://www.trektellen.nl/doc  
- Jaarverslag Duinbrug/Spanjaardduin (Belgische kust): https://www.trektellen.org/static/doc/Jaarverslag_Trektelposten_Duinbrug_-_Spanjaardduin_Bredene.pdf  
- Migraction Cap Gris-Nez: https://www.migraction.net/index.php?m_id=1510&frmSite=112  
- Migraction Le Clipon: https://www.migraction.net/index.php?m_id=1510&frmSite=17  
- Migraction (Rhône-vallei voorbeeldsite, voorjaarstrekcontext): https://www.migraction.net/index.php?m_id=1510&showret=1&cp=all&frmSite=36  
- LPO activiteit Cap Gris-Nez: https://www.lpo.fr/lpo-locales/region-hauts-de-france/lpo-pas-de-calais/agenda-pas-de-calais/agenda-2025-pas-de-calais/la-migration-au-cap-gris-nez  
- Parc du Marquenterre: https://www.parcdumarquenterre.fr/?lang=2  
- Trektellen platform (NL/BE site-overzicht en kaart): https://www.trektellen.nl/  
- Trektellen kaart: https://www.trektellen.nl/maps/  
- Natuurpunt trektellingen (BE context): https://www.natuurpunt.be/projecten/trektellingen  
- Vogelbescherming (wind/stuwing en voorjaarstrek): https://www.vogelbescherming.nl/actueel/bericht/vogeltrek-gejaagd-door-de-wind  
- EuroBirdPortal (flyway context): https://www.eurobirdportal.org/ebp/en/  
- EURING Migration Mapping Tool: https://euring.org/research/migration-mapping  
- Eurasian African Bird Migration Atlas: https://migrationatlas.org/  

---

## 11) Addendum — praktische start op Spanjaardduin Bredene met weinig feedback

Je insteek is logisch: eerst betrouwbaar werken op **één telpost** (Spanjaardduin Bredene), daarna pas uitbreiden.

### 11.1 Startfase met beperkte feedback uit de app

In de beginfase hoeft dit nog geen “echt AI-model” te zijn.  
De meest robuuste aanpak is:

1. **Rule-first baseline** (zoals eerder beschreven) voor dagelijkse score + klasse  
2. **Lichte handmatige evaluatie** na elke telperiode (bv. “verwachting klopte / te hoog / te laag”)  
3. **Kleine calibraties per maand** in gewichten en drempels

Zo kan je met weinig app-feedback toch al snel bruikbare voorspellingen krijgen, zonder te vroeg te overfitten op een kleine dataset.

### 11.2 Is een cloud database direct nodig?

**Kort antwoord:** niet per se voor dag 1, maar wel sterk aanbevolen zodra je structureel wil leren en opschalen.

Pragmatische fasering:
- **Fase A (lokaal/klein):** lokale opslag (CSV/SQLite/Room) kan volstaan om te starten.
- **Fase B (stabilisatie):** periodieke export/back-up naar cloud object storage of eenvoudige cloud DB.
- **Fase C (AI-trainbaar):** centrale cloud datastore + versiebeheer van trainingsdatasets wordt belangrijk.

### 11.3 Waarom cloud op termijn toch nuttig is

Voor een later “echt” AI-model heb je doorgaans nodig:
- langere historiek (seizoenen over meerdere jaren),
- consistente datakwaliteit (zelfde schema, minder ontbrekende velden),
- reproduceerbare training (welke data-versie gaf welk modelresultaat),
- gecombineerde bronnen (weer, observaties, modeloutput, evaluatiefeedback).

Dat is veel eenvoudiger met centrale cloudopslag dan met enkel lokale opslag op één toestel.

### 11.4 Aanbevolen minimum-architectuur voor jouw situatie

Voor Spanjaardduin Bredene:
- app blijft voorspellingen lokaal tonen (lage operationele complexiteit),
- dagelijks 1 recordset wegschrijven: inputfeatures + voorspelling + confidence + “observed outcome”,
- wekelijks synchroniseren naar cloud (batch i.p.v. realtime),
- later pas trainingspipeline bouwen op die cloudhistoriek.

Zo hou je de start eenvoudig, maar blokkeer je de latere AI-uitrol niet.

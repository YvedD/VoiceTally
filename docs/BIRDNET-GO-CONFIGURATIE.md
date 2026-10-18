# BirdNET-GO Configuratiehandleiding voor VoiceTally

## 🎯 Wat is opgelost?

Er was een probleem: de BirdNET-GO integratie was in de code aanwezig, maar:
- ❌ Er was **geen configuratiescherm** om de server in te stellen
- ❌ **Auto-discovery** kon de server op het lokale netwerk niet vinden
- ❌ **Geen manuele mogelijkheid** om de host/poort in te voeren
- ❌ **Geen visuele feedback** in de tellingscherm over detecties

Dit is nu **OPGELOST**!

---

## 🚀 Nieuwe Functies

### 1. **BirdNET-GO Configuratiescherm**
Lokatie: **Instellingen → "CONFIGUREER BIRDNET-GO"**

Het scherm biedt:
- ✅ **Auto-discover**: Automatisch servers op het lokale netwerk zoeken
- ✅ **Handmatige invoer**: Host, poort en protocol (HTTP/HTTPS) instellen
- ✅ **Test verbinding**: Controleren of de server bereikbaar is
- ✅ **Status display**: Huidige configuratie en verbindingsstatus weergeven

---

## 📱 Stap-voor-stap: Server configureren voor SPD Hotspot

### Scenario: Je bent op de telpost met SPD hotspot (192.168.x.x range)

#### **OPTIE 1: Auto-Discover (aanbevolen)**
1. Open VoiceTally > **Instellingen**
2. Scroll naar beneden tot **"BirdNET-GO Configuratie"**
3. Klik **"CONFIGUREER BIRDNET-GO"**
4. Klik **"Scan for BirdNET-GO Servers"**
5. Wacht tot scan klaar is
6. Als gevonden → klik **"Apply Selected Server"**
7. Klik **"SAVE CONFIGURATION"**

#### **OPTIE 2: Handmatige configuratie**
1. Open VoiceTally > **Instellingen**
2. Klik **"CONFIGUREER BIRDNET-GO"**
3. Voer in:
   - **Protocol**: HTTP (voor lokale Pi met hotspot)
   - **Host**: `birdnet.local` OF IP-adres (bijv. `192.168.1.100`)
   - **Poort**: `8080` (standaard)
4. Klik **"Test Connection"** om te verifiëren
5. Klik **"SAVE CONFIGURATION"**

---

## 🔍 Troubleshooting

### ❓ Auto-discover vindt de server niet

**Oorzaak**: BirdNET-GO advertiseert zich niet via mDNS  
**Oplossing**: Gebruik handmatige configuratie
- Controleer het IP-adres van de Pi op de SPD hotspot
- Voer deze handmatig in in het configuratiescherm

### ❓ Test Connection faalt ("Cannot reach server")

**Mogelijke oorzaken**:
1. Pi is niet verbonden met dezelfde hotspot
2. BirdNET-GO draait niet op de Pi
3. Verkeerde host/poort/protocol

**Checkpoints**:
- Ping de Pi: Open terminal/PowerShell
  ```
  ping birdnet.local
  ping 192.168.x.x  # IP-adres van de Pi
  ```
- Open in browser: `http://birdnet.local:8080`
- Controleer op de Pi dat BirdNET-GO service draait

### ❓ SPD hotspot herkenning werkt niet

**Aanvullende mDNS hostnamen die worden geprobeerd**:
- `birdnet.local`
- `birdnet-go.local`
- `raspberrypi.local`

Vraag jezelf af:
- Wat is het **exact** de hostnaam van je BirdNET-GO Pi?
- Zit je op **dezelfde hotspot** (SPD)?
- Controleer Network settings op de Pi of Android via: `Instellingen → Netwerk → Verbonden apparaten`

---

## 🔐 Configuratie opslaan en herstellen

### Waar wordt de config opgeslagen?
SharedPreferences (beveiligd locale opslag):
```
/data/data/com.yvesds.vt5/shared_prefs/vt5_prefs.xml
```

### Config wissen en opnieuw starten
1. Open **Configureer BirdNET-GO**
2. Klik **"Clear Configuration"**
3. Beantwoord "ja"
4. Alle instellingen zijn nu gewist → start opnieuw op

---

## 📡 Wat gebeurt er eenmaal geconfigureerd?

### In het TellingScherm (telpost):
1. De app **verbindt automatisch** met de BirdNET-GO server
2. **Pending detecties** (onzekere/experimentele waarnemingen) worden in **real-time** gestrewd
3. **Confirmed detecties** verschijnen in een **detectielijst**
4. Je kunt detecties **direct toevoegen** aan je telling met één tik

### Visuele feedback:
- 🟢 **Groen** (≥80% vertrouwen)
- 🟡 **Geel** (60-80% vertrouwen)
- 🟠 **Oranje** (<60% vertrouwen)

---

## 🐛 Debug info zonder logcat (op het veld!)

Omdat je op de telpost geen logcat kunt lezen, zijn hier **alternatieven**:

### 1. Toast/Pop-up berichten
Het configuratiescherm toont:
- "✓ Connection successful!" of "✗ Connection failed"
- Hoeveel servers gevonden zijn
- Actuele foutmeldingen

### 2. Status display in app
In het configuratiescherm:
```
✓ Configured: birdnet.local:8080
URL: http://birdnet.local:8080/api/v2
```

### 3. File-based logging (toekomstig)
De app schrijft diagnostiek naar:
```
Documents/VT5/logs/vt5_app_log.txt
Documents/VT5/exports/
```

---

## 🔗 Technische Details

### Endpoints die gebruikt worden:

| Endpoint | Doel |
|----------|------|
| `/api/v2/ping` | Verbindingscontrole (heartbeat) |
| `/api/v2/detections/stream` | Real-time SSE stream (pending & confirmed) |
| `/api/v2/detections/recent` | Fallback: recente detecties ophalen |

### Discovery methodes (in volgorde):

1. **NSD** (Network Service Discovery) - advertised `_http._tcp` services
2. **mDNS Active** - direct resolve: `birdnet.local`, `birdnet-go.local`, `raspberrypi.local`

---

## ✅ Checklist: Staat alles klaar?

```
[ ] BirdNET-GO Pi is voorbereid en draait
[ ] Hotspot naam is "SPD" (of jouw eigen naam)
[ ] Android tablet is op dezelfde hotspot verbonden
[ ] VoiceTally app geopend
[ ] Navigeer naar Instellingen → BirdNET-GO Configuratie
[ ] Test Auto-discover OF voer handmatig host/poort in
[ ] "Test Connection" geeft ✓ groene status
[ ] Configuratie opgeslagen
[ ] Start telling → detecties verschijnen live!
```

---

## 📞 Volgende Stap: Integratie in TellingScherm

Zodra BirdNET-GO geconfigureerd is, zul je in het **TellingScherm** zien:
- 📊 **Pending Ticker** (bovenaan): Scrollende balk van onzekere detecties
- 🎯 **Detection List**: Clickable detecties om direct toe te voegen aan telling

(Deze integratie kan nog verder uitgebreid worden met visuele panelen in de tegel-overlay)

---

## 🎓 Voorbeeld Configuratie: Raspberry Pi op SPD

**Pi Hostname**: `raspberrypi`  
**SPD Network Range**: `192.168.30.0/24`  
**Pi IP**: `192.168.30.42`  
**BirdNET-GO Port**: `8080`

**In VoiceTally**:
```
Protocol: HTTP
Host: raspberrypi.local  (of 192.168.30.42)
Port: 8080
```

**Test**: Browser op tablet → `http://raspberrypi.local:8080` → BirdNET-GO web interface verschijnt ✓

---

**Vragen of problemen?** Controleer het logbestand of volg de troubleshooting checklist hierboven!


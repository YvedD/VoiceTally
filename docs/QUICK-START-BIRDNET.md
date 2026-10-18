# 🚀 Quick Start: BirdNET-GO op SPD Hotspot

## ⚡ TL;DR (30 seconden)

1. Open VoiceTally → **Instellingen** (tandwiel icoon)
2. Scroll naar beneden → **"BirdNET-GO Configuratie"**
3. Klik **"CONFIGUREER BIRDNET-GO"** knop
4. Kies: **Auto-discover** OR **Handmatig**
5. Voer host in: `192.168.30.42` (of `raspberrypi.local`)
6. Poort: `8080`
7. Klik **"Test Connection"** → moet ✅ groen zijn
8. Klik **"SAVE"** → Klaar!

---

## 🔧 Voor Handmatige Configuratie (SPD Hotspot)

### Stap 1: Vind Pi IP-adres op SPD
```bash
# Op laptop via SPD hotspot:
nslookup raspberrypi.local
# OF ping naar standaard range:
for i in {1..255}; do ping -c 1 192.168.30.$i & done
```

### Stap 2: In VoiceTally
- **Protocol**: `HTTP` (!)
- **Host**: Afhankelijk van Pi setup:
  - Ideaal: `birdnet.local`
  - Anders: `raspberrypi.local`
  - Fallback: Directe IP `192.168.30.xx`
- **Poort**: `8080`

### Stap 3: Test & Save
- Klik **"Test Connection"**
- Wacht op feedback (groen ✅ of rood ✗)
- Klik **"SAVE CONFIGURATION"**

---

## ❌ Snelle Fixes

### Probleem: Auto-discover vindt niks
→ Gebruik **handmatige mode**, voer IP-adres in

### Probleem: Test Connection faalt
→ Controleer:
- [x] Pi is AAN
- [x] BirdNET-GO draait (`systemctl status birdnet-go`)
- [x] Je zit op dezelfde WiFi (SPD)
- [x] Poort 8080 is OPEN

### Probleem: Browser ziet `http://birdnet.local:8080` WEL
→ Dan moet de app het ook zien!
→ Wacht 5 seconden, probeer opnieuw
→ Sluit app, herstart, probeer opnieuw

---

## 📱 UI Buttons Referentie

| Button | Werking |
|--------|---------|
| **Scan for BirdNET-GO** | Start auto-discovery op netwerk |
| **Apply Selected Server** | Neemt geselecteerde server van scan |
| **Test Connection** | Controleert bereikbaarheid huidige config |
| **Save Configuration** | Slaat op in app (persistent) |
| **Clear Configuration** | Wist alles, start opnieuw |
| **Back** | Terug naar Instellingen |

---

## 🎯 Na Configuratie: Wat Verwacht Je?

Zodra **SAVED**, zal in **TellingScherm**:

1. App connect met BirdNET-GO server
2. Detecties verschijnen (als BirdNET mic actief is)
3. Pending hits: Onzekere herkenningen
4. Confirmed hits: Zekere herkenningen
5. Jij kan hits **direct toevoegen aan telling** met 1 klik

---

## 🔍 Debug: Waar Zijn De Logs?

Zonder logcat op telpost, controleer:

1. **Toast berichten** op scherm (automatisch)
2. **Status tekst** in configuratiescherm
3. **Logs bestand** later:
   ```
   Documents/VT5/logs/vt5_app_log.txt
   Documents/VT5/exports/
   ```

---

## 📋 Checklist voor Telpost

```
Thuis VOORBEREIDING:
[ ] Pi is voorbereid met BirdNET-GO
[ ] BirdNET-GO start automatisch bij boot
[ ] Microphone werkt op Pi
[ ] Test via laptop browser: http://birdnet.local:8080

OP TELPOST:
[ ] SPD hotspot is AAN
[ ] Android tablet verbonden op SPD
[ ] VoiceTally geopend
[ ] Ga naar Instellingen → BirdNET-GO
[ ] Configureer: auto-discover OR handmatig
[ ] Test Connection ✅ groen
[ ] Save
[ ] Start telling
[ ] Detecties verschijnen 🎉
```

---

## 🆘 Noodgeval: Kan niks meer

1. **Wis configuratie**
   - Open BirdNET-GO config
   - "Clear Configuration"
   - Confirmeer
   - App herstart automatisch

2. **Herstart alles**
   - Sluit VoiceTally
   - Wacht 3 sec
   - Herstart app
   - Probeer opnieuw

3. **Fallback: Browser connectie**
   - Open Chrome/Edge
   - Typ: `http://birdnet.local:8080`
   - Zien BirdNET-GO UI? → Server werkt
   - Dus probleem is in app config
   - → Volg stappen hierboven

---

## 🌐 Hostname Opties (in volgorde proberen)

1. `birdnet.local`
2. `birdnet-go.local`
3. `raspberrypi.local`
4. Directe IP (bijv. `192.168.30.42`)

App probeert alle 4 automatisch bij auto-discover.

---

## 💬 Wat Dit Scherm NIET Doet (Yet)

- ❌ Geen live detectielist weergave (apart scherm)
- ❌ Geen certificaat validatie (intern netwerk)
- ❌ Geen batch export van detecties
- ❌ Geen Android Nearby Connections

→ Deze kunnen later toegevoegd worden!

---

**Vragen?** Zie volledige handleiding: `BIRDNET-GO-CONFIGURATIE.md`  
**Tech Details?** Zie: `IMPLEMENTATION-NOTES.md`


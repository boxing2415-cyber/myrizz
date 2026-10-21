# MyRizz AI — πλωτός AI βοηθός για Android

Native εφαρμογή Android (Kotlin, Jetpack Compose, MVVM, StateFlow) που εμφανίζει μια **κινητή φούσκα** πάνω από
WhatsApp, Instagram, Messenger, Tinder κ.λπ. Πατάς τη φούσκα, επικολλάς το μήνυμα (ή ανεβάζεις screenshot) και παίρνεις
**3 φυσικές απαντήσεις** από πραγματικό AI.

> **Το Samsung Keyboard μένει ανέπαφο.** Δεν υπάρχει `InputMethodService`, δεν υπάρχει custom πληκτρολόγιο.
> Η εφαρμογή **δεν διαβάζει ποτέ αυτόματα** συνομιλίες: μόνο ό,τι επικολλήσεις, μοιραστείς ή επιλέξεις εσύ.

## Τι κάνει

| Λειτουργία | Πώς δουλεύει |
|---|---|
| Πλωτή φούσκα | Σύρεται παντού· ενεργοποίηση/απενεργοποίηση από την εφαρμογή, από την ειδοποίηση ή από το πλακίδιο Γρήγορων Ρυθμίσεων |
| Πάνελ βοηθού | Ανοίγει πάνω από την εφαρμογή που χρησιμοποιείς (όπου το Android επιτρέπει overlays) |
| Είσοδος κειμένου | Επικόλληση · *Κοινοποίηση → MyRizz AI* · επιλογή κειμένου → *Rewrite with MyRizz AI* |
| Screenshot | Επιλογή μέσω system Photo Picker → OCR στη συσκευή (ML Kit) → διορθώνεις το κείμενο → απαντήσεις |
| 3 απαντήσεις | Δομημένο JSON από το backend· διαφορετικές μεταξύ τους |
| 12 στυλ | Flirty, Smooth, Funny, Confident, Romantic, Casual, Playful, Respectful, Bold, Short & Sweet, Serious, Witty + δική σου οδηγία |
| Γλώσσες | UI Ελληνικά/English· απαντήσεις Ελληνικά, English, **Greeklish**, αυτόματη αναγνώριση |
| Εργαλεία AI | Βελτίωση, πιο flirty/αστείο/σύντομο/σίγουρο, διόρθωση, μετάφραση EL↔EN, ξεκίνημα συζήτησης, follow-up, ανάλυση τόνου |
| Αντιγραφή & αντικατάσταση | Αντιγραφή με ένα πάτημα· αντικατάσταση επιλεγμένου κειμένου στα πεδία που το επιτρέπουν (`PROCESS_TEXT`) |
| Αγαπημένα / Ιστορικό | Ιστορικό **μόνο αν το ενεργοποιήσεις**· διαγραφή όλων με ένα κουμπί |

Περιορισμοί του Android και τι κάνουμε αντ' αυτών: [`docs/PLATFORM_LIMITS.md`](docs/PLATFORM_LIMITS.md).

## Αρχιτεκτονική

```
app/       Android (Kotlin/Compose)
  overlay/   OverlayService (φούσκα + πάνελ), BubbleTileService, PickerActivity
  ui/        οθόνες Compose (onboarding, home, assistant, screenshot, ρυθμίσεις, απόρρητο…)
  data/      AiClient (OkHttp, retries, timeouts), Settings/Favorites/History (EncryptedSharedPreferences)
  domain/    SensitiveContentGuard, ConversationParser, UrlValidator, enums
  ml/        OcrEngine (ML Kit, on-device)
backend/   Node.js proxy χωρίς εξαρτήσεις — το API key ζει ΜΟΝΟ εδώ, ποτέ στο APK
.github/   workflow που χτίζει το debug APK
```

## Βήμα 1 — Backend (απαραίτητο για να δουλέψει το AI)

Χωρίς backend η εφαρμογή δείχνει καθαρά «Απαιτείται ρύθμιση» — δεν προσποιείται AI.

**Τοπικά (δοκιμή):**
```bash
cd backend
cp .env.example .env        # βάλε ANTHROPIC_API_KEY (ή AI_PROVIDER=openai + OPENAI_API_KEY)
node -e "console.log(require('crypto').randomBytes(24).toString('hex'))"   # → βάλ' το στο APP_ACCESS_TOKENS
npm run dev
npm test                    # 14 tests
```

**Στο internet (απαραίτητο HTTPS — η εφαρμογή απορρίπτει http://):**
- *Render.com:* New → Blueprint → επίλεξε το repo (`backend/render.yaml`). Στο dashboard όρισε `ANTHROPIC_API_KEY` και `APP_ACCESS_TOKENS`.
- *Οποιοδήποτε Docker host (Fly.io, Railway, Cloud Run):* `backend/Dockerfile`, port 8080, `TRUST_PROXY=1`.

Έλεγχος: ανοίγεις `https://<το-backend-σου>/health` → `{"ok":true,"aiConfigured":true,…}`.

Μεταβλητές: `AI_PROVIDER`, `ANTHROPIC_API_KEY`/`ANTHROPIC_MODEL`, `OPENAI_API_KEY`/`OPENAI_MODEL`, `APP_ACCESS_TOKENS`,
`RATE_LIMIT_PER_MIN`, `MAX_INPUT_CHARS`, `MAX_IMAGE_BYTES`, `REQUEST_TIMEOUT_MS` (βλ. `backend/.env.example`).

## Βήμα 2 — Δημιουργία του APK

### Α) Με GitHub Actions (χωρίς να εγκαταστήσεις τίποτα)
1. Ανέβασε τον φάκελο σε νέο GitHub repo (branch `main`).
2. *(Προαιρετικά)* Settings → Secrets and variables → Actions → **Variables** → `BACKEND_URL` = `https://το-backend-σου`
   (δεν είναι μυστικό· είναι μόνο η διεύθυνση. Αλλιώς την βάζεις μέσα στην εφαρμογή.)
3. Actions → **Build debug APK** → Run workflow (τρέχει και σε κάθε push).
4. Όταν τελειώσει, κατέβασε το artifact **MyRizzAI-debug-apk** → `app-debug.apk`.

### Β) Τοπικά με Android Studio
Άνοιξε τον φάκελο στο Android Studio (JDK 17+, SDK 36) → *Build → Build APK(s)*, ή από τερματικό:
```bash
./gradlew :app:assembleDebug -Pmyrizz.backendUrl=https://το-backend-σου
# → app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest          # unit tests
./gradlew :app:connectedDebugAndroidTest  # UI/instrumented tests (συσκευή ή emulator)
```

## Βήμα 3 — Εγκατάσταση στο Samsung Galaxy S26 Ultra

1. Πέρασε το `app-debug.apk` στο κινητό (Drive, USB, κ.λπ.) και άνοιξέ το από τα *Τα αρχεία μου*.
2. Αν ζητήσει: *Ρυθμίσεις → Ασφάλεια και απόρρητο → Εγκατάσταση άγνωστων εφαρμογών* → επίτρεψε στην εφαρμογή που άνοιξε το APK.
   Αν το Auto Blocker είναι ενεργό (One UI), απενεργοποίησέ το προσωρινά.
3. Ή με USB: ενεργοποίησε *Επιλογές προγραμματιστή → USB debugging* και τρέξε `adb install -r app-debug.apk`.

## Βήμα 4 — Πρώτη χρήση

1. Άνοιξε το **MyRizz AI** → ολοκλήρωσε το onboarding.
2. *Ρυθμίσεις → AI backend*: βάλε το URL και το access token → **Test connection**.
3. Δώσε την άδεια **«Εμφάνιση πάνω από άλλες εφαρμογές»** και επίτρεψε τις ειδοποιήσεις (η φούσκα τρέχει ως ορατή υπηρεσία).
4. Ενεργοποίησε τη φούσκα. Σύρε την όπου θέλεις· πάτα την για να ανοίξει το πάνελ.
5. Επικόλλησε μήνυμα (ή πάτα εικόνα για screenshot) → διάλεξε στυλ → **Generate** → πάτα μια απάντηση για **αντιγραφή** και επικόλλησέ την στη συνομιλία με το Samsung Keyboard.

Αν η φούσκα εξαφανίζεται στο Samsung: *Ρυθμίσεις → Εφαρμογές → MyRizz AI → Μπαταρία → Χωρίς περιορισμούς*.

## Απόρρητο και ασφάλεια

- Κανένα keylogging, καμία παρακολούθηση clipboard, καμία Accessibility υπηρεσία, καμία άδεια αποθήκευσης/επαφών/μικροφώνου.
- Αίτημα στο AI **μόνο** όταν πατήσεις εσύ κουμπί, και **μόνο μετά από ρητή συγκατάθεση**.
- Κείμενο που μοιάζει με κωδικό, κάρτα, IBAN, OTP ή ιδιωτικό κλειδί **μπλοκάρεται** πριν σταλεί (`SensitiveContentGuard`).
- Το OCR γίνεται **στη συσκευή**· τα screenshots σβήνονται από την προσωρινή μνήμη.
- Συνομιλίες δεν αποθηκεύονται. Ιστορικό = opt-in. *Ρυθμίσεις → Απόρρητο → Διαγραφή όλων* σβήνει τα πάντα.
- Τοπικά δεδομένα σε `EncryptedSharedPreferences`· μόνο HTTPS· το API key του AI δεν υπάρχει στο APK.
- Κανένα μήνυμα δεν στέλνεται αυτόματα.

## Τεστ

- Android unit tests: `SensitiveContentGuardTest`, `UrlValidatorTest`, `ConversationParserTest`, `AiClientTest` (MockWebServer: retries, 401/429/503, offline, κακό JSON), `ContractTest` (app ↔ backend).
- Instrumented: έλεγχος ότι **δεν** δηλώνεται πληκτρολόγιο/accessibility, ιστορικό off by default, εκκίνηση της MainActivity.
- Backend: `cd backend && npm test`.

## Γνωστοί περιορισμοί

- Το πάνελ δεν μπορεί να εμφανιστεί πάνω από οθόνες που το Android/η εφαρμογή προστατεύει (π.χ. τράπεζες, ρυθμίσεις συστήματος).
- Η εφαρμογή δεν γράφει στο πεδίο κειμένου άλλης εφαρμογής (αυτό το κάνει μόνο ένα πληκτρολόγιο)· γι' αυτό υπάρχει αντιγραφή + επικόλληση και η αντικατάσταση επιλεγμένου κειμένου.
- Το OCR (Latin script) διαβάζει καλύτερα αγγλικά/Greeklish· για ελληνικά γράμματα η εφαρμογή μπορεί να χρησιμοποιήσει το backend (`/v1/transcribe`) αν δώσεις συγκατάθεση.

# Checklist di rilascio su Play Store

Cosa è già pronto nel repo, e cosa resta da fare fuori dal codice (GitHub, Play Console,
immagini reali).

## Fatto nel repo

- [x] Workflow `.github/workflows/release-aab.yml` - produce un `.aab` firmato, on-demand
      (manual dispatch) o pushando un tag `v*`.
- [x] `app/build.gradle.kts` - `signingConfigs.release` legge le credenziali da variabili
      d'ambiente (`RELEASE_KEYSTORE_PATH`/`_PASSWORD`, `RELEASE_KEY_ALIAS`/`_PASSWORD`),
      mai committate. Senza quelle variabili, una build locale resta semplicemente non firmata.
- [x] `app/proguard-rules.pro` - documentato perché resta quasi vuoto (nessuna
      serializzazione basata su reflection nel codice).
- [x] `docs/privacy-policy.html` - informativa privacy bilingue (EN/IT), pronta per essere
      servita da GitHub Pages.
- [x] Chiave di firma release generata (RSA 2048, PKCS12, valida 30 anni) e inviata come
      file separati in chat - **non è nel repo**.

## Da fare tu

### 1. Salvare la chiave di firma
- [ ] Salva `release.keystore` e le credenziali che ti ho inviato in un posto sicuro
      (password manager, backup offline). È l'unica copia esistente.

### 2. Configurare i secrets su GitHub
Repo → Settings → Secrets and variables → Actions → New repository secret:
- [ ] `RELEASE_KEYSTORE_BASE64` (contenuto di `release.keystore.base64.txt`)
- [ ] `RELEASE_KEYSTORE_PASSWORD`
- [ ] `RELEASE_KEY_ALIAS`
- [ ] `RELEASE_KEY_PASSWORD`

### 3. Pubblicare la privacy policy
- [ ] Abilita GitHub Pages sul repo (Settings → Pages → Source: `docs/` sul branch `main`)
- [ ] Verifica che l'URL pubblicata (tipo `https://<utente>.github.io/balls/privacy-policy.html`)
      sia raggiungibile senza login
- [ ] Rileggi il testo e correggi eventuali dati societari/contatto se necessario

### 4. Generare il primo `.aab`
- [ ] Prima di lanciare la build, aggiorna se serve `versionCode`/`versionName` in
      `app/build.gradle.kts`
- [ ] GitHub → Actions → "Release AAB" → Run workflow (oppure `git tag v1.0.0 && git push --tags`)
- [ ] Scarica l'artifact `release-aab` prodotto dal workflow

### 5. Asset grafici (fuori dalla mia portata: serve un vero emulatore/dispositivo)
- [ ] Icona store 512×512
- [ ] Feature graphic 1024×500
- [ ] Almeno 2 screenshot del gioco (consigliati 4-8, su più formati schermo)
- [ ] Titolo, descrizione breve e descrizione lunga per la scheda Play Store

### 6. Play Console
- [ ] Crea l'app su Play Console
- [ ] Carica il primo `.aab` (traccia interna/chiusa consigliata prima della produzione)
- [ ] Collega l'URL della privacy policy
- [ ] Compila il "Data safety" form: **nessuna raccolta dati** (verificato: nessun permesso
      INTERNET, nessuna libreria di analytics/ads/crash-reporting nel codice)
- [ ] Compila il questionario di classificazione contenuti (content rating)
- [ ] Carica gli asset grafici del punto 5
- [ ] Verifica che l'app venga installata e giocata correttamente su un dispositivo reale
      prima di promuoverla in produzione

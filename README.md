# e2-ma-tim15-2026 — Slagalica VPL

## Tehnologije

- Java + XML
- Android Studio (Hedgehog ili noviji)
- Firebase (Auth, Firestore, Realtime Database, Cloud Messaging)

## Arhitektura

Troslojna arhitektura:

- `model/`  klase podataka (User, Question, ConnectPair, LeagueUtil...)
- `repository/`  pristup Firebase bazi (UserRepository, QuestionRepository, ConnectRepository...)
- `service/`  poslovna logika (AuthService)
- `activities/`  UI aktivnosti (HomeActivity, GameActivity, ProfileActivity...)
- `activities/fragments/`  fragmenti igara (KoZnaZnaFragment, SpojniceFragment, AsocijacijeFragment...)
- `multiplayer/`  Firebase sinhronizacija za online igru
- `views/`  custom pogledi (SerbiaMapView)

## Pokretanje aplikacije

### Preduslovi

- Android Studio **Hedgehog (2023.1.1)** ili noviji
- JDK 17 (dolazi uz Android Studio)
- Android SDK sa API level **30** (Android 11) ili višim
- Fizički uređaj ili emulator sa API 30+
- Aktivna internet veza (Firebase zahtijeva mrežni pristup)

### Koraci

1. **Kloniraj repozitorij**
   ```bash
   git clone <repo-url>
   cd e2-ma-tim15-2026
   ```

2. **Otvori projekat u Android Studiu**
   - Pokreni Android Studio
   - Odaberi `File → Open` i navigiraj do klonirane mape
   - Sačekaj da Gradle sinhronizacija završi (može potrajati nekoliko minuta prvi put)

3. **Firebase konfiguracija**
   - Fajl `app/google-services.json` je već uključen u repozitorij
   - Nije potrebna dodatna konfiguracija - aplikacija se spaja na postojeći Firebase projekat `slagalica-vrtlogalica`

4. **Pokreni aplikaciju**
   - Spoji Android uređaj putem USB-a (uključi USB debugging u Developer Options) **ili** pokreni emulator (API 30+)
   - Klikni zeleno dugme Run (Shift+F10) u Android Studiu
   - Odaberi ciljni uređaj i sačekaj instalaciju

5. **Registracija i prijava**
   - Pri prvom pokretanju odaberi **Registruj se** i unesi korisničko ime, email i lozinku
   - Nakon registracije automatski se ulazi na početni ekran
   - Postoji i dugme **Prijavi se** za postojeće naloge

### Napomene

- Aplikacija zahtijeva internet vezu za sve funkcionalnosti (Firebase Auth, baza podataka, multiplayer)
- Za testiranje multiplayera potrebna su **dva uređaja ili emulatora** istovremeno prijavljena na različite naloge
- Dnevni bonus žetona se dodjeljuje automatski pri prvoj prijavi svakog dana
- Pitanja za igre (Ko zna zna, Spojnice) se automatski učitavaju iz Firestore-a pri prvom pokretanju

## Tim 15

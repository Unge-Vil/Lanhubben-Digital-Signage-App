# Lanhubben Digital Signage – Android TV-app

Enkel Android TV / Google TV-app som viser
<https://www.lanhubben.no/lankultur/public/player.php> i fullskjerm.

- Fullskjerm (WebView), skjermen holdes våken, lyd/video spiller automatisk
- Første gang vises et valg for skjermrotasjon (0°/90°/180°/270°) for TV-er i stående format. Valget huskes.
- Menyen (last på nytt, rotasjon, avslutt) åpnes med OK/Select, piltaster, Tilbake, Meny-tasten, langt trykk på berøringsskjerm, eller via det ekstra ikonet «Lanhubben Innstillinger» i launcheren. Play/Pause laster siden på nytt.
- Skjermen holdes våken mens appen kjører. For å hindre at TV-en/Chromecast sovner av seg selv: slå av skjermsparer og energisparing i Innstillinger på enheten (se under).
- Prøver på nytt hvert 10. sekund ved nettverksfeil
- Dukker opp i Android TV-launcheren (Leanback), forsøker å starte ved oppstart
- Menytast / Play-Pause på fjernkontrollen laster siden på nytt

## Bygge

Åpne i Android Studio og kjør, eller med Gradle (JDK 17 og Android SDK kreves):

```
gradle wrapper   # kun første gang, hvis gradlew mangler
./gradlew assembleRelease
```

APK: `app/build/outputs/apk/release/`. Installer på TV med `adb install app-release.apk`.

URL-en endres i `app/build.gradle.kts` (`PLAYER_URL`).

## Hindre sleep/skjermsparer

Appen holder skjermen på, men Android TV/Google TV har egne innstillinger som kan overstyre dette etter mange timer uten fjernkontroll-bruk. På hver enhet:

- Innstillinger → System → Ambient mode / Skjermsparer: **Av** (eller «Start nå» tid til lengste)
- Innstillinger → System → Strøm og energi: sett «Slå av skjermen automatisk» og «Slå av enheten automatisk» til **Aldri**
- Slå av HDMI-CEC «auto power off» hvis TV-en skrur seg av

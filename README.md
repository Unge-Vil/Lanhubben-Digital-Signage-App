# Lanhubben Digital Signage – Android TV-app

Enkel Android TV / Google TV-app som viser
<https://www.lanhubben.no/lankultur/public/player.php> i fullskjerm.

- Fullskjerm (WebView), skjermen holdes våken, lyd/video spiller automatisk
- Første gang vises et valg for skjermrotasjon (0°/90°/180°/270°) for TV-er i stående format. Valget huskes.
- OK/Select på fjernkontrollen åpner en meny: last siden på nytt eller endre skjermrotasjon. Play/Pause laster siden på nytt.
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

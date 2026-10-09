# Endringslogg

## v1.1.0

- Fjernstyring: Lanhubben-siden kan kalle window.LanhubbenApp (getInfo, eload, setRotation, estartApp). Se docs/REMOTE_CONTROL.md
- Hovedsiden kan bare navigere innen lanhubben.no

## v1.0.1 – 2026-10-09

- Ny feilskjerm i Lanhubben-stil når siden ikke kan lastes: viser hva som er galt (ingen nettverk, serveren svarer ikke, HTTP-feil, SSL-feil), feilkode og nedtelling til neste forsøk. Laster automatisk på nytt når nettverket kommer tilbake
- Autostart: appen forsøker nå å starte etter oppstart av enheten og etter oppdatering (manglende tillatelse er lagt til). På Android 10 og nyere kan systemet blokkere dette, og da må appen åpnes manuelt én gang
- Parringen huskes: cookies skrives til disk så den overlever strømbrudd

## v1.0 – 2026-10-09

Første versjon.

- Viser Lanhubbens spiller (`https://www.lanhubben.no/lankultur/public/player.php`) i fullskjerm på Android TV, Google TV og Chromecast med Google TV
- Skjermen holdes våken, og video og lyd starter automatisk
- Automatisk ny innlasting ved nettverksfeil
- Valg for skjermrotasjon (0°/90°/180°/270°) for TV-er i stående format
- Meny via OK/Select, piltaster, Tilbake, Meny eller langt trykk, samt eget «Lanhubben Innstillinger»-ikon i launcheren
- Skjuler WebViews grå standardikon for video som lastes
- Lanhubben-ikon og TV-banner
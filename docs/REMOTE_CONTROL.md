# Fjernstyring av appen fra Lanhubben (for utvikler)

Android-appen eksponerer et lite JavaScript-grensesnitt på `window.LanhubbenApp` til siden som vises (`player.php`). Dere trenger ingen endringer i appen eller Play Store for å bruke det. Det eneste dere må gjøre er å kalle metodene fra spillerens JavaScript når en kommando kommer fra Mission Control (for eksempel over den eksisterende websocket-forbindelsen).

Grensesnittet finnes bare i appen. I vanlige nettlesere er `window.LanhubbenApp` `undefined`.

## Krav og sikkerhet

- Grensesnittet virker kun når hovedsiden ligger på `https://*.lanhubben.no` (og `lanhubben.no`). Appen navigerer ikke til andre domener i hovedvinduet.
- Alle kall fra andre sider gjør ingenting. `getInfo()` returnerer da `"{}"`.
- Sidene dere viser må ikke laste inn upålitelig tredjepartsinnhold i `<iframe>`, siden grensesnittet teknisk sett er synlig for alle rammer på siden.
- Alle metoder er synkrone og returnerer raskt. Verdier er enkle typer eller JSON-tekst.

## Metoder

| Metode | Beskrivelse | Returnerer |
|---|---|---|
| `LanhubbenApp.getInfo()` | Informasjon om appen og enheten | JSON-tekst |
| `LanhubbenApp.reload()` | Laster spilleren på nytt | ingenting |
| `LanhubbenApp.setRotation(degrees)` | Setter skjermrotasjon og lagrer valget. Gyldige verdier: `0`, `90`, `180`, `270` | `true` hvis godtatt, ellers `false` |
| `LanhubbenApp.restartApp()` | Starter appens skjerm på nytt (nullstiller WebView) | ingenting |

### `getInfo()` – eksempel på svar

```json
{
  "app": "lanhubben-signage",
  "version": "1.0.1",
  "versionCode": 2,
  "rotation": 0,
  "online": true,
  "device": "Google Chromecast",
  "android": 34
}
```

## Eksempel i `player.php`

```js
const app = window.LanhubbenApp;            // undefined utenfor appen
const inApp = typeof app !== 'undefined';

// Meld fra til serveren om at skjermen kjører appen
if (inApp) {
  const info = JSON.parse(app.getInfo());
  socket.send(JSON.stringify({ type: 'device-info', ...info }));
}

// Kommandoer fra Mission Control
socket.addEventListener('message', (e) => {
  const msg = JSON.parse(e.data);
  if (!inApp) return;
  switch (msg.command) {
    case 'reload':       app.reload(); break;
    case 'restart_app':  app.restartApp(); break;
    case 'set_rotation': {
      const ok = app.setRotation(Number(msg.degrees));
      socket.send(JSON.stringify({ type: 'ack', id: msg.id, ok }));
      break;
    }
  }
});
```

## Forslag til oppsett i Mission Control

1. En knapprad per skjerm: «Last på nytt», «Roter 0° / 90° / 180° / 270°» og «Start appen på nytt». Vis den bare for skjermer som har meldt inn `device-info` (altså kjører appen).
2. Bruk en kommando-ID og en kvittering (`ack`), slik at samme kommando ikke utføres to ganger etter en omlasting.
3. Vis versjon, rotasjon og sist sett per skjerm basert på `getInfo()`.

## Rotasjon

Rotasjonen endrer hvordan siden tegnes i appen. Den påvirker ikke Android selv. TV-en må stå i riktig retning fysisk. `90` roterer med klokken og `270` mot klokken. Valget huskes av appen, også etter omstart.

## Test under utvikling

Debug-bygget av appen aktiverer Chrome DevTools for WebView. Koble til enheten med adb, åpne `chrome://inspect` i Chrome og kall metodene fra konsollen, for eksempel `LanhubbenApp.setRotation(90)`.
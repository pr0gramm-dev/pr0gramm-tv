# Server and self-update

This document tells how the app gets its catalog from a server and how the app updates itself.

## Terms

These terms have one meaning in all documents of this repo.

- **Server address**: the text that the user types in the dialog "Server", for example `tv.example.org`. The app
  makes the URL of the manifest from it.
- **Server**: the static file server at the server address. It holds the manifest and the files that the manifest
  names.
- **Operator**: the person who puts the manifest and its files on the server.
- **Manifest**: the file `manifest.json` on the server.
- **Catalog**: the content of the manifest, that is, the sites and the apps that the app shows.
- **Start page**: the screen of the app with the tiles.
- **Self-update**: the app downloads a newer version of itself from GitHub Releases and installs it.
- **Release key**: the private key that signs the release builds (see [Release](release.md)).

## Server address

The app contains no server address. On the first start, the app has no saved server address, so it opens the
dialog "Server". The dialog has a text field for the TV keyboard. The hint is "Adresse des Katalogs, zum Beispiel
tv.example.org".

To set the server address:

1. Type the server address in the text field, for example `tv.example.org`.
2. Select "Verbinden".

The app then loads the manifest from the server. The app saves the server address only when the manifest is
valid.

The app makes the URL of the manifest from the server address with these rules:

- When the text has no scheme, the app adds `https://`.
- When the path does not end with `/manifest.json`, the app adds `/manifest.json`.
- Every build (debug and release) accepts `http://` and `https://` to any host. For example, `http://192.168.1.5:8080`
  works in your own network.
- The app refuses every other scheme. It also refuses a server address with a user, a query or a fragment.

| You type | The app loads |
|---|---|
| `tv.example.org` | `https://tv.example.org/manifest.json` |
| `example.org/tv` | `https://example.org/tv/manifest.json` |
| `http://192.168.1.5:8080` | `http://192.168.1.5:8080/manifest.json` |
| `https://tv.example.org/manifest.json` | `https://tv.example.org/manifest.json` |

When the app cannot save the server address, the dialog stays open and shows the reason:

- "Keine gültige Adresse": the text is not an http or https URL with a host.
- "Server nicht erreichbar": the app got no manifest from the server, for example because of a network error or an
  HTTP status other than 200.
- "Katalog ungültig: <first problem of the parser>": the server sent a manifest that is not valid.

Back closes the dialog. The start page then shows "Kein Server eingerichtet" and the button "Server eingeben". This
button opens the dialog again.

To change the server address:

1. On the start page, select the gear at the top and push OK. The general settings open.
2. Select the row "Server". The row shows the saved server address.
3. Push OK. The dialog "Server" opens.
4. Type the new server address and select "Verbinden".

When you save a different server address, the app restarts its process one time.

### Data of each server

The app keeps the data of each server in a separate folder on the TV:

- the catalog cache
- "Meine Liste" ("My List" in the English UI)
- "Weiterschauen" ("Continue Watching" in the English UI)
- the site settings
- the time of the last use of each site and app

When you change to a different server address, the app keeps the old folder. When you go back to the old server
address, the app shows the old data again. The pointer look and the app language are the same for all servers.

## Requirements for the server

Each static file server that obeys the rules below works. Examples are nginx, Caddy and a storage bucket with a
web front end.

- The server sends the manifest at the URL that the app makes from the server address.
- The server sends each file that the manifest names with a relative path (banners, APKs). The path of
  the file is relative to the manifest.
- A file can also have an absolute http or https URL on a different server, for example an APK in a GitHub Release.
  The app does not check the origin of a file. You choose which server address you trust.
- The server uses http or https. With https, the certificate must be valid.
- The server answers with status 200. It can send at most 5 redirects (301, 302, 303, 307, 308) before that answer.
  A redirect can go to any http or https URL. This rule applies to the manifest, the images and the APKs.
- The server needs no login. The app sends no token and no cookie.

### Example: layout of a server

This example layout serves the catalog of [`example/`](../example/README.md) at the server address
`tv.example.org`:

```text
/srv/tv/                  root of https://tv.example.org/
├── manifest.json         https://tv.example.org/manifest.json
├── img/
│   ├── bbb.jpg           "banner": "img/bbb.jpg"   (one banner for each site)
│   └── jellyfin.jpg      "banner": "img/jellyfin.jpg"
└── apks/
    └── kodi.apk          "apk": "apks/kodi.apk"
```

The APK of Jellyfin is not on this server: the manifest names it with an absolute URL on GitHub.

With the server address `example.org/tv`, the app loads `img/bbb.jpg` from `https://example.org/tv/img/bbb.jpg`.

### Example: nginx and Caddy

These two configurations are examples, not tested setups. Each one serves the folder `/srv/tv` as
`https://tv.example.org/`. Change the host name, the paths and the certificate files for your server.

nginx:

```nginx
server {
    listen 443 ssl;
    server_name tv.example.org;
    ssl_certificate     /etc/ssl/tv.example.org.crt;
    ssl_certificate_key /etc/ssl/tv.example.org.key;
    root /srv/tv;
}
```

Caddy (Caddy gets the certificate by itself):

```caddyfile
tv.example.org {
    root * /srv/tv
    file_server
}
```

### Limits and checks

- The app reads a manifest of at most 2 MiB.
- The app reads an image of at most 5 MiB. The app does not load a larger image. Its tile shows the name, and the
  next sync tries again.
- `size` and `sha256` of an APK find a broken download. Over http, these values come from the same unprotected
  manifest. Thus they do not find a file that someone in the network replaced.
- Android's signature check still protects the update of an installed app.

### When the app asks the server

- The app asks for the manifest at most one time per hour, when the start page comes to the front.
- The app keeps the time of the last answer only in memory. After a restart of the app, the app asks at once.
- "Wiederholen" on the start page without a catalog asks at once.
- The app replaces its catalog when `version` in the manifest changes.
- When the manifest is not valid, the app keeps the last valid catalog.

### Check a manifest

[`example/`](../example/README.md) is a complete catalog with one site and one app. Its README describes the format
of the manifest. To check a manifest with the parser of the app, run:

```sh
mise run check-manifest example/manifest.json          # parser and images, no APKs
mise run check-manifest example/manifest.json --apks   # also every APK, its sha256 and size
```

## Self-update

The app reads `https://api.github.com/repos/pr0gramm-dev/pr0gramm-tv/releases/latest` without a token. The
self-update works without a server address. A failure of the catalog does not stop the self-update. A failure of the
self-update does not stop the catalog.

- The tag `vMAJOR.MINOR.PATCH` of the release gives the `versionCode` (MAJOR·10 000 + MINOR·100 + PATCH)·1000. MINOR
  and PATCH are at most 99 ([Version code](release.md#version-code)). The app ignores a release with a different tag
  form.
- When the `versionCode` is greater than the installed one, the app downloads the asset `pr0gramm-tv.apk`.
- The app compares the size and the SHA-256 of the APK with `size` and `digest` from the GitHub API.
- The download follows https redirects only to `github.com` and `*.githubusercontent.com`.
- The app installs the update only while the start page is in front.
- The app asks GitHub at most one time per hour. "Nach Updates suchen" in the general settings asks at once.
- Without a token, GitHub allows 60 requests per hour and IP address. Above that limit, the general settings show
  "GitHub nicht erreichbar" until the next check.
- Only a build that the release key signed installs an update. Other builds show "Neue Version, nur per Deploy
  installierbar".

Examples of the calculation:

| Tag | `versionName` | `versionCode` |
|---|---|---|
| `v0.1.0` | `0.1.0` | 100 |
| `v1.2.3` | `1.2.3` | 10203 |
| `v2.10.0` | `2.10.0` | 21000 |

A release build reads the self-updates of the repo that built it: `mise-tasks/stage` passes `$GITHUB_REPOSITORY` as
`-PupdateRepo`. For other builds, set the repo with the Gradle property `-PupdateRepo=<owner>/<repo>`. The default is
`pr0gramm-dev/pr0gramm-tv`. The build stops when the value does not have the form `owner/repo`. For example, this command installs a debug build that
reads the releases of the fork `octocat/pr0gramm-tv`. A debug build shows a newer release, but does not install it:

```sh
mise exec -- ./gradlew installDebug -PupdateRepo=octocat/pr0gramm-tv
```

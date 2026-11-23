<p align="center">
  <img src="docs/assets/logo.png" alt="pr0gramm logo" width="128">
</p>

<h1 align="center">pr0gramm</h1>

<p align="center">
  Open your sites full screen on Android TV, with a remote-control pointer.
</p>

<p align="center">
  <a href="https://github.com/pr0gramm-dev/pr0gramm-tv/actions/workflows/ci.yml"><img alt="CI" src="https://github.com/pr0gramm-dev/pr0gramm-tv/actions/workflows/ci.yml/badge.svg"></a>
  <a href="https://github.com/pr0gramm-dev/pr0gramm-tv/releases/latest"><img alt="Release" src="https://img.shields.io/github/v/release/pr0gramm-dev/pr0gramm-tv?sort=semver"></a>
  <a href="LICENSE"><img alt="License: MIT" src="https://img.shields.io/github/license/pr0gramm-dev/pr0gramm-tv"></a>
  <img alt="Android TV" src="https://img.shields.io/badge/Android%20TV-minSdk%2033-3DDC84?logo=android&logoColor=white">
</p>

<p align="center">
  <a href="#install">Install</a> ·
  <a href="#catalog-and-server">Catalog</a> ·
  <a href="docs/">Documentation</a> ·
  <a href="CONTRIBUTING.md">Contributing</a>
</p>

<p align="center">
  <img src="docs/assets/screenshots/home.png" alt="The home screen of pr0gramm with a row of film tiles" width="860">
</p>

---

The app opens your sites in a full-screen WebView. You control it with the TV remote. The app shows the sites
and installable apps from a catalog. You choose the server that hosts the catalog.

## Features

- Full-screen WebView for each site
- Remote-control pointer and video in full screen
- Settings for each site
- A saved list ("Meine Liste")
- Installs and updates Android apps from the catalog
- Catalog on any static file server. A catalog change needs no app release.
- Self-update from the GitHub Releases of this repo

**Planned:** a "Watched" list ("Angeschaut") for films and videos that you watched to the end, and an ambient
mode. The app does not have these features yet. See [docs/roadmap.md](docs/roadmap.md).

## Screenshots

<table>
  <tr>
    <td><img src="docs/assets/screenshots/site-pointer.jpg" alt="A site in full screen with the remote-control pointer on a link"></td>
    <td><img src="docs/assets/screenshots/app-menu.png" alt="The menu of an app tile with the button Install"></td>
  </tr>
  <tr>
    <td align="center">A site with the pointer</td>
    <td align="center">Install an app from the catalog</td>
  </tr>
  <tr>
    <td><img src="docs/assets/screenshots/settings.png" alt="The settings screen"></td>
    <td><img src="docs/assets/screenshots/remote-keys.png" alt="The help screen with the remote keys"></td>
  </tr>
  <tr>
    <td align="center">Settings</td>
    <td align="center">Remote keys</td>
  </tr>
</table>

The screenshots show the example catalog in [`example/`](example/README.md): open movies of the Blender
Foundation as sites, and Jellyfin, Moonlight and Kodi as apps. The sources of the images are in
[docs/assets/screenshots/CREDITS.md](docs/assets/screenshots/CREDITS.md).

## Install

Requires Android TV 13 (API 33) or later.

1. Download `pr0gramm-tv.apk` from the [latest release](https://github.com/pr0gramm-dev/pr0gramm-tv/releases/latest).
2. Install the APK on the TV. For example, run `adb install pr0gramm-tv.apk`.
3. Start pr0gramm. Enter the address of your catalog in the server dialog.

<p align="center">
  <img src="docs/assets/screenshots/server.png" alt="The server dialog with the TV keyboard" width="560">
</p>

The app updates itself after this first install.

## Catalog and server

The app contains no server address. You enter the address on the first start.

- The catalog is a file named `manifest.json` on a static file server.
- The server needs no login.
- A site opens a web page. An app is an Android APK that the app installs and updates.

This manifest has one site and one app:

```json
{
  "version": 1,
  "date": "2026-11-23",
  "sites": [
    {
      "id": "bbb",
      "name": "Big Buck Bunny",
      "url": "https://peach.blender.org/",
      "category": "Open Movies",
      "banner": "img/bbb.jpg"
    }
  ],
  "apps": [
    {
      "package": "org.jellyfin.androidtv",
      "name": "Jellyfin",
      "banner": "img/jellyfin.jpg",
      "versionCode": 200099,
      "apk": "https://github.com/jellyfin/jellyfin-androidtv/releases/download/v0.20.0/jellyfin-androidtv-v0.20.0-release.apk",
      "sha256": "dfcc665195bed4ea087dca63df6f77497acc1185c4b15776b336d80eeaeb7fa6",
      "size": 29945596
    }
  ]
}
```

- Raise `version` with every change. The TV then loads the new catalog.
- `banner` is the 16:9 picture of a tile, for sites and for apps. It is a path relative to `manifest.json`, or an
  absolute http(s) URL.
- The complete example with 8 sites and 3 apps is in [`example/`](example/README.md). It also describes every field.
- The rules for the server are in [docs/server-and-updates.md](docs/server-and-updates.md).

## Documentation

| Topic | File |
|---|---|
| Catalog, server, self-update | [docs/server-and-updates.md](docs/server-and-updates.md) |
| Remote control, ads, test page | [docs/features.md](docs/features.md) |
| Build, configuration, development | [docs/development.md](docs/development.md) |
| Release process | [docs/release.md](docs/release.md) |
| Planned features | [docs/roadmap.md](docs/roadmap.md) |
| Architecture | [docs/architecture.md](docs/architecture.md) |
| Privacy | [PRIVACY.md](PRIVACY.md) |

## Contributing

Read [CONTRIBUTING.md](CONTRIBUTING.md) before you open a pull request. To report a security problem, read
[SECURITY.md](SECURITY.md). Do not open a public issue for it.

## License

MIT. See [LICENSE](LICENSE). The images in `example/img/` keep their own licenses, see
[example/README.md](example/README.md#credits).

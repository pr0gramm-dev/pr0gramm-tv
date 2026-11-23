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
- Remote-control pointer and video fullscreen
- Settings for each site
- A saved list ("Meine Liste")
- Catalog on any static file server. A catalog change needs no app release.
- Self-update from the GitHub Releases of this repo

**Planned:** a "Watched" list ("Angeschaut") for films and videos that you watched to the end, and an ambient
mode. The app does not have these features yet. See [docs/roadmap.md](docs/roadmap.md).

## Screenshots

<table>
  <tr>
    <td><img src="docs/assets/screenshots/site-pointer.jpg" alt="A site in full screen with the remote-control pointer on a link"></td>
    <td><img src="docs/assets/screenshots/settings.png" alt="The settings screen"></td>
  </tr>
  <tr>
    <td align="center">A site with the pointer</td>
    <td align="center">Settings</td>
  </tr>
  <tr>
    <td><img src="docs/assets/screenshots/server.png" alt="The server dialog with the TV keyboard"></td>
    <td><img src="docs/assets/screenshots/remote-keys.png" alt="The help screen with the remote keys"></td>
  </tr>
  <tr>
    <td align="center">Server dialog on the first start</td>
    <td align="center">Remote keys</td>
  </tr>
</table>

The screenshots use a demo catalog with open movies of the Blender Foundation. The sources are in
[docs/assets/screenshots/CREDITS.md](docs/assets/screenshots/CREDITS.md).

## Install

Requires Android TV 13 (API 33) or later.

1. Download `pr0gramm-tv.apk` from the [latest release](https://github.com/pr0gramm-dev/pr0gramm-tv/releases/latest).
2. Install the APK on the TV. For example, run `adb install pr0gramm-tv.apk`.
3. Start pr0gramm. Enter the address of your catalog in the server dialog.

The app updates itself after this first install.

## Catalog and server

The app contains no server address. You enter the address on the first start.

- The catalog is a file named `manifest.json` on a static file server.
- The server needs no login.
- The complete rules are in [docs/server-and-updates.md](docs/server-and-updates.md).
- A complete example is in [`example/`](example/README.md).

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

MIT. See [LICENSE](LICENSE).

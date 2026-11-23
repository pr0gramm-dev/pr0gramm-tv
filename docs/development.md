# Development

This document tells how to set up the tools, how to build and test the app, and how to install it on a TV.

## Requirements

- [mise](https://mise.jdx.dev). mise installs Java 17 and the Android SDK command-line tools from `mise.toml`.
- `jq` and `curl`.
- A TV with ADB debugging over the network on.
- For `mise run deploy` and `mise run release-key`: a secret store for the release key, for example a password
  manager with a command-line tool. See [Release key](release.md#release-key).

To install the tools:

1. Install the tools of `mise.toml`:

   ```sh
   mise install
   ```

2. Install the Android platform tools, the platform and the build tools:

   ```sh
   mise exec -- sdkmanager platform-tools "platforms;android-37.0" "build-tools;37.0.0"
   ```

## Layout

| Folder | Content |
|---|---|
| `app/`, `build-logic/`, `gradle/`, `config/`, `gradlew` | the Gradle project of the app, in the repo root |
| `example/` | the example catalog (`manifest.json`, `img/`) and the format of the manifest |
| `testpage/` | the test page |
| `mise-tasks/`, `tests/`, `docs/` | the tasks (`mise run ...`), the shell tests of the tasks and workflows, the documentation |
| `.github/workflows/` | `ci.yml` runs `mise run test` on every push and pull request; `release.yml` makes the tag release (see [Release](release.md)) |

The mise tasks run from any folder.

The code of the app has five layers after Feature-Sliced Design: `app` > `ui` > `domain` > `data` > `core`. The
task `packageGraph` in `./gradlew check` checks them ([docs/architecture.md](architecture.md), section 5).

## Build without the release key

A contributor without the release key builds and installs a debug build:

1. Connect adb to the TV. Use the IP address and the ADB port of the TV, for example:

   ```sh
   adb connect 192.0.2.10:5555
   ```

2. Build and install the debug build:

   ```sh
   mise exec -- ./gradlew installDebug
   ```

The debug key signs a debug build. A debug build never installs a self-update. When a newer release exists, the
general settings show "Neue Version, nur per Deploy installierbar".

To run the checks (tests, lint, detekt, source rules, package graph), run this command. It needs no private files.

```sh
mise run test
```

`mise run test` runs `./gradlew check` and the `build-logic` tests, then every `tests/*-test.sh`. The test
`tests/privacy-test.sh` makes sure that no tracked file names private content.

## Configuration

The file `local.json` holds only the settings of the owner for `mise run deploy`. Git ignores `local.json` and
`blocklist.txt`. To make `local.json`, copy the example:

```sh
cp local.example.json local.json
```

The example `local.example.json` contains:

```json
{
  "adb": "192.0.2.10:5555",
  "blocklist": "https://lists.example.org/domains.txt"
}
```

| Field | Required | Meaning |
|---|---|---|
| `adb` | for deploy | the host and the ADB port of the TV, `host:port` |
| `blocklist` | no | the http(s) URL of the blocklist; the app does not load requests to the domains on it (see [Ads](features.md#ads)) |

The build checks `local.json` and names the field of each error. Without `local.json`, the build uses
`local.example.json` and shows a warning. To use a different file, run `./gradlew -PlocalJson=<file> ...`.

## Build and install with the release key

```sh
mise run deploy
```

`mise run deploy` does these steps. When a check fails, deploy stops before it builds the app.

1. It checks that `local.json` has the field `adb`.
2. It checks that `config/release-cert.pem` exists and that it can read the release key (`mise-tasks/sign --check`,
   see [Release key](release.md#release-key)). Deploy never signs with the debug key.
3. It gets the version from the highest local tag `vMAJOR.MINOR.PATCH`. Without a tag, the version is `0.0.1`.
4. It downloads the blocklist.
5. It checks that the TV is reachable and that the TV confirmed the debugging.
6. It builds the unsigned release APK (R8) in one Gradle call.
7. It signs the APK.
8. It runs `adb install -r`.
9. It compiles the app on the TV with `cmd package compile -m speed`.

Before you run deploy, run `git pull --tags`. When the `versionCode` of the build is lower than the `versionCode` of
the app on the TV, `adb install` fails with `INSTALL_FAILED_VERSION_DOWNGRADE`.

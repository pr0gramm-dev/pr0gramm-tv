# Release

This document tells how the owner makes a release, how GitHub Actions builds and signs it, and where the release
key is.

## Make a release

A release is a git tag `vMAJOR.MINOR.PATCH`, for example `v0.1.0`. MINOR and PATCH are at most 99. The Mac of the
owner builds no release.

The tag gives the version of the app:

- `versionName` = `MAJOR.MINOR.PATCH`
- `versionCode` = MAJOR·10 000 + MINOR·100 + PATCH

For example, the tag `v1.2.3` gives the `versionName` `1.2.3` and the `versionCode` 10203.

To make a release:

1. Select a commit on `main`.
2. Make the tag on that commit:

   ```sh
   git tag v0.1.0
   ```

3. Push the tag:

   ```sh
   git push origin v0.1.0
   ```

## The workflow

The push of the tag starts `.github/workflows/release.yml`. The workflow runs one release at a time. It has two
jobs:

1. `build` (`contents: read`, environment `release`) runs `mise-tasks/stage <tag>`. `stage` does these steps:
   - Before Gradle, `stage` runs `mise run version check <tag>`. `stage` stops when this check refuses the tag. The
     tag must have the form `vMAJOR.MINOR.PATCH`. It must be greater than every other SemVer tag. Its commit must be
     on `origin/main`.
   - `stage` also stops when `HEAD` is not the tagged commit.
   - `stage` downloads the blocklist from `BLOCKLIST_URL`.
   - `stage` builds the APK with the version of the tag. The self-update repo of the APK is `$GITHUB_REPOSITORY`.
   - `stage` signs the APK as `pr0gramm-tv.apk`.
   - The job uploads the APK as the artifact `apk`.
2. `release` (`contents: write`, no checkout) creates the GitHub Release `<tag>` in this repo
   (`$GITHUB_REPOSITORY`). The release has exactly one asset, `pr0gramm-tv.apk`.

The workflow commits nothing and pushes no image. When only the job `release` fails, "Re-run failed jobs" repeats
the job with the APK of the same build.

`.github/workflows/ci.yml` runs `mise run test` on every push and every pull request. It only reads the repo. It has
no secret, no environment and no write permission.

## Setup on GitHub

The owner sets up these values one time, in the repo settings on GitHub:

| Name | Kind | Content |
|---|---|---|
| `release` | Environment | "Deployment branches and tags": Selected, one tag rule `v*` |
| `RELEASE_SIGNING_KEY` | Environment secret in `release` | the release key, the same base64 PKCS#8 line as in your secret store (see [Release key](#release-key)) |
| `BLOCKLIST_URL` | Repository variable | the http(s) URL of the blocklist (see [Ads](features.md#ads)) |

The job `build` runs in the environment `release`. Only that job gets the release key, and only for a tag `v*`.

## Release key

There is no keystore. The release key is one line of text: an EC P-256 key, PKCS#8 DER as base64. You keep the
release key in a secret store of your choice, for example a password manager. The certificate is in the repo.

| Where | Content |
|---|---|
| Your secret store | the release key |
| Environment secret `RELEASE_SIGNING_KEY` (environment `release`) | the same release key, for `release.yml` |
| `config/release-cert.pem` | the certificate (public) |

### Connect your secret store

The tasks read and write the release key only through two shell commands. Set the commands in `mise.local.toml` in
the repo root. Git ignores this file.

| Variable | Used by | The command must |
|---|---|---|
| `RELEASE_KEY_COMMAND` | `mise run deploy`, `mise-tasks/sign`, `mise run release-key` | print the release key on stdout |
| `RELEASE_KEY_STORE_COMMAND` | `mise run release-key` | read a new release key on stdin and store it |

Example for [pass](https://www.passwordstore.org):

```toml
# mise.local.toml
[env]
RELEASE_KEY_COMMAND = "pass show pr0gramm-tv/release-key"
RELEASE_KEY_STORE_COMMAND = "pass insert -m pr0gramm-tv/release-key"
```

Other examples for `RELEASE_KEY_COMMAND`:

| Secret store | `RELEASE_KEY_COMMAND` |
|---|---|
| 1Password CLI | `op read op://<vault>/pr0gramm-tv-release-key/password` |
| Bitwarden ([rbw](https://github.com/doy/rbw)) | `rbw get pr0gramm-tv-release-key` |
| macOS Keychain | `security find-generic-password -s pr0gramm-tv-release-key -w` |

These examples are not tested. Check the command once: `RELEASE_KEY_COMMAND` must print exactly one base64 line.

### Sign an APK

`mise-tasks/sign` is a hidden task. `mise run deploy` and `mise-tasks/stage` use it. `sign` does these steps:

1. It reads the release key from `RELEASE_SIGNING_KEY` when the variable is set. An empty value stops `sign`.
2. Without `RELEASE_SIGNING_KEY`, it reads the release key from the output of `RELEASE_KEY_COMMAND`.
3. Without both variables, it stops.
4. It pipes the release key directly into `apksigner sign --key`. No file holds the release key.
5. It runs `apksigner verify --print-certs` and checks that the signer is exactly `config/release-cert.pem`. When the
   signer is different, `sign` deletes the APK.

`sign --check` does only steps 1 to 3 and signs nothing. `mise run deploy` runs it before the build.

### Create the release key

Create the release key one time. The task never replaces a release key, because every TV accepts updates only from
the same certificate.

1. Set `RELEASE_KEY_STORE_COMMAND`. Also set `RELEASE_KEY_COMMAND`, so that the task can check the stored key.
2. Run `mise run release-key`. The task does these steps:
   1. It stops when `config/release-cert.pem` exists, or when `RELEASE_KEY_COMMAND` prints a key already.
   2. It creates the release key and the certificate with `openssl`.
   3. It pipes the release key into `RELEASE_KEY_STORE_COMMAND`.
   4. It reads the release key back with `RELEASE_KEY_COMMAND` and compares it.
   5. It writes `config/release-cert.pem`.
3. Commit `config/release-cert.pem`.
4. Copy the release key from your secret store into the environment secret `RELEASE_SIGNING_KEY` on GitHub.

When a step fails, the task does not write the certificate. No file holds the release key at any time.

### Change of the release key

`mise run deploy` never uninstalls an app. When the release key changed, the signature of the new APK does not
match the installed app, and `adb install` fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. Deploy then shows the
`adb uninstall` command with a warning. The uninstall deletes the data of the app on the TV, for example "Meine Liste" and the
settings.

Without `-PreleaseSigned=true`, a plain `./gradlew assembleRelease` signs with the debug key and shows a warning.
`mise run deploy` and `stage` pass `-PreleaseSigned=true`. A self-update cannot replace a build that the debug key
signed.

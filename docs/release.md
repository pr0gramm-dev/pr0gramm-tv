# Release

This document tells how the owner makes a release, how GitHub Actions builds and signs it, and where the release
key is.

## Make a release

A release is a git tag `vMAJOR.MINOR.PATCH`, for example `v0.1.0`. MINOR and PATCH are at most 99. The owner's
machine builds no release.

The tag gives the version of the app:

- `versionName` = `MAJOR.MINOR.PATCH`
- `versionCode` = MAJOR·10 000 + MINOR·100 + PATCH

For example, the tag `v1.2.3` gives the `versionName` `1.2.3` and the `versionCode` 10203.

### With the release PR (normal way)

[release-please](https://github.com/googleapis/release-please) finds the next version from the commit messages on
`main`. Every commit message has the form of [Conventional Commits](https://www.conventionalcommits.org):

| Commit message | Next version from 0.4.2 | Next version from 1.4.2 |
|---|---|---|
| `fix: …` | 0.4.3 | 1.4.3 |
| `feat: …` | 0.5.0 | 1.5.0 |
| `feat!: …` or a footer `BREAKING CHANGE: …` | 0.5.0 | 2.0.0 |
| `docs: …`, `chore: …`, `test: …`, `refactor: …` | no release | no release |

Before 1.0.0, a breaking change raises MINOR, not MAJOR (`bump-minor-pre-major` in `release-please-config.json`).

To make a release:

1. Merge pull requests into `main`. After each push to `main`, `.github/workflows/release-please.yml` updates one
   pull request "chore(main): release X.Y.Z". The pull request contains the next version, `CHANGELOG.md` and
   `version.txt`.
2. Read the changelog in the release PR. release-please writes the pull request again after each push to `main`.
3. Merge the release PR. Then the workflows do these steps:
   1. release-please creates the tag `vX.Y.Z` and a draft release with the notes of the changelog.
   2. The job `dispatch` starts `.github/workflows/release.yml` on the tag.
   3. `release.yml` builds and signs the APK, adds it to the draft release and publishes the release.

The TVs see the release only after step 3, because a draft release is not the latest release.

Notes:

- `version.txt` holds the version of the last release. release-please writes it. The build does not read it: the
  version comes only from the tag.
- A release PR of release-please starts no CI run, because `GITHUB_TOKEN` creates it. The release PR changes only
  `CHANGELOG.md`, `version.txt` and `.release-please-manifest.json`.

### With a tag by hand

You can also push a tag yourself. Then `release.yml` creates the release with the fixed note "Signed app version X"
and without a changelog.

1. Select a commit on `main`.
2. Make the tag on that commit and push it:

   ```sh
   git tag v0.1.0
   git push origin v0.1.0
   ```

Make each version in one way only: with the release PR, or with a tag by hand.

## The workflow

A tag `v*` starts `.github/workflows/release.yml`: the push of the tag, or the dispatch of `release-please.yml` on
the tag. The workflow runs one release at a time. It has two jobs:

1. `build` (`contents: read`, environment `release`) runs only for a tag. It runs `mise-tasks/stage <tag>`. `stage`
   does these steps:
   - Before Gradle, `stage` runs `mise run version check <tag>`. `stage` stops when this check refuses the tag. The
     tag must have the form `vMAJOR.MINOR.PATCH`. It must be greater than every other SemVer tag. Its commit must be
     on `origin/main`.
   - `stage` also stops when `HEAD` is not the tagged commit.
   - `stage` downloads the blocklist from `BLOCKLIST_URL`.
   - `stage` builds the APK with the version of the tag. The self-update repo of the APK is `$GITHUB_REPOSITORY`.
   - `stage` signs the APK as `pr0gramm-tv.apk`.
   - The job uploads the APK as the artifact `apk`.
2. `release` (`contents: write`, no checkout) publishes the GitHub Release `<tag>` in this repo
   (`$GITHUB_REPOSITORY`) with exactly one asset, `pr0gramm-tv.apk`:
   - When a release of the tag exists (the draft of release-please), the job uploads the APK and publishes the
     release as the latest release.
   - Otherwise the job creates the release.

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
| "Allow GitHub Actions to create and approve pull requests" | Setting under Actions > General | on: release-please opens the release PR |

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

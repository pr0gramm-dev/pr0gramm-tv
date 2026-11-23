# Contributing

Thank you for your interest. This guide tells how to build and test the app, and how to send a change.

## Setup

The full setup is in [docs/development.md](docs/development.md). In short:

```sh
mise install
mise exec -- sdkmanager platform-tools "platforms;android-37.0" "build-tools;37.0.0"
mise exec -- ./gradlew installDebug
```

## Before you open a pull request

1. Run `mise run test`. It runs `./gradlew check`, the `build-logic` tests and every `tests/*-test.sh`.
2. Keep the layers of the app that [docs/architecture.md](docs/architecture.md) describes. The task `packageGraph`
   in `./gradlew check` checks them.
3. Do not add a file with private content. `tests/privacy-test.sh` checks the tracked files.
4. Do not weaken a check, a lint rule or a test to make a build pass. When a change of a check is necessary, give
   the reason in the pull request.

## Pull requests

- Make a branch from `main`. Name the branch after the change, for example `fix-manifest-parser`.
- Write the commit message in English, in the form `type: short summary`, for example
  `fix: keep last valid catalog`.
- Fill in [the pull request template](.github/PULL_REQUEST_TEMPLATE.md).
- Put one change in each pull request.

## Issues

- Bug: use the bug report template. Give the app version, the TV model and the Android version.
- Feature: use the feature request template. Describe the problem before the solution. The planned features are in
  [docs/roadmap.md](docs/roadmap.md).
- Question: open a discussion or an issue.

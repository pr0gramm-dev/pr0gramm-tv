# Privacy

This document tells which data the app pr0gramm sends and which data it stores.

pr0gramm is an Android TV app. It has no account, no analytics and no crash-reporting SDK. It asks for these
permissions (see `app/src/main/AndroidManifest.xml`):

- `INTERNET`: the app loads the catalog, the websites and the self-update.
- `REQUEST_INSTALL_PACKAGES`: the app installs the apps of the catalog and the self-update.
- `UPDATE_PACKAGES_WITHOUT_USER_ACTION`: the app installs the self-update without a question to the user.

The app also reads which apps of the catalog are installed on the TV, and which of them it can start.

## Data the app sends

- **The server at your server address:** the app requests the manifest `manifest.json` and the images and the APKs
  that the manifest names. Each request contains the header `Accept-Language` with the language of the app texts
  (`en` or `de`). The app sends no token and no cookie.
- **Other servers in the manifest:** when the manifest names a file with an absolute URL, the app requests the file
  from that server.
- **The websites that you open:** the WebView loads them. The owners of the websites get the requests that their
  pages make. The WebView sends the header `X-Requested-With` with the app id `dev.pr0gramm.tv`.
- **The websites of saved pages:** when the app saves a page in "Meine Liste" or "Weiterschauen", it loads the
  cover image of the page.
- **GitHub:** the app asks `api.github.com` for the latest release, at most one time per hour, without a token. The
  app downloads `pr0gramm-tv.apk` from `github.com` or `*.githubusercontent.com` only when a newer version exists.

## Data the app stores

The app stores these data on the TV, in a separate folder for each server:

- the catalog cache
- "Meine Liste" and "Weiterschauen", with the cover images
- the settings of each site
- the time of the last use of each site and app

The app also stores these data for all servers:

- the server address
- the pointer look
- the version of a self-update that did not install
- the app language (Android stores it)

The app keeps these data until you clear the app data or uninstall the app. Android makes no backup of the data
(`android:allowBackup="false"`).

## Data at build time

The release build downloads a blocklist (`BLOCKLIST_URL`). The app does not load requests to the domains on this
list.

## Contact

For questions about this policy, open an issue in this repo.

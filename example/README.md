# Example catalog

This folder holds a complete example catalog and describes the format of the manifest.

- The **manifest** is the file `manifest.json`.
- The **catalog** is the content of the manifest: the sites and the apps that the app shows.

The operator puts the manifest and the files that it names on a static file server (the server). The TV app loads
`<server address>/manifest.json`. It loads each file with a relative path relative to the manifest. A file can also
have an absolute http(s) URL on any server.

Every build accepts http and https. The app follows at most 5 redirects
to any http or https URL. Over http, `sha256` and `size` find a broken download. They do not find a file that
someone in the network replaced. The rules for the server are in
[docs/server-and-updates.md](../docs/server-and-updates.md).

This folder has one site (Big Buck Bunny) and one app (Kodi). To use it:

1. Copy the folder.
2. Change the manifest and the files.
3. Put the folder on your server.

```text
manifest.json
img/bbb.png      banner of the site
apks/kodi.apk    not in git: put your own APK here
```

## Check a catalog

```sh
mise run check-manifest example/manifest.json          # parser and images, no APKs
mise run check-manifest example/manifest.json --apks   # also every APK, its sha256 and size
```

The task runs the parser of the app. When the manifest is valid, the task prints `manifest.json: in Ordnung`.
Otherwise it prints one line for each problem and exits with 1. A relative path is relative to the directory where
you start mise. `tests/example-test.sh` checks this example in `mise run test`.

The Kodi entry has a placeholder `sha256` and `size`. Thus `--apks` fails until you do these steps:

1. Put a real APK at `apks/kodi.apk`.
2. Get its SHA-256 and its size:

   ```sh
   shasum -a 256 apks/kodi.apk   # sha256
   wc -c < apks/kodi.apk         # size
   ```

3. Write the two values into `manifest.json`.

## Format

### Minimal manifest

This manifest has one site and no apps. It is valid:

```json
{
  "version": 1,
  "date": "2026-11-20",
  "sites": [
    {
      "id": "bbb",
      "name": "Big Buck Bunny",
      "url": "https://peach.blender.org/"
    }
  ],
  "apps": []
}
```

The full example with all fields that this folder uses is [`manifest.json`](manifest.json).

### Top level

| Field | Required | Meaning |
|---|---|---|
| `version` | yes | whole number from 1. The TV replaces its catalog when `version` changes. Thus raise `version` with every change. |
| `date` | yes | date of the catalog, `YYYY-MM-DD` |
| `sites` | yes | list of sites, can be empty |
| `apps` | yes | list of apps, can be empty |

The app ignores unknown fields. Each other error makes the whole manifest invalid. The check lists every error.

### One entry of `sites`

| Field | Required | Default | Meaning |
|---|---|---|---|
| `id` | yes | | `[a-z][a-z0-9]*`, unique in `sites` |
| `name` | yes | | name on the tile |
| `url` | yes | | the http(s) URL that the site opens (the start URL) |
| `description` | no | | short text about the site |
| `category` | no | | category, shown in the menu of the tile |
| `tags` | no | `[]` | list of strings |
| `color` | no | | background colour behind the banner, `#RRGGBB` |
| `banner` | no | | image file, a relative path like `img/a.png` or an absolute URL |
| `cursor` | no | `true` | `true`: pointer mode, `false`: focus mode |
| `desktopMode` | no | `false` | desktop layout |
| `zoom` | no | `100` | page zoom in percent, 50 to 150 |
| `blocklist` | no | `true` | block the domains of the blocklist |
| `allowInsecureContent` | no | `false` | allow http content on an https page |
| `allow` | no | `[]` | domains that the blocklist never blocks for this site; the host of `url` is always allowed |

### One entry of `apps`

| Field | Required | Meaning |
|---|---|---|
| `package` | yes | Android package name like `org.xbmc.kodi`, unique in `apps`. `dev.pr0gramm.tv` is not allowed: the app updates itself from GitHub Releases (self-update). |
| `name` | yes | name on the tile |
| `image` | no | image file, a relative path like `img/kodi.png` or an absolute URL |
| `versionCode` | yes | `versionCode` of the APK, greater than 0 |
| `apk` | yes | APK file, a relative path like `apks/kodi.apk` or an absolute URL like `https://github.com/o/r/releases/download/v1/a.apk` |
| `sha256` | yes | SHA-256 of the APK, 64 hex digits in lowercase |
| `size` | yes | size of the APK in bytes, greater than 0 |

### Relative paths and absolute URLs

A relative path has segments of `A-Z a-z 0-9 . _ -`. It has no scheme, no leading `/`, and no segment `.` or `..`.

| Value | Valid | Reason |
|---|---|---|
| `img/bbb.png` | yes | relative path |
| `https://github.com/o/r/releases/download/v1/a.apk` | yes | absolute URL |
| `/img/bbb.png` | no | leading `/` |
| `../img/bbb.png` | no | segment `..` |
| `img/my banner.png` | no | space in a segment |

An absolute URL has the scheme `http` or `https` (in any case, for example `HTTPS://`) and a host. A query is
allowed. A user and a fragment are not allowed. The app loads the file from exactly this URL. An image is at most
5 MiB.

The app stores an image from a URL as `url-<SHA-256 of the URL text>`. Thus two URLs are the same image only when
their text is the same. `check-manifest` checks an absolute URL only with the parser. It loads nothing from the
network, also not with `--apks`.

## Credits

`img/bbb.png` shows Big Buck Bunny, (c) copyright 2008, Blender Foundation, https://peach.blender.org/.
The film and its images are under the Creative Commons Attribution 3.0 license (CC BY 3.0,
https://creativecommons.org/licenses/by/3.0/).

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

This folder has 8 sites and 3 apps:

- The sites are the open movies of the Blender Foundation. Each site has a banner.
- Jellyfin and Moonlight are apps with an image. Their APKs are release assets on GitHub (absolute URLs) with the
  real `versionCode`, `sha256` and `size`.
- Kodi is an app without an image: its tile shows the name. Its APK is a relative path on your own server.

To use the example catalog:

1. Copy the folder.
2. Change the manifest and the files.
3. Put the folder on your server.

To look at the example catalog on a TV or in the emulator, serve this folder with any static file server. Then
enter its address in the server dialog. For example:

```sh
cd example && python3 -m http.server 8088   # in the Android emulator, the server address is http://10.0.2.2:8088
```

```text
manifest.json
img/<site id>.jpg   banner of each site, 640 × 360 px
img/jellyfin.jpg    image of the Jellyfin app
img/moonlight.jpg   image of the Moonlight app
apks/kodi.apk       not in git: put your own APK here
```

## Check a catalog

```sh
mise run check-manifest example/manifest.json          # parser and images, no APKs
mise run check-manifest example/manifest.json --apks   # also every APK, its sha256 and size
```

The task runs the parser of the app. When the manifest is valid, the task prints `manifest.json: in Ordnung`.
Otherwise it prints one line for each problem and exits with 1. A relative path is relative to the directory where
you start mise. `tests/example-test.sh` checks this example in `mise run test`.

The Kodi entry has a placeholder `sha256` and `size`. `--apks` checks only APKs with a relative path. Thus
`--apks` fails until you do these steps:

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
| `banner` | no | | the picture of the tile (see [Banners](#banners)) |
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
| `banner` | no | the picture of the tile (see [Banners](#banners)) |
| `versionCode` | yes | `versionCode` of the APK, greater than 0 |
| `apk` | yes | APK file, a relative path like `apks/kodi.apk` or an absolute URL like `https://github.com/o/r/releases/download/v1/a.apk` |
| `sha256` | yes | SHA-256 of the APK, 64 hex digits in lowercase |
| `size` | yes | size of the APK in bytes, greater than 0 |

### Banners

Every entry of the catalog that has a tile names its picture `banner`: a site, an app, and each later kind of
entry, for example the entries of the planned ambient mode ([roadmap](../docs/roadmap.md)). Do not use a different
field name for a picture.

- A banner has the format 16:9, for example 640 × 360 px. The example banners have this size.
- A banner is an image file: a relative path like `img/bbb.jpg`, or an absolute URL. It is at most 5 MiB.
- Without a banner, the tile shows the name.
- An app reads only `banner`. The app ignores an old field `image`.

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

The banners show stills from the open movies of the [Blender Foundation](https://studio.blender.org/films/). The
stills come from Wikimedia Commons. The script that made the banners cropped each still to 640 × 360 px and added
the film title. The authors do not endorse these changes.

| File | Film | Source | Author | License |
|---|---|---|---|---|
| `img/bbb.jpg` | Big Buck Bunny | [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Big.Buck.Bunny.-.Frank.Rinky.Gimera.png) | Blender Foundation | CC BY 3.0 |
| `img/sintel.jpg` | Sintel | [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Durian_-_Sintel-wallpaper-sintel.jpg) | David REVOY | CC BY 3.0 |
| `img/tos.jpg` | Tears of Steel | [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Tears_of_Steel_frame_01_2a.jpg) | Blender Foundation, Project Mango | CC BY 3.0 |
| `img/ed.jpg` | Elephants Dream | [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Elephants_Dream_-_Emo_and_Proog.png) | Ton Roosendaal et al. | CC BY 2.5 |
| `img/cosmos.jpg` | Cosmos Laundromat | [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:CosmosLaundromatFranck.jpg) | Blender Foundation | CC BY 3.0 |
| `img/dillama.jpg` | Caminandes 2: Gran Dillama | [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Caminandes_gran_dillama.png) | Francesco Siddi, Pablo Vazquez | CC BY 3.0 |
| `img/llamigos.jpg` | Caminandes 3: Llamigos | [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Blender_Foundation_-_Caminandes_-_Episode_3_-_Llamigos_-_Koro_and_Oti_share_more_food_to_eat_at_night.png) | Blender Foundation | CC BY 4.0 |
| `img/spring.jpg` | Spring | [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Blender_Open_Movie_-_Spring_%282019%29.png) | Alexandru.rosu | CC0 |

The banners of the apps are their Android TV banners, scaled to 640 × 360 px:

| File | Source | License |
|---|---|---|
| `img/jellyfin.jpg` | [jellyfin/jellyfin-ux](https://github.com/jellyfin/jellyfin-ux/blob/master/branding/android/tv/app/mipmap-xxxhdpi/app_banner.png) | CC BY-SA 4.0 |
| `img/moonlight.jpg` | [moonlight-stream/moonlight-android](https://github.com/moonlight-stream/moonlight-android/blob/master/fastlane/metadata/android/en-US/images/tvBanner.png) | GPL-3.0 |

Every file in `img/` keeps the license in the tables above. The MIT license of this repo does not apply to these
files. Jellyfin, Moonlight and Kodi are names of their projects. This project has no connection with these projects.

# Features

This document describes the remote control on a site, the ad protection and the test page.

## Remote control

On a site, the app has two modes: pointer mode and focus mode. To change between the two modes, hold OK for
600 ms. The change does not work while the page menu is open.

### Pointer mode

- The arrow keys move the pointer. When you hold an arrow key, the pointer moves faster.
- OK clicks at the position of the pointer.
- When you push the pointer against an edge of the page, the page scrolls.
- When a video with sound starts, the pointer hides. The next arrow key shows the pointer again.

### Focus mode

- The arrow keys move the focus from element to element. An orange frame shows the selected element.
- OK clicks the selected element.
- A text field opens the keyboard only when you push OK. The keyboard does not open when the frame moves to the
  text field.
- On a video player, the arrow keys move the focus to the next element. OK plays the video in full screen. This
  applies to a video in the page and to a player that the page embeds from a different site.

### Video in full screen

- ▲ and ▼ set the volume of the TV.
- ◀ and ▶ go 10 s back or forward.
- OK pauses or plays the video.

### Back

When you release Back, the app does the first possible action of this list:

1. Leave the video full screen.
2. Close the page menu.
3. Go back one page in the history.
4. Close the site.

When you hold Back for 600 ms, the app closes the site at once and shows the start page.

## Ads

- A new window opens in the app itself, but only after a click.
- A player that a page embeds from a different site cannot open a new window. The clicks in such players often open
  ads.
- When a page sends itself to a different site without a key press, the app stops the page. The app then shows
  "Weiterleitung blockiert".
- Links, forms and redirects of the server still work.

### Blocklist

The blocklist is a list of domains. The app does not load requests to a domain on the blocklist or to its
subdomains. The blocklist contains only whole domains. It has no element rules and no path rules (uBlock syntax).

The build gets the blocklist from a URL:

- `mise run deploy` takes the URL from the field `blocklist` in `local.json`.
- The tag release takes the URL from the repository variable `BLOCKLIST_URL`.

Each of the two gives the downloaded file to Gradle. The TV never downloads the blocklist. Gradle does these steps:

1. Gradle reads one domain from each line. It ignores lines that start with `#` and lines that are not a domain.
2. Gradle checks that the file has at least 1000 domains.
3. Gradle replaces `blocklist.txt`. The first line of `blocklist.txt` is `# source: <URL>`.
4. Gradle builds the domains into the app.

Example of a blocklist file with the correct format:

```text
# lines that start with # are comments
ads.example.com
tracker.example.net
```

A hosts file (`0.0.0.0 ads.example.com`) and uBlock rules (`||ads.example.com^`) give no domains. Use a list with
one domain on each line, for example the "domains" format of a [hagezi](https://github.com/hagezi/dns-blocklists)
list.

When a step fails:

- The download or the check fails in `mise run deploy`: when the old `blocklist.txt` came from the same URL, the
  build keeps it and shows a warning. Otherwise the build stops.
- A Gradle build with a blocklist URL but without `blocklist.txt` shows a warning. It builds the app without a
  blocklist.
- A Gradle build without a blocklist URL builds the app without a blocklist.
- The download or the check fails in the tag release: the workflow stops before the release.

The host of the `url` of a site is always allowed, with its subdomains. When the blocklist breaks a video on a site,
do one of these two steps in the manifest:

- Add the host of the video to `allow` of the site.
- Set `"blocklist": false` for the site.

Example of one entry of `sites` with each of the two fields:

```json
{
  "id": "bbb",
  "name": "Big Buck Bunny",
  "url": "https://peach.blender.org/",
  "allow": ["video.example.net"]
}
```

```json
{
  "id": "bbb",
  "name": "Big Buck Bunny",
  "url": "https://peach.blender.org/",
  "blocklist": false
}
```

## Test page

The file `testpage/index.html` checks cookies, popups, the user agent, the zoom, the text input and the scrolling.
To open the test page on the TV:

1. Start a web server on your computer:

   ```sh
   python3 -m http.server 8000 -d testpage
   ```

2. Connect port 8000 of the TV to port 8000 of your computer:

   ```sh
   adb reverse tcp:8000 tcp:8000
   ```

3. Add a site with `"url": "http://localhost:8000/"` to your manifest, for example:

   ```json
   { "id": "testpage", "name": "Test page", "url": "http://localhost:8000/" }
   ```

4. Raise `version` in the manifest. The TV replaces its catalog at the next check of the server (see
   [When the app asks the server](server-and-updates.md#when-the-app-asks-the-server)).

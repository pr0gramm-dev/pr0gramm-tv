# Roadmap

This document lists planned features. The app does not have these features yet.

Each section has a status, the facts about the app today, and open questions. An open question has no decision
yet. Do not read an open question as a plan.

## "Angeschaut" ("Watched", planned)

**Status: planned.** The app does not have this feature.

### The app today

- The start page shows two shelves of entries: "Weiterschauen" ("Continue Watching" in the English UI) and "Meine
  Liste" ("My List" in the English UI). An entry is a saved page.
- The code has exactly these two shelves: `Shelf.CONTINUE_WATCHING` and `Shelf.MY_LIST` in `data.savedpages`, and
  `Shelves` in `domain.savedpages`.
- The browser saves a page in "Weiterschauen" after 10 s of video in full screen. The shelf keeps the last 5
  entries of each site and at most 50 entries.
- The user saves a page in "Meine Liste" by hand, for example with the page menu. The shelf keeps at most 50 entries.
- The app keeps the shelves of each server in the folder of that server.
- The app does not record when a video plays to the end. The browser records only the start and the end of the full
  screen (`WatchRecord` in `BrowserViewModel`).

### Planned behavior

- "Angeschaut" is a list of the films and videos that the user watched to the end.
- "Angeschaut" is a third shelf, next to "Weiterschauen" and "Meine Liste".
- The app keeps "Angeschaut" for each server, like the other two shelves.

### Open questions

- How does the app find out that a video played to the end? This question also applies to a player that the page
  embeds from a different site.
- Is an entry a page (`SavedPageKey`: the site and the normalized URL), or a video on a page?
- Does an entry leave "Weiterschauen" when it goes into "Angeschaut"?
- Where on the start page is the row "Angeschaut"?
- Which limits apply: a maximum number of entries, a limit for each site?
- Does the page menu show the entries of "Angeschaut", like the entries of the other two shelves?
- Can the user remove an entry by hand?

## Ambient mode (planned, scope open)

**Status: planned, scope open.** The owner calls this feature "Ambients". The meaning of this word is not
confirmed. This section contains one decision, the field name of a picture. Everything else is open.

### The app today

- The app has no ambient mode.
- The manifest parser ignores unknown fields. The test `ManifestTest.ignoresUnknownFieldsAndTypes` uses an unknown
  top-level field `ambient` as its example. This test is not a design of ambient mode.

### Decided

- A picture of an ambient entry in the manifest has the field name `banner`, like the picture of a site and of an
  app (Owner, 2026-11-23). See [the banner rule](../example/README.md#banners).

### Open questions

- Is ambient mode an Android TV screensaver (a `DreamService`)?
- Is ambient mode a screen inside the app, for example when nobody pushes a key for some time?
- Does the content come from the catalog, for example ambient videos or images in a new section of the manifest?
- Which content does ambient mode show: videos, images, a clock, or something else?
- When does ambient mode start, and which key stops it?
- Does ambient mode play sound?
- Does the content of ambient mode change for each server, like the shelves?

## How to propose a feature

Open an issue with the feature request template
([`.github/ISSUE_TEMPLATE/feature-request.yml`](../.github/ISSUE_TEMPLATE/feature-request.yml)). Describe the
problem before the solution.

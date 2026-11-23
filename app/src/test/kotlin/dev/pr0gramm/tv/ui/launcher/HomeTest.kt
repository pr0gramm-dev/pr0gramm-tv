package dev.pr0gramm.tv.ui.launcher

import dev.pr0gramm.tv.data.catalogsync.catalogOf
import dev.pr0gramm.tv.data.manifest.Apk
import dev.pr0gramm.tv.data.manifest.CatalogApp
import dev.pr0gramm.tv.data.manifest.Control
import dev.pr0gramm.tv.data.manifest.Layout
import dev.pr0gramm.tv.data.manifest.ServerPath
import dev.pr0gramm.tv.data.manifest.Site
import dev.pr0gramm.tv.data.manifest.SiteId
import dev.pr0gramm.tv.data.manifest.SiteOptions
import dev.pr0gramm.tv.data.manifest.Zoom
import dev.pr0gramm.tv.data.recentlyopened.OpenedKey
import dev.pr0gramm.tv.data.savedpages.SavedPage
import dev.pr0gramm.tv.data.savedpages.SavedPageKey
import dev.pr0gramm.tv.data.savedpages.Shelf
import dev.pr0gramm.tv.domain.appinstall.InstallJob
import dev.pr0gramm.tv.domain.appinstall.InstallQueue
import dev.pr0gramm.tv.domain.savedpages.SavedList
import dev.pr0gramm.tv.domain.savedpages.perSite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeTest {
    private val defaults = SiteOptions(Control.FOCUS, Zoom.of(100)!!, Layout.NORMAL, true, false)

    private fun site(id: String) = Site(
        SiteId(id), id.uppercase(), "https://$id.example/", null, null, emptyList(), null, false, emptySet(),
        defaults, null,
    )

    private fun app(pkg: String, version: Long = 2) =
        CatalogApp(pkg, pkg, null, version, Apk(ServerPath.of("$pkg.apk")!!, "ab".repeat(32), 10))

    private val a = site("a")
    private val b = site("b")
    private val c = site("c")
    private val kodi = app("org.kodi")
    private val vlc = app("org.vlc")
    private val mx = app("org.mx")
    private val catalog = catalogOf(a, b, c, apps = listOf(kodi, vlc, mx))

    private fun entry(site: Site, path: String, savedAt: Long = 1) =
        SavedPage(site.id, "https://${site.id.value}.example/$path", path, null, savedAt, null)

    private fun input(
        favorites: Set<Site> = emptySet(),
        watching: List<SavedPage> = emptyList(),
        myList: List<SavedPage> = emptyList(),
        installed: Map<String, Long> = emptyMap(),
        queue: InstallQueue = InstallQueue(),
    ) = HomeInput(
        catalog.sites.associate { it.id to defaults.copy(favorite = it in favorites) },
        watching,
        myList,
        installed,
        queue,
        emptyMap(),
    )

    private fun build(input: HomeInput = input()) = Home.build(catalog, input)

    private fun HomeState.keys(kind: RowKind) = rows.first { it.kind == kind }.tiles.map { it.key }

    @Test
    fun emptyBlocksAreLeftOut() {
        assertEquals(listOf(RowKind.SITES, RowKind.APPS), build().rows.map { it.kind })
        assertEquals(emptyList<Row>(), Home.build(catalogOf(), input()).rows)
        assertTrue(HomeState.Empty.rows.isEmpty())
    }

    @Test
    fun blocksStandInOrder() {
        val state = build(input(watching = listOf(entry(a, "w")), myList = listOf(entry(b, "m"))))
        assertEquals(
            listOf(RowKind.CONTINUE_WATCHING, RowKind.MY_LIST, RowKind.SITES, RowKind.APPS),
            state.rows.map { it.kind },
        )
    }

    /** The tiles of Weiterschauen when [stored] is the stored list, newest first. */
    private fun watching(vararg stored: SavedPage): List<TileKey> =
        build(input(watching = stored.toList())).keys(RowKind.CONTINUE_WATCHING)

    private fun shown(vararg entries: SavedPage): List<TileKey> =
        entries.map { TileKey.Saved(Shelf.CONTINUE_WATCHING, it.key) }

    @Test
    fun watchingShowsTheNewestThreeOfEachSite() {
        val (a1, a2, a3) = (1..3).map { entry(a, "a$it") }
        val (a4, a5) = (4..5).map { entry(a, "a$it") }
        val (b1, b2) = (1..2).map { entry(b, "b$it") }
        assertEquals(shown(a5, b2, a4, a3, b1), watching(a5, b2, a4, a3, b1, a2, a1))
    }

    @Test
    fun watchingKeepsTheStoredOrder() {
        // Newer savedAt further back: the row follows the stored order, not the TV's clock.
        val (a1, a2, a3) = (1..3).map { entry(a, "a$it", savedAt = 10L - it) }
        val (b1, b2) = (1..2).map { entry(b, "b$it", savedAt = 20L + it) }
        assertEquals(shown(a3, b2, a2, a1, b1), watching(a3, b2, a2, a1, b1))
    }

    @Test
    fun entriesOfASiteMissingFromTheCatalogTakeNoPlace() {
        val gone = site("gone")
        val (g1, g2, g3) = (1..3).map { entry(gone, "g$it") }
        val (a1, a2, a3) = (1..3).map { entry(a, "a$it") }
        assertEquals(shown(a3, a2, a1), watching(g3, a3, g2, a2, g1, a1))
    }

    @Test
    fun entriesWithoutOwnTitleAllAppear() {
        val same = (1..4).map { entry(a, "p$it").copy(title = a.name) }
        assertEquals(shown(same[0], same[1], same[2]), watching(*same.toTypedArray()))
    }

    @Test
    fun watchingHasNoTileLimitBesidesThreePerSite() {
        val sites = (1..6).map { site("s$it") }
        val many = catalogOf(*sites.toTypedArray())
        val options = sites.associate { it.id to defaults }
        val watching = sites.flatMap { s -> (1..4).map { entry(s, "w$it") } }
        val keys = Home.build(many, input(watching = watching).copy(options = options)).keys(RowKind.CONTINUE_WATCHING)
        assertEquals(18, keys.size)
    }

    @Test
    fun removingAShownEntryBringsTheNextStoredOneInAtItsPlace() {
        val visible = setOf(a.id, b.id)
        fun save(list: List<SavedPage>, e: SavedPage) = SavedList.add(list, visible, e, Shelf.CONTINUE_WATCHING.perSite)
        val (a1, a2, a3) = (1..3).map { entry(a, "a$it") }
        val (a4, a5) = (4..5).map { entry(a, "a$it") }
        val b1 = entry(b, "b1")
        val stored = listOf(a1, a2, b1, a3, a4, a5).fold(emptyList(), ::save)
        assertEquals(shown(a5, a4, a3, b1), watching(*stored.toTypedArray()))
        val removed = SavedList.remove(stored, a4.key)
        assertEquals(shown(a5, a3, b1, a2), watching(*removed.toTypedArray()))
        // The same entry saved again replaces the old one and moves to the front.
        assertEquals(shown(a2, a5, a3, b1), watching(*save(removed, a2).toTypedArray()))
    }

    @Test
    fun myListKeepsGivenOrderAndSkipsUnknownSites() {
        val first = entry(b, "1")
        val second = entry(a, "2")
        val state = build(input(myList = listOf(first, entry(site("gone"), "z"), second)))
        assertEquals(
            listOf<TileKey>(TileKey.Saved(Shelf.MY_LIST, first.key), TileKey.Saved(Shelf.MY_LIST, second.key)),
            state.keys(RowKind.MY_LIST),
        )
    }

    @Test
    fun favoritesFirstThenManifestOrder() {
        val keys = build(input(favorites = setOf(c))).keys(RowKind.SITES)
        assertEquals(listOf<TileKey>(TileKey.Site(c.id), TileKey.Site(a.id), TileKey.Site(b.id)), keys)
    }

    @Test
    fun favoritesFirstThenTheLastUsedThenTheRest() {
        val d = site("d")
        val e = site("e")
        val all = listOf(a, b, c, d, e)
        // a and d are favorites (d used later than a), b and e were used (e last), c never.
        val used = mapOf<OpenedKey, Long>(
            OpenedKey.Site(a.id) to 1L,
            OpenedKey.Site(d.id) to 8L,
            OpenedKey.Site(b.id) to 5L,
            OpenedKey.Site(e.id) to 9L,
        )
        val options = all.associate { it.id to defaults.copy(favorite = it == a || it == d) }
        val input = HomeInput(options, emptyList(), emptyList(), emptyMap(), InstallQueue(), used)
        val state = Home.build(catalogOf(*all.toTypedArray()), input)
        val tiles = state.rows.first { it.kind == RowKind.SITES }.tiles.map { it as Tile.Site }
        // Favorites keep the manifest's order; the used ones come newest first; the unused one comes last.
        assertEquals(listOf(a, d, e, b, c), tiles.map { it.site })
        assertEquals(listOf(1L, 8L, 9L, 5L, null), tiles.map { it.usedAt })
        // Without use the order stays the manifest's.
        val manifestOrder = listOf<TileKey>(TileKey.Site(a.id), TileKey.Site(b.id), TileKey.Site(c.id))
        assertEquals(manifestOrder, build().keys(RowKind.SITES))
    }

    @Test
    fun appsUsedLastComeFirstThenTheRest() {
        val used = mapOf<OpenedKey, Long>(OpenedKey.App("org.kodi") to 3L, OpenedKey.App("org.mx") to 7L)
        val keys = build(input(installed = mapOf("org.kodi" to 2L)).copy(used = used)).keys(RowKind.APPS)
        assertEquals(listOf("org.mx", "org.kodi", "org.vlc"), keys.map { (it as TileKey.App).pkg })
    }

    @Test
    fun missingAppsLeftInstalledRightEachInManifestOrder() {
        val keys = build(input(installed = mapOf("org.kodi" to 2L))).keys(RowKind.APPS)
        assertEquals(listOf("org.vlc", "org.mx", "org.kodi"), keys.map { (it as TileKey.App).pkg })
    }

    private fun stateOf(pkg: String, input: HomeInput): AppState {
        val tiles = build(input).rows.first { it.kind == RowKind.APPS }.tiles.filterIsInstance<Tile.App>()
        return tiles.first { it.entry.pkg == pkg }.state
    }

    @Test
    fun appStates() {
        fun state(pkg: String, installed: Long? = null, queue: InstallQueue = InstallQueue()) =
            stateOf(pkg, input(installed = listOfNotNull(installed?.let { pkg to it }).toMap(), queue = queue))
        assertEquals(AppState.Missing, state("org.kodi"))
        assertEquals(AppState.Installed, state("org.kodi", installed = 2))
        assertEquals(AppState.Update, state("org.kodi", installed = 1))
        val running = InstallQueue(running = InstallJob.App(kodi), percent = 40)
        assertEquals(AppState.Running(40), state("org.kodi", queue = running))
        assertEquals(AppState.Waiting, state("org.vlc", queue = running.copy(waiting = listOf(InstallJob.App(vlc)))))
        assertEquals(AppState.Failed, state("org.kodi", queue = InstallQueue(failed = mapOf("org.kodi" to 2L))))
        // A failure of an older versionCode is no failure of this one.
        assertEquals(AppState.Missing, state("org.kodi", queue = InstallQueue(failed = mapOf("org.kodi" to 1L))))
    }

    private val openA = OpenTarget(a.id, null)

    @Test
    fun menuAndPrimaryOfSites() {
        val tile = Tile.Site(a, defaults)
        assertEquals(
            listOf(Action.Open(openA), Action.SetFavorite(a, true), Action.OpenSettings(a)),
            Home.menu(tile),
        )
        assertEquals(Action.SetFavorite(a, false), Home.menu(Tile.Site(a, defaults.copy(favorite = true)))[1])
        assertEquals(Action.Open(openA), Home.primary(tile))
    }

    @Test
    fun menuOfSavedPagesPerShelf() {
        val e = entry(a, "x")
        val open = Action.Open(OpenTarget(a.id, e.url, title = "x"))
        assertEquals(
            listOf(open, Action.AddToMyList(e), Action.Remove(Shelf.CONTINUE_WATCHING, e.key)),
            Home.menu(Tile.Saved(Shelf.CONTINUE_WATCHING, e, a)),
        )
        assertEquals(
            listOf(open, Action.Remove(Shelf.MY_LIST, e.key)),
            Home.menu(Tile.Saved(Shelf.MY_LIST, e, a)),
        )
    }

    @Test
    fun anEntryOpensWithItsTitleAndPictureForTheSplash() {
        val picture = java.io.File("cover.webp")
        val own = entry(a, "x").copy(image = picture)
        assertEquals(
            Action.Open(OpenTarget(a.id, own.url, title = "x", image = picture)),
            Home.primary(Tile.Saved(Shelf.MY_LIST, own, a)),
        )
        // Without an own title the entry is the site's start page: the splash shows the site, not its name twice.
        val start = entry(a, "x").copy(title = a.name)
        assertEquals(
            Action.Open(OpenTarget(a.id, start.url)),
            Home.primary(Tile.Saved(Shelf.CONTINUE_WATCHING, start, a)),
        )
    }

    @Test
    fun aStartPageEntryKeepsItsPictureButNotTheSiteNameAsTitle() {
        // The splash shows the entry's own picture; the name stays on the site, not repeated as a title.
        val picture = java.io.File("start.webp")
        val start = entry(a, "x").copy(title = a.name, image = picture)
        assertEquals(
            Action.Open(OpenTarget(a.id, start.url, title = null, image = picture)),
            Home.primary(Tile.Saved(Shelf.CONTINUE_WATCHING, start, a)),
        )
    }

    @Test
    fun tileKeys() {
        val e = entry(a, "x")
        assertEquals(TileKey.Saved(Shelf.MY_LIST, SavedPageKey.of(a.id, e.url)), Tile.Saved(Shelf.MY_LIST, e, a).key)
    }
}

package dev.pr0gramm.tv.ui.launcher.settings

import dev.pr0gramm.tv.data.catalogsync.ServerAddress
import dev.pr0gramm.tv.domain.selfupdate.UpdateStatus
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * A general page before any check: app version "1.0 (7)", catalog 3 of 2026-11-18, server "tv.example", in UTC.
 * Tests change it with `copy`.
 */
internal val GENERAL_INFO = GeneralInfo(
    status = UpdateStatus.Idle,
    checking = false,
    appVersion = "1.0 (7)",
    catalog = CatalogStamp(3, LocalDate.of(2026, 11, 18)),
    server = ServerAddress.parse("tv.example"),
    checkedAt = null,
    zone = ZoneOffset.UTC,
)

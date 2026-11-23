package dev.pr0gramm.tv.domain.selfupdate

import java.io.File

/** E2: the own update at [versionCode] is downloaded and checked to [apk]; it goes into the install queue. */
data class SelfUpdateReady(val versionCode: Long, val apk: File)

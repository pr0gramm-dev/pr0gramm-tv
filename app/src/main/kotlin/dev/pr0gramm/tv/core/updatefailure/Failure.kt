package dev.pr0gramm.tv.core.updatefailure

/** Why an update did not happen. [CANCELLED] is the user saying no in Android; it is no error. */
enum class Failure { UNREACHABLE, BAD_INDEX, BROKEN_APK, OTHER_KEY, INSTALL_FAILED, CANCELLED }

/** Thrown on the way of an update or a download; the updater turns it into a failed status. */
internal class UpdateFailure(val failure: Failure, detail: String? = null, cause: Throwable? = null) :
    Exception(if (detail == null) failure.name else "$failure $detail", cause)

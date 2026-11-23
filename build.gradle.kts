plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
    // Puts KGP at the catalog's Kotlin version on the classpath, so AGP's built-in Kotlin uses it.
    alias(libs.plugins.kotlin.android) apply false
}

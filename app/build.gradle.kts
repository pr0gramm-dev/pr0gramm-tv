import dev.pr0gramm.tv.core.appversion.AppVersion

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    id("pr0gramm.blocklist")
    id("pr0gramm.quality")
}

// Release key (mise run deploy / release): -PreleaseSigned=true builds the release APK unsigned; the task signs it
// afterwards with apksigner and the release key from RELEASE_KEY_COMMAND or RELEASE_SIGNING_KEY (mise-tasks/sign),
// so Gradle never sees the key. Without it the release build is signed with the debug key and warns (AC9a).
val releaseSigned: Boolean = providers.gradleProperty("releaseSigned").orNull.toBoolean()

// -PappVersion=MAJOR.MINOR.PATCH (AC10): the release tag vMAJOR.MINOR.PATCH, or MAJOR.MINOR.PATCH-nightly.N for a
// nightly build; default 0.0.1. AppVersion (core.appversion, compiled into build-logic) gives versionCode and
// versionName, with the same rule the self-update uses (docs/release.md, "Version code").
val appVersion: AppVersion = AppVersion.parse(providers.gradleProperty("appVersion").getOrElse("0.0.1"))
    ?: throw GradleException(
        "-PappVersion must be MAJOR.MINOR.PATCH or MAJOR.MINOR.PATCH-nightly.N (MINOR, PATCH ≤ 99; N 1 to 999)",
    )

// Self-update (AC6): -PupdateRepo=owner/repo names the GitHub repo whose latest release the app reads, default
// pr0gramm-dev/pr0gramm-tv. A release build gets the repo of its workflow (mise-tasks/stage). UpdateRepo
// (build-logic) holds the form rule and its test.
val updateRepo: String = providers.gradleProperty("updateRepo").getOrElse(UpdateRepo.DEFAULT).also {
    if (!UpdateRepo.valid(it)) {
        throw GradleException("-PupdateRepo muss owner/repo sein, ist aber \"$it\".")
    }
}

fun quoted(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "dev.pr0gramm.tv"
    compileSdk = 37
    defaultConfig {
        applicationId = "dev.pr0gramm.tv"
        // 33: LocaleManager and android:localeConfig for the app language (localisation spec, AC1).
        minSdk = 33
        targetSdk = 34
        versionCode = appVersion.code.toInt()
        versionName = appVersion.toString()
        resValue("string", "app_name", "pr0gramm")
        buildConfigField("boolean", "RELEASE_SIGNED", "false")
        buildConfigField("String", "UPDATE_REPO", quoted(updateRepo))
    }
    buildFeatures {
        compose = true
        resValues = true
        buildConfig = true
    }
    // The golden blocklist asset of build-logic is the contract the app's Blocklist reads (AC6).
    sourceSets.getByName("test").resources.srcDir(rootProject.file("build-logic/src/test/resources"))
    buildTypes {
        release {
            // Only a release-key build can be replaced by an APK from the store.
            buildConfigField("boolean", "RELEASE_SIGNED", releaseSigned.toString())
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // null: the output is app-release-unsigned.apk, which mise-tasks/sign signs.
            signingConfig = if (releaseSigned) null else signingConfigs.getByName("debug")
        }
    }
}

if (!releaseSigned) {
    tasks.matching { it.name == "packageRelease" }.configureEach {
        doFirst {
            logger.warn("WARNUNG: ohne -PreleaseSigned=true ist das Release-APK mit dem Debug-Schlüssel signiert.")
        }
    }
}

// A failing unit test prints its name and full stack trace, also under --quiet (mise run test, CI): the CI job keeps
// no test report, so the log is the only place to see which test failed.
tasks.withType<Test>().configureEach {
    testLogging {
        quiet {
            events(org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED)
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.tv.material)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.datastore)
    implementation(libs.datastore.preferences)
    implementation(libs.coil.compose)
    implementation(libs.webkit)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // Android's org.json is a stub in unit tests; the real one runs on the JVM.
    testImplementation(libs.json)
}

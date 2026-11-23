plugins {
    `kotlin-dsl`
}

kotlin {
    jvmToolchain(17)
    // The app's manifest model and parser (AC2), so the build checks a manifest exactly as the TV does, and the
    // app's version rule (AC10), so app/build.gradle.kts derives versionCode as the self-update does. Both
    // directories are pure Kotlin: an Android import there fails this compile.
    sourceSets.main {
        kotlin.srcDir("../app/src/main/kotlin/dev/pr0gramm/tv/data/manifest")
        kotlin.srcDir("../app/src/main/kotlin/dev/pr0gramm/tv/core/appversion")
    }
}

dependencies {
    implementation("com.google.code.gson:gson:2.11.0")
    runtimeOnly(kotlin("stdlib")) // for checkManifest, which runs the classes outside Gradle
    implementation(libs.detekt.gradle.plugin)
    compileOnly("com.android.tools.build:gradle-api:${libs.versions.agp.get()}")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

gradlePlugin {
    plugins {
        register("quality") {
            id = "pr0gramm.quality"
            implementationClass = "QualityPlugin"
        }
        register("blocklist") {
            id = "pr0gramm.blocklist"
            implementationClass = "BlocklistPlugin"
        }
    }
}

tasks.test { useJUnitPlatform() }

// `./gradlew -p build-logic checkManifest -Pmanifest=<file> [-PskipApks]`: the parser plus the files the manifest names.
tasks.register<JavaExec>("checkManifest") {
    group = "verification"
    description = "Checks a manifest.json: parser, referenced files, APK sha256 and size."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "ManifestTasks"
    val manifest = providers.gradleProperty("manifest")
    val skipApks = providers.gradleProperty("skipApks").isPresent
    argumentProviders.add(CommandLineArgumentProvider {
        listOfNotNull(
            manifest.orNull?.let { File(it).let { f -> if (f.isAbsolute) f else File(rootDir.parentFile, it) }.path }
                ?: throw GradleException("-Pmanifest=<Datei> fehlt"),
            "--skip-apks".takeIf { skipApks },
        )
    })
}

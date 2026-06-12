import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.gradle.api.tasks.bundling.Compression
import org.gradle.api.tasks.bundling.Tar

val softMusicPackageVersion = "1.1.1"

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("com.github.hypfvieh:dbus-java-core:5.2.0")
    implementation("com.github.hypfvieh:dbus-java-transport-native-unixsocket:5.2.0")
    implementation("net.jthink:jaudiotagger:3.0.1")
}

compose.desktop {
    application {
        mainClass = "com.softmusic.app.desktop.MainKt"
        jvmArgs += listOf("-Dsun.awt.X11.XWMClass=com-softmusic-app-desktop-MainKt")

        nativeDistributions {
            targetFormats(TargetFormat.AppImage, TargetFormat.Deb, TargetFormat.Rpm)
            packageName = "SoftMusic"
            packageVersion = softMusicPackageVersion
            modules("jdk.security.auth")
            linux {
                iconFile.set(project.file("src/main/resources/favicon.png"))
            }
        }
    }
}

tasks.register<Tar>("packagePortableLinux") {
    group = "distribution"
    description = "Builds a portable Linux x86_64 tar.gz distribution."
    dependsOn("createDistributable")

    archiveFileName.set("softmusic-$softMusicPackageVersion-linux-x86_64.tar.gz")
    destinationDirectory.set(layout.buildDirectory.dir("compose/binaries/main/portable"))
    compression = Compression.GZIP

    from(layout.buildDirectory.dir("compose/binaries/main/app/SoftMusic")) {
        into("SoftMusic")
    }
    eachFile {
        if (
            path == "SoftMusic/bin/SoftMusic" ||
            path == "SoftMusic/lib/runtime/lib/jexec" ||
            path == "SoftMusic/lib/runtime/lib/jspawnhelper"
        ) {
            permissions {
                unix("755")
            }
        }
    }
}

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    alias(libs.plugins.ktlint)
    application
}

group = "me.chosante"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

// Reuses autobuilder's committed equipments.json (and its sibling resource files, harmlessly)
// without a project dependency on :autobuilder -- that would drag in OR-Tools' ~100 native
// dylibs and Clikt just to read one JSON file. See EquipmentCatalog.kt.
sourceSets {
    main {
        resources {
            srcDir("../autobuilder/src/main/resources")
        }
    }
}

dependencies {
    implementation(project(":common-lib"))
    implementation(libs.bundles.ktor.server)
    implementation(libs.bundles.exposed)
    implementation(libs.sqlite.jdbc)
    implementation(libs.kotlinx.serialization.json)
    implementation("org.slf4j:slf4j-api:2.0.18")
    implementation("org.apache.logging.log4j:log4j-slf4j2-impl:2.26.1")
    testImplementation(libs.ktor.server.test.host)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    jvmToolchain(
        libs.versions.jvm
            .get()
            .toInt()
    )
}

application {
    mainClass.set("me.chosante.marketserver.MainKt")
}

tasks.test {
    useJUnitPlatform()
    // sqlite-jdbc loads its native library via JNI; silence JDK 25's restricted-method warning
    // the same way autobuilder's OR-Tools tests already do (root build.gradle.kts only covers
    // JavaExec tasks, not Test tasks).
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

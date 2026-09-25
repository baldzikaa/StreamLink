plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.shadow)
    alias(libs.plugins.run.paper)
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
}

kotlin {
    jvmToolchain(25)
}

dependencies {
    compileOnly(libs.paper.api)
    implementation(libs.tiktok) {
        exclude(group = "org.slf4j")
        exclude(group = "com.google.code.gson")
    }

    testImplementation(libs.paper.api)
    testImplementation(kotlin("test"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

tasks {
    test {
        useJUnitPlatform()
    }

    processResources {
        val props = mapOf("version" to project.version, "kotlin" to libs.versions.kotlin.get())
        inputs.properties(props)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }

    shadowJar {
        archiveClassifier = ""
        dependencies {
            // paper downloads kotlin itself, see libraries in plugin.yml
            exclude(dependency("org.jetbrains.kotlin:.*"))
            exclude(dependency("org.jetbrains:annotations"))
        }
        val libs = "win.baldzika.streamlink.libs"
        relocate("io.github.jwdeveloper", "$libs.tiktok")
        relocate("com.google.protobuf", "$libs.protobuf")
        relocate("org.java_websocket", "$libs.websocket")
        mergeServiceFiles()
        exclude("META-INF/maven/**", "google/protobuf/**")
    }

    build {
        dependsOn(shadowJar)
    }

    runServer {
        minecraftVersion("26.1.2")
    }
}

runPaper.folia.registerTask()

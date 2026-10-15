plugins {
    id("java-library")
    id("com.gradleup.shadow") version "9.6.1"
    id("xyz.jpenilla.run-paper") version "3.1.0"
    id("com.diffplug.spotless") version "6.25.0"
}

repositories {
    gradlePluginPortal()
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://oss.sonatype.org/content/groups/public/")
    maven("https://hub.spigotmc.org/nexus/content/repositories/public/")
}

sourceSets {
    create("legacy") {
        java.srcDir("src/legacy/java")
        compileClasspath += sourceSets.main.get().output
    }
    create("modern") {
        java.srcDir("src/modern/java")
        compileClasspath += sourceSets.main.get().output
    }
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:1.8.8-R0.1-SNAPSHOT")
    add("legacyCompileOnly", "org.spigotmc:spigot-api:1.8.8-R0.1-SNAPSHOT")
    add("modernCompileOnly", "org.spigotmc:spigot-api:26.3-R0.1-SNAPSHOT")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

spotless {
    java {
        googleJavaFormat("1.28.0").aosp()
        target(
            "src/main/java/**/*.java",
            "src/legacy/java/**/*.java",
            "src/modern/java/**/*.java",
        )
        removeUnusedImports()
        formatAnnotations()
        trimTrailingWhitespace()
        toggleOffOn("// @formatter:off", "// @formatter:on")
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(if (name == "compileModernJava") 17 else 8)
}

tasks {
    jar {
        from(sourceSets["legacy"].output)
        from(sourceSets["modern"].output)
    }

    build {
        dependsOn(shadowJar)
    }

    shadowJar {
        from(sourceSets["legacy"].output)
        from(sourceSets["modern"].output)
        // relocate("com.fasterxml.jackson", "me.pintoadmin.fabricmoddownloader.shaded.com.fasterxml.jackson")
        // relocate("org.yaml.snakeyaml", "me.pintoadmin.fabricmoddownloader.shaded.org.yaml.snakeyaml")
        archiveClassifier.set("")
    }

    compileJava {
        dependsOn("spotlessApply")
    }

    runServer {
        minecraftVersion("26.3")
    }

    processResources {
        val props = mapOf("version" to version)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}

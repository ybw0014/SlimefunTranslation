import net.minecrell.pluginyml.bukkit.BukkitPluginDescription

plugins {
    id("java")
    id("maven-publish")
    id("com.gradleup.shadow") version "8.3.3"
    id("io.freefair.lombok") version "8.10.2"
    id("net.minecrell.plugin-yml.bukkit") version "0.6.0"
    id("xyz.jpenilla.run-paper") version "2.3.1"
}

group = "net.guizhanss"
version = "UNOFFICIAL"
description = "SlimefunTranslation"

val mainPackage = "net.guizhanss.slimefuntranslation"

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://s01.oss.sonatype.org/content/repositories/snapshots/")
    maven("https://jitpack.io")
    maven("https://repo.dmulloy2.net/repository/public/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:1.20.4-experimental-SNAPSHOT")
    compileOnly("com.github.Slimefun:Slimefun4:RC-37")
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("com.comphenix.protocol:ProtocolLib:5.1.0")
    implementation("net.guizhanss:guizhanlib-all:2.1.0")
    implementation("org.bstats:bstats-bukkit:3.1.0")
}

java {
    sourceCompatibility = JavaVersion.VERSION_16
}

tasks.compileJava {
    options.encoding = "UTF-8"
}

tasks.javadoc {
    options.encoding = "UTF-8"
}

tasks.shadowJar {
    fun doRelocate(from: String) {
        val last = from.split(".").last()
        relocate(from, "$mainPackage.libs.$last")
    }

    doRelocate("net.guizhanss.guizhanlib")
    doRelocate("org.bstats")
    minimize()
    archiveClassifier = ""
}

bukkit {
    main = "net.guizhanss.slimefuntranslation.SlimefunTranslation"
    apiVersion = "1.16"
    authors = listOf("ybw0014")
    description = "A Slimefun Addon that translates items without actually modifying the items."
    depend = listOf("Slimefun", "ProtocolLib", "PlaceholderAPI")
    softDepend = listOf("GuizhanLibPlugin")

    commands {
        register("sftranslation") {
            description = "SlimefunTranslation command"
            aliases = listOf("slimefuntranslation", "sft", "sftr", "sftransl")
        }
    }

    permissions {
        register("sftranslation.command.id") {
            description = "Get the ID of the Slimefun item in your main hand"
            default = BukkitPluginDescription.Permission.Default.TRUE
        }
        register("sftranslation.command.search") {
            description = "Search for a Slimefun item with your current language"
            default = BukkitPluginDescription.Permission.Default.TRUE
        }
        register("sftranslation.command.translation.extract") {
            description = "Extract the translation files to the \"translations\" folder"
            default = BukkitPluginDescription.Permission.Default.OP
        }
        register("sftranslation.command.translation.generate") {
            description = "Generate a translation file based on the given addon and language."
            default = BukkitPluginDescription.Permission.Default.OP
        }
        register("sftranslation.command.translation.reload") {
            description = "Reload the translation files."
            default = BukkitPluginDescription.Permission.Default.OP
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}

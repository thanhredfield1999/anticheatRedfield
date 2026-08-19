plugins {
    java
}

allprojects {
    group = "vn.heomc.anticheat"
    version = "0.0.1-spike"
}

subprojects {
    apply(plugin = "java")

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
    }

    java {
        toolchain.languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.named("test") {
    enabled = false
}

project(":paper-spike") {
    tasks.processResources {
        filesMatching("plugin.yml") {
            expand("version" to project.version)
        }
    }

    dependencies {
        compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    }
}

project(":packetevents-spike") {
    tasks.processResources {
        filesMatching("plugin.yml") {
            expand("version" to project.version)
        }
    }

    repositories {
        maven("https://repo.codemc.io/repository/maven-releases/")
        maven("https://repo.codemc.io/repository/maven-snapshots/")
    }

    dependencies {
        compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
        compileOnly("com.github.retrooper:packetevents-spigot:2.13.0")
    }
}

project(":protocollib-spike") {
    tasks.processResources {
        filesMatching("plugin.yml") {
            expand("version" to project.version)
        }
    }

    dependencies {
        compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
        compileOnly("net.dmulloy2:ProtocolLib:5.4.0")
    }
}

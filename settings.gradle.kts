pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://repo.codemc.io/repository/maven-releases/")
    }
}
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://repo.codemc.io/repository/maven-releases/")
    }
}
rootProject.name = "heomc-anticheat-plugin"
include(":paper-spike", ":packetevents-spike", ":protocollib-spike", ":anticheat-plugin")
project(":paper-spike").projectDir = file("spikes/paper-spike")
project(":packetevents-spike").projectDir = file("spikes/packetevents-spike")
project(":protocollib-spike").projectDir = file("spikes/protocollib-spike")
project(":anticheat-plugin").projectDir = file("anticheat-plugin")

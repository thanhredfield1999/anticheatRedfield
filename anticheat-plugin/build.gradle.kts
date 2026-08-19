plugins { java }

group = "vn.heomc.anticheat"
version = "0.1.0-mvp"

java { toolchain.languageVersion = JavaLanguageVersion.of(21) }

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.codemc.io/repository/maven-releases/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("com.github.retrooper:packetevents-spigot:2.13.0")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test { useJUnitPlatform() }
tasks.processResources { filesMatching("plugin.yml") { expand("version" to project.version) } }

// PacketEvents chỉ là adapter tùy chọn; core không phụ thuộc API packet.

tasks.jar { archiveBaseName.set("HybridAnticheat") }

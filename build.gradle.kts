plugins {
    java
    id("xyz.jpenilla.run-paper") version "3.1.0"
}

group = "com.junseo"
version = "0.2.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc"
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:${property("paperApiVersion")}")

    // DB 저장소 테스트용 (서버에서는 Paper 에 들어 있는 드라이버를 씁니다)
    testImplementation("org.xerial:sqlite-jdbc:3.49.1.0")
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks {
    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(25)
    }

    processResources {
        val props = mapOf("version" to project.version)
        inputs.properties(props)
        filteringCharset = "UTF-8"
        filesMatching("paper-plugin.yml") {
            expand(props)
        }
    }

    jar {
        archiveFileName.set("JunseoCity-${project.version}.jar")
    }

    test {
        useJUnitPlatform()
    }

    // ./gradlew runServer : Paper 서버를 내려받아 이 플러그인을 넣고 바로 실행합니다.
    runServer {
        minecraftVersion(property("minecraftVersion") as String)
    }
}

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
    // 도시 설계도 계산 (미니맵·지도에 씀, jar 안에 함께 넣음)
    implementation(project(":citymap"))

    // DB 저장소 테스트용 (서버에서는 Paper 에 들어 있는 드라이버를 씁니다)
    testImplementation("org.xerial:sqlite-jdbc:3.49.1.0")
    // 미니맵 테스트에서 설계도(layout.json)를 읽을 때 씀 (서버에서는 Paper 에 들어 있음)
    testImplementation("com.google.code.gson:gson:2.14.0")
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
        // citymap 모듈(과 설계도 layout.json)을 jar 안에 합칩니다
        dependsOn(configurations.runtimeClasspath)
        from({ configurations.runtimeClasspath.get().filter { it.name.endsWith(".jar") }.map { zipTree(it) } })
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }

    test {
        useJUnitPlatform()
        // ./gradlew test -DmapPreview=true  →  build/preview/ 에 미니맵·전체 지도 미리보기 PNG
        systemProperty("mapPreview", System.getProperty("mapPreview") ?: "false")
        systemProperty("layoutFile", rootProject.file("map/layout.json").absolutePath)
    }

    // ./gradlew runServer : Paper 서버를 내려받아 이 플러그인을 넣고 바로 실행합니다.
    runServer {
        minecraftVersion(property("minecraftVersion") as String)
    }
}

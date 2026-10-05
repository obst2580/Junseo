plugins {
    java
}

group = "com.junseo"
version = "0.1.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc"
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:${property("paperApiVersion")}")

    // 지형 계산은 서버 없이 테스트합니다 (layout.json 읽기에 Gson 사용)
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
        filesMatching("plugin.yml") {
            expand(props)
        }
        // 설계도 데이터를 플러그인 안에 넣습니다
        from(rootProject.file("map/layout.json"))
    }

    jar {
        archiveFileName.set("JunseoMapGen-${project.version}.jar")
    }

    test {
        useJUnitPlatform()
        // ./gradlew :mapgen:test -DmapPreview=true  →  mapgen/build/preview/ 에 지도 미리보기 PNG
        systemProperty("mapPreview", System.getProperty("mapPreview") ?: "false")
        systemProperty("layoutFile", rootProject.file("map/layout.json").absolutePath)
        maxHeapSize = "1g"
    }
}

plugins {
    `java-library`
}

group = "com.junseo"
version = "0.2.0"

repositories {
    mavenCentral()
}

// 도시 설계도(map/layout.json) 계산 공용 모듈.
// 마인크래프트와 상관없는 순수 Java 라서 생성기(mapgen)와 본 플러그인(미니맵)이 같이 씁니다.
dependencies {
    // Gson 은 Paper 서버에 들어 있으므로 컴파일할 때만 씁니다
    compileOnly("com.google.code.gson:gson:2.14.0")

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
        // 설계도 데이터를 같이 넣어서, 이 모듈을 쓰는 플러그인 jar 안에 들어가게 합니다
        from(rootProject.file("map/layout.json"))
    }

    test {
        useJUnitPlatform()
        // ./gradlew :citymap:test -DmapPreview=true  →  citymap/build/preview/ 에 지도 미리보기 PNG
        systemProperty("mapPreview", System.getProperty("mapPreview") ?: "false")
        systemProperty("previewOnly", System.getProperty("previewOnly") ?: "")
        systemProperty("layoutFile", rootProject.file("map/layout.json").absolutePath)
        maxHeapSize = (project.findProperty("testHeap") as String?) ?: "1g"
    }
}

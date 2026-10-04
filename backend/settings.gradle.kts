// repo1 is the same Maven Central content; repo.maven.apache.org rate-limits intermittently from some networks.
pluginManagement {
    repositories {
        maven("https://repo1.maven.org/maven2")
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        maven("https://repo1.maven.org/maven2")
        mavenCentral()
    }
}

rootProject.name = "junseo-backend"

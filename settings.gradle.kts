pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Appodeal's SDK and network adapters (google-ads-exit Stage B), and
        // only those: the content filter keeps every other artifact resolving
        // from Google and Maven Central, so a typo can't pull a look-alike
        // from this repository. The adapters' own network SDKs resolve from
        // Maven Central or from here (Appodeal mirrors the ones that publish
        // nowhere public).
        maven("https://artifactory.appodeal.com/appodeal") {
            content {
                includeGroupByRegex("com\\.appodeal.*")
                includeGroupByRegex("com\\.explorestack.*")
                includeGroupByRegex("io\\.bidmachine.*")
                includeGroupByRegex("com\\.my\\.target.*")
                includeGroupByRegex("com\\.mbridge.*")
                includeGroupByRegex("com\\.fyber.*")
                includeGroupByRegex("com\\.unity3d.*")
                includeGroupByRegex("com\\.vungle.*")
                includeGroupByRegex("com\\.applovin.*")
                includeGroupByRegex("com\\.inmobi\\.monetization.*")
            }
        }
    }
}

rootProject.name = "JustMyWeather"
include(":app")
include(":shared")

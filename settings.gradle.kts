val offlineMavenRepo = (providers.gradleProperty("offlineMavenRepo")
    .orElse(providers.environmentVariable("OFFLINE_MAVEN_REPO"))
    .orElse("D:/Android/maven_repo_quicksearch"))
    .get()

pluginManagement {
    val offlineMavenRepo = (providers.gradleProperty("offlineMavenRepo")
        .orElse(providers.environmentVariable("OFFLINE_MAVEN_REPO"))
        .orElse("D:/Android/maven_repo_quicksearch"))
        .get()
    repositories {
        maven { url = uri(offlineMavenRepo) }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri(offlineMavenRepo) }
        google()
        mavenCentral()
    }
}

rootProject.name = "DreamQuickSearch"
include(":app")
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
}

// Collect deps: gradlew collectToOfflineMaven -PofflineMavenRepo=D:/Android/maven_repo_quicksearch
apply(from = "offline-maven.gradle")


plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.coui.appcompat"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("proguard-consumer.flags")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    sourceSets {
        named("main") {
            manifest.srcFile("AndroidManifest.xml")
            java.srcDirs("java")
            kotlin.srcDirs("java")
            res.srcDirs("res")
            assets.srcDirs("assets")
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    api("androidx.appcompat:appcompat:1.7.0")
    api("androidx.activity:activity:1.9.3")
    api("androidx.annotation:annotation:1.8.2")
    api("androidx.constraintlayout:constraintlayout:2.1.4")
    api("androidx.coordinatorlayout:coordinatorlayout:1.2.0")
    api("androidx.core:core-ktx:1.13.1")
    api("androidx.customview:customview:1.1.0")
    api("androidx.dynamicanimation:dynamicanimation:1.0.0")
    api("androidx.fragment:fragment:1.8.5")
    api("androidx.lifecycle:lifecycle-common:2.8.7")
    api("androidx.lifecycle:lifecycle-runtime:2.8.7")
    api("androidx.lifecycle:lifecycle-viewmodel:2.8.7")
    api("androidx.preference:preference:1.2.1")
    api("androidx.recyclerview:recyclerview:1.3.2")
    api("androidx.savedstate:savedstate:1.2.1")
    api("androidx.viewpager:viewpager:1.0.0")
    api("androidx.viewpager2:viewpager2:1.1.0")
    api("com.google.android.material:material:1.12.0")
    api("com.airbnb.android:lottie:6.6.2")
    api("com.squareup.okio:okio:3.9.1")
    api(files("libs/rebound-0.3.8.jar"))
}

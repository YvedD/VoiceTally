// app/build.gradle.kts
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.yvesds.vt5"
    compileSdk = 35
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "com.yvesds.vt5"
        minSdk = 33
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug { isMinifyEnabled = false }
    }

    buildFeatures {
        viewBinding = true
        compose = false
        buildConfig = true
    }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    sourceSets {
        named("main") {
            kotlin.directories += "build/generated/ksp/main/kotlin"
        }
        named("debug") {
            kotlin.directories += "build/generated/ksp/debug/kotlin"
        }
    }
}

// KSP configuration (outside android block)
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("androidx.fragment:fragment-ktx:1.8.4")

    implementation("com.google.android.material:material:1.12.0")
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // Flexbox voor wrap-content tiles in meerdere kolommen
    implementation("com.google.android.flexbox:flexbox:3.0.0")

    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-cbor:1.7.3")

    implementation("commons-codec:commons-codec:1.17.1")
    implementation("org.apache.commons:commons-text:1.12.0")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.8.1")

    implementation("androidx.media:media:1.7.1")

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // DataStore
    implementation(libs.androidx.datastore.preferences)
}

// Tests uit
tasks.matching {
    it.name.startsWith("test", true) || it.name.startsWith("connectedAndroidTest", true)
}.configureEach { this.enabled = false }

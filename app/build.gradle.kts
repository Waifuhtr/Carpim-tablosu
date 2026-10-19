plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// CI'da her derleme bir öncekinden büyük versionCode alır; böylece APK üstüne kurulabilir.
val ciBuildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "com.carpimtablosu.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.carpimtablosu.app"
        minSdk = 26
        targetSdk = 35
        versionCode = ciBuildNumber
        versionName = "1.0.$ciBuildNumber"
        // Uygulama yalnızca Türkçe; kütüphanelerin diğer dil dosyaları APK'ya girmez
        resourceConfigurations += "tr"
    }

    signingConfigs {
        // Depoda sabit tutulan anahtar: her CI derlemesi aynı imzayı taşır,
        // böylece yeni APK eskisini silmeden güncelleme olarak kurulabilir.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        create("release") {
            val ks = System.getenv("RELEASE_KEYSTORE_PATH")
            if (ks != null) {
                storeFile = file(ks)
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            } else {
                storeFile = file("debug.keystore")
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("debug")
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                // Her test sınıfı ayrı JVM'de: Robolectric testleri arasında biriken durum
                // (ör. Compose testlerinin hiç durulmaması) sonraki testleri etkilemesin
                it.forkEvery = 1
                // Ekran görüntüsü üretmek için: ./gradlew testDebugUnitTest -Pshots
                it.systemProperty("shots", if (project.hasProperty("shots")) "true" else "false")
                it.systemProperty("roborazzi.test.record", if (project.hasProperty("shots")) "true" else "false")
                it.systemProperty("shots.dir", rootProject.file("screenshots").absolutePath)
            }
        }
    }
    lint {
        abortOnError = true
        checkReleaseBuilds = false
        // Bağımlılık sürümleri bilinçli olarak sabit tutuluyor
        disable += "GradleDependency"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core-ktx:1.6.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.39.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.39.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

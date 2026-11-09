plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.github.rvonbergen84.tvlauncher"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.rvonbergen84.tvlauncher"
        minSdk = 24
        targetSdk = 35
        versionCode = 12
        versionName = "0.12.0"
    }

    // One shared signing key, kept in the repo, so a build from any machine
    // can install over the previous one on the TV. It is not a secret.
    signingConfigs {
        create("shared") {
            storeFile = rootProject.file("keystore/dev.jks")
            storePassword = "tvlauncher"
            keyAlias = "dev"
            keyPassword = "tvlauncher"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("shared")
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
                // Preview images are written to app/build/previews/ by HomePreviewTest.
                it.systemProperty("roborazzi.test.record", "true")
            }
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.tv:tv-material:1.0.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Screen previews rendered on the JVM: ./gradlew testDebugUnitTest --tests '*HomePreviewTest*'
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.32.2")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.32.2")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

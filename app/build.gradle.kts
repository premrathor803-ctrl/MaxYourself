plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.elevate.launcher"
    compileSdk = 34

    signingConfigs {
        // A fixed key (committed on purpose) so every CI build is signed the same way and
        // new APKs install as an update over older ones. Personal sideload use only.
        create("elevate") {
            storeFile = file("elevate.keystore")
            storePassword = "elevate123"
            keyAlias = "elevate"
            keyPassword = "elevate123"
        }
    }

    defaultConfig {
        applicationId = "com.elevate.launcher"
        minSdk = 24
        targetSdk = 34
        versionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1
        versionName = "2.0.0-alpha"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("elevate")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("elevate")
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
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.fragment:fragment-ktx:1.8.1")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    testImplementation("junit:junit:4.13.2")
}

plugins { id("com.android.application") }

android {
    namespace = "il.co.hebrewmapsoffline"
    compileSdk = 35

    defaultConfig {
        applicationId = "il.co.hebrewmapsoffline"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            // Signed with the debug key for direct testing/sideloading; create a private release key for store distribution.
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("org.mapsforge:mapsforge-map-android:0.25.0")
    implementation("org.mapsforge:mapsforge-map:0.25.0")
    implementation("org.mapsforge:mapsforge-core:0.25.0")
}

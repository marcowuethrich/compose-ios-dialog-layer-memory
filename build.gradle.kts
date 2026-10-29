plugins {
    kotlin("multiplatform") version "2.4.20"
    kotlin("plugin.compose") version "2.4.20"
}

val composeVersion = providers.gradleProperty("composeVersion").get()

kotlin {
    iosSimulatorArm64 {
        compilations.getByName("main").cinterops.create("metaltextures")
        binaries.executable {
            entryPoint = "repro.main"
            baseName = "DialogLayerRepro"
        }
    }
    sourceSets {
        iosMain.dependencies {
            implementation("org.jetbrains.compose.foundation:foundation:$composeVersion")
        }
    }
}

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
    }
    versionCatalogs {
        create("libs") {
            version("agp", "8.7.3")
            version("kotlin", "2.0.21")
            version("coreKtx", "1.15.0")
            version("lifecycle", "2.8.7")
            version("activityCompose", "1.9.3")
            version("composeBom", "2024.12.01")
            version("datastore", "1.1.1")
            version("gson", "2.11.0")

            library("core-ktx", "androidx.core", "core-ktx").versionRef("coreKtx")
            library("lifecycle-runtime-ktx", "androidx.lifecycle", "lifecycle-runtime-ktx").versionRef("lifecycle")
            library("lifecycle-viewmodel-compose", "androidx.lifecycle", "lifecycle-viewmodel-compose").versionRef("lifecycle")
            library("activity-compose", "androidx.activity", "activity-compose").versionRef("activityCompose")
            library("compose-bom", "androidx.compose", "compose-bom").versionRef("composeBom")
            library("compose-ui", "androidx.compose.ui", "ui").withoutVersion()
            library("compose-ui-graphics", "androidx.compose.ui", "ui-graphics").withoutVersion()
            library("compose-ui-tooling-preview", "androidx.compose.ui", "ui-tooling-preview").withoutVersion()
            library("compose-material3", "androidx.compose.material3", "material3").withoutVersion()
            library("compose-material-icons-extended", "androidx.compose.material", "material-icons-extended").withoutVersion()
            library("datastore-preferences", "androidx.datastore", "datastore-preferences").versionRef("datastore")
            library("gson", "com.google.code.gson", "gson").versionRef("gson")

            plugin("android-application", "com.android.application").versionRef("agp")
            plugin("kotlin-android", "org.jetbrains.kotlin.android").versionRef("kotlin")
            plugin("kotlin-compose", "org.jetbrains.kotlin.plugin.compose").versionRef("kotlin")
        }
    }
}

rootProject.name = "AutoClicker"
include(":app")

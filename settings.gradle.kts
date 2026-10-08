import java.util.Locale

// Force US Locale and UTF-8 across the Gradle daemon to prevent Room/KSP from emitting Arabic-Indic numerals
Locale.setDefault(Locale.US)
System.setProperty("user.language", "en")
System.setProperty("user.country", "US")
System.setProperty("file.encoding", "UTF-8")

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

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
  }
}

rootProject.name = "SamMikrotik"

include(":app")

import java.util.Locale

Locale.setDefault(Locale.US)
System.setProperty("user.language", "en")
System.setProperty("user.country", "US")
System.setProperty("file.encoding", "UTF-8")

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.google.devtools.ksp) apply false
  alias(libs.plugins.secrets) apply false
  alias(libs.plugins.google.services) apply false
}

pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositories { google(); mavenCentral() }
}
rootProject.name = "JustInTime"

include(":shared")       // regras, banco de dados e telas (Android + PC)
include(":androidApp")   // o app Android: alarmes, notificações, widget

// Kit visual da NokyLabs (submodule em lib/NokyLabs.UI)
include(":nokylabs-ui")
project(":nokylabs-ui").projectDir = file("lib/NokyLabs.UI/compose/nokylabs-ui")

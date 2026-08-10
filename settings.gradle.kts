import org.gradle.api.initialization.resolve.RepositoriesMode

rootProject.name = "libgdx-agent-gameplay"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        exclusiveContent {
            forRepository {
                ivy {
                    name = "Gradle distributions"
                    url = uri("https://services.gradle.org/distributions")
                    patternLayout {
                        artifact("[artifact]-[revision]-[classifier].[ext]")
                    }
                    metadataSources { artifact() }
                }
            }
            filter { includeModule("gradle", "gradle") }
        }
    }
}

include(
    "gameplay-core",
    "gameplay-libgdx",
    "gameplay-runtime",
    "gameplay-box2d",
    "gameplay-fixture",
)

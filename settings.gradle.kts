pluginManagement {
    // Official repositories are the default so that CI (GitHub Actions) and the F-Droid
    // build server can resolve dependencies. Set AWAKR_MIRRORS=true to prefer the Chinese
    // mirrors this project was originally developed against (see docs/toolchain.md).
    val useMirrors = System.getenv("AWAKR_MIRRORS").equals("true", ignoreCase = true)
    repositories {
        if (useMirrors) {
            maven("https://maven.aliyun.com/repository/google") {
                content {
                    includeGroupByRegex("com\\.android.*")
                    includeGroupByRegex("androidx.*")
                    includeGroupByRegex("com\\.google\\.android.*")
                    includeGroupByRegex("com\\.google\\.testing.*")
                }
            }
            maven("https://repo.huaweicloud.com/repository/maven")
            maven("https://maven.aliyun.com/repository/public")
        }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    val useMirrors = System.getenv("AWAKR_MIRRORS").equals("true", ignoreCase = true)
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (useMirrors) {
            maven("https://maven.aliyun.com/repository/google") {
                content {
                    includeGroupByRegex("com\\.android.*")
                    includeGroupByRegex("androidx.*")
                    includeGroupByRegex("com\\.google\\.android.*")
                    includeGroupByRegex("com\\.google\\.testing.*")
                }
            }
            maven("https://repo.huaweicloud.com/repository/maven")
            maven("https://maven.aliyun.com/repository/public")
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "Awakr"

include(":app")

// Shared foundations
include(":core:time")
include(":core:ui")
include(":core:alarm")
include(":core:audio")
include(":core:notify")
include(":core:perm")

// Data
include(":data")

// Plugin platform
include(":sdk")
include(":plugin-runtime")
include(":plugin-codegen")

// Plugins
include(":plugins:mission-math")
include(":plugins:mission-code")
include(":plugins:mission-shake")
include(":plugins:mission-typing")
include(":plugins:mission-memory")
include(":plugins:mission-color")
include(":plugins:mission-squat")
include(":plugins:mission-step")
// include(":plugins:sound-default")
// include(":plugins:theme-default")
// include(":plugins:locale-ru")

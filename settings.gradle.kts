pluginManagement {
    repositories {
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
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
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
// include(":plugins:sound-default")
// include(":plugins:theme-default")
// include(":plugins:locale-ru")

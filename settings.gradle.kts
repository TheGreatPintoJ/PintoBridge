rootProject.name = "PintoBridge"
if (providers.gradleProperty("devBuild").isPresent) {
    rootProject.buildFileName = "dev.build.gradle.kts"
}
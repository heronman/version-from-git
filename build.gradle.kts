plugins {
    kotlin("jvm") version "1.9.25"
    id("java-library")
    id("idea")
    id("maven-publish")
}

group = "net.agl.gradle"
version = "1.0.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
    withSourcesJar()
    withJavadocJar()
}

repositories {
    mavenCentral()
    maven { url = uri(findProperty("repo.proxy.url")!! as String) }
}

val mockitoAgent = configurations.create("mockitoAgent")

dependencies {
    implementation("org.eclipse.jgit:org.eclipse.jgit:7.4.0.202509020913-r")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = project.group.toString()
            artifactId = project.name
            version = project.version.toString()
            from(components["java"])
        }
    }

    repositories {
        maven {
            name = "maven"
            url = uri(
                findProperty(
                    "repo.publish.${
                        if (project.version.toString().endsWith("-SNAPSHOT"))
                            "snapshots" else "releases"
                    }"
                )!! as String
            )
            credentials {
                this.username = findProperty("repo.publish.username")!! as String
                this.password = findProperty("repo.publish.password")!! as String
            }
        }
    }
}

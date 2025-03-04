/*
 * MIT License
 *
 * Copyright (c) 2021-2024. Aleksandr Serdiukov, Anton Zamyatin, Aleksandr Sinitsyn, Vitalii Dravgelis and Computer Technologies Laboratory ITMO University team.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.tasks.testing.logging.TestLogEvent.*
import java.io.ByteArrayOutputStream
import java.nio.file.Files

plugins {
  java
  application
  id("com.github.johnrengelman.shadow") version "7.1.2"
}

group = "ru.itmo.ctlab.hict"

repositories {
  mavenCentral()
  maven {
    url = uri("https://maven.scijava.org/content/repositories/public/")
  }
}

val vertxVersion = "4.4.1"
val junitJupiterVersion = "5.9.1"

val mainVerticleName = "ru.itmo.ctlab.hict.hict_server.MainVerticle"
val launcherClassName = "io.vertx.core.Launcher"

val watchForChange = "src/**/*"
val doOnChange = "${projectDir}/gradlew classes"

val versionFile = file("${project.projectDir}/version.txt")

val webUICloneDirectory = layout.buildDirectory.dir("webui").get()
val webUIRepositoryDirectory = webUICloneDirectory.dir("HiCT_WebUI")
val webUIRepositoryAddress = "https://github.com/ctlab/HiCT_WebUI.git"
val webUITargetDirectory = layout.projectDirectory.dir("src/main/resources/webui")
val webUIBranch = "dev-0.1.5"

version = readVersion()

application {
  mainClass.set(launcherClassName)
}

dependencies {
//  implementation(fileTree("src/main/resources/libs"))
//  runtimeOnly(fileTree("src/main/resources/libs/natives"))

  implementation("cisd:jhdf5:19.04.1")


  // https://mvnrepository.com/artifact/cisd/base
  implementation("cisd:base:18.09.0")
  implementation("org.jetbrains:annotations:24.0.0")
  implementation("org.jetbrains:annotations:24.0.0")


  // https://mvnrepository.com/artifact/org.apache.bcel/bcel
  implementation("org.apache.bcel:bcel:6.7.0")



  compileOnly("org.projectlombok:lombok:1.18.22")
  annotationProcessor("org.projectlombok:lombok:1.18.22")
  testCompileOnly("org.projectlombok:lombok:1.18.22")
  testAnnotationProcessor("org.projectlombok:lombok:1.18.22")


  implementation("org.slf4j:slf4j-api:1.7.+")
//  implementation("org.slf4j:slf4j-nop:1.7.+")
  implementation("ch.qos.logback:logback-classic:1.2.+")


  implementation(platform("io.vertx:vertx-stack-depchain:$vertxVersion"))
  implementation("io.vertx:vertx-core")
  implementation("io.vertx:vertx-web-client")
  implementation("io.vertx:vertx-web-validation")
  implementation("io.vertx:vertx-config")
  implementation("io.vertx:vertx-web")
  implementation("io.vertx:vertx-web-openapi")
  implementation("io.vertx:vertx-web-sstore-cookie")
  implementation("io.vertx:vertx-json-schema")
  implementation("io.vertx:vertx-shell")
  implementation("io.vertx:vertx-web-api-contract")
  implementation("io.vertx:vertx-uri-template")
  implementation("io.vertx:vertx-rx-java3")
  implementation("io.vertx:vertx-reactive-streams")
  testImplementation("io.vertx:vertx-junit5")
  testImplementation("org.junit.jupiter:junit-jupiter:$junitJupiterVersion")

  // https://mvnrepository.com/artifact/org.apache.commons/commons-lang3
  implementation("org.apache.commons:commons-lang3:3.12.0")

  // https://mvnrepository.com/artifact/org.apache.commons/commons-pool2
  implementation("org.apache.commons:commons-pool2:2.11.1")

// https://mvnrepository.com/artifact/org.apache.commons/commons-csv
  implementation("org.apache.commons:commons-csv:1.10.0")

  // https://mvnrepository.com/artifact/org.scijava/native-lib-loader
  implementation("org.scijava:native-lib-loader:2.4.0")


}

java {
  sourceCompatibility = JavaVersion.VERSION_19
  targetCompatibility = JavaVersion.VERSION_19
}

tasks.withType<ShadowJar> {
  archiveClassifier.set("fat")
  manifest {
    attributes(mapOf("Main-Verticle" to mainVerticleName))
  }
  mergeServiceFiles()
}

tasks.withType<Test> {
  useJUnitPlatform()
  testLogging {
    events = setOf(PASSED, SKIPPED, FAILED)
  }
}

tasks.withType<JavaExec> {
  doFirst {
    environment(
      "LD_LIBRARY_PATH",
      "\$LD_LIBRARY_PATH:/home/${System.getenv("USER")}/hdf/HDF5-1.14.1-Linux/HDF_Group/HDF5/1.14.1/lib:/home/${
        System.getenv(
          "USER"
        )
      }/hdf/HDF5-1.14.1-Linux/HDF_Group/HDF5/1.14.1/lib/plugin"
    )
    environment(
      "HDF5_PLUGIN_PATH",
      "/home/${System.getenv("USER")}/hdf/HDF5-1.14.1-Linux/HDF_Group/HDF5/1.14.1/lib/plugin"
    )
    environment("VERTXWEB_ENVIRONMENT", "dev")
  }
  args = listOf(
    "run",
    mainVerticleName,
    "--redeploy=$watchForChange",
    "--launcher-class=$launcherClassName",
    "--on-redeploy=$doOnChange"
  )
}





fun readVersion(): String {
  return if (versionFile.exists()) {
    versionFile.readText().trim()
  } else {
    "0.0.0"
  }
}

fun writeVersion(version: String) {
  versionFile.writeText(version)
}

fun incrementPatchVersion(currentVersion: String): String {
  val gitHash = getGitHash(layout.projectDirectory.asFile)
  val webuiVer = if (webUIRepositoryDirectory.asFile.exists()) {
    val webuiGitHash = getGitHash(webUIRepositoryDirectory.asFile)
    "webui_$webuiGitHash"
  } else "nowebui"
  val (semver, oldHash) = currentVersion.split("-")
  val (major, minor, patch) = semver.split(".")
  val newPatch = patch.toInt() + 1
  return "$major.$minor.$newPatch-$gitHash-$webuiVer"
}

fun getGitHash(repositoryDir: File): String {
  val byteOut = ByteArrayOutputStream()
  project.exec {
    commandLine("git", "rev-parse", "--short=7", "HEAD")
    standardOutput = byteOut
    workingDir = repositoryDir
  }
  return String(byteOut.toByteArray()).trim()
}

val currentVersion: String by lazy { readVersion() }

version = currentVersion

tasks.register("incrementPatchVersion") {
  doLast {
    val newVersion = incrementPatchVersion(currentVersion)
    writeVersion(newVersion)
    project.version = newVersion
    println("[Patch] Version incremented to $newVersion")
  }
}

tasks.register("cleanWebUI") {
  doLast {
    delete(webUIRepositoryDirectory.dir("dist"))
    delete(webUITargetDirectory)
  }
}

tasks.register("buildWebUI") {
  dependsOn("cleanWebUI")
  doLast {
    try {
      Files.createDirectories(webUICloneDirectory.asFile.toPath())
      val cloneResult = project.exec {
        commandLine("git", "clone", webUIRepositoryAddress)
        workingDir = webUICloneDirectory.asFile
        standardOutput = System.out
        isIgnoreExitValue = true
      }


      if (cloneResult.exitValue != 0) {
        print("Failed to clone WebUI repository, maybe it already exists. Trying to pull changes.")
        val pullResult = project.exec {
          commandLine("git", "pull")
          workingDir = webUIRepositoryDirectory.asFile
          standardOutput = System.out
          isIgnoreExitValue = true
        }

        if (pullResult.exitValue != 0) {
          print("Failed to pull changes from WebUI repository. Proceeding without baked-in WebUI.")
          return@doLast
        } else {
          print("Successfully pulled changes")
        }
      }

      val checkOutResult = project.exec {
        commandLine("git", "checkout", webUIBranch)
        workingDir = webUIRepositoryDirectory.asFile
        standardOutput = System.out
        isIgnoreExitValue = true
      }

      if (checkOutResult.exitValue != 0) {
        print("Failed to checkout branch ${webUIBranch}, will use main branch instead");
      }


      project.exec {
        commandLine("git", "pull")
        workingDir = webUIRepositoryDirectory.asFile
        standardOutput = System.out
        isIgnoreExitValue = true
      }


      project.exec {
        commandLine("npm", "install")
        workingDir = webUIRepositoryDirectory.asFile
        standardOutput = System.out
      }

      project.exec {
        commandLine("npm", "run", "build")
        workingDir = webUIRepositoryDirectory.asFile
        standardOutput = System.out
      }
    } catch (e: Exception) {
      print("Caught an exception during building WebUI, proceeding without it")
      print(e)
      return@doLast
    }
  }
}

tasks.register<Copy>("copyWebUI") {
  dependsOn("buildWebUI")
  doLast {
    Files.createDirectories(webUITargetDirectory.asFile.toPath())
  }
  from(webUIRepositoryDirectory.dir("dist"))
  into(webUITargetDirectory)
}

tasks.named("clean") {
  dependsOn("cleanWebUI")
}

tasks.named("processResources") {
  dependsOn("copyWebUI")
}

tasks.named("build") {
  dependsOn("copyWebUI")
  dependsOn("incrementPatchVersion")
}

tasks.named("jar") {
  dependsOn("copyWebUI")
  dependsOn("incrementPatchVersion")
}

# GitHub CI-SonarCloud Erklärung
>:warning: Eine funktionierende CI für ein Gradle Android Kotlin Projekt mit den unten beschriebenen Konfigurationen für Software Engineering II kann in diesem Repository gefunden werden. :warning:
## Account Einrichtungen und erste Projekterstellungen
Kurze Erklärungsschritte für zukünftige CIs in diese Richtung. Zum Ausgeben der Metriken wird Sonarcloud verwendet

1. Erstellung einer neuen **GitHub Organisation**
2. Erstellung eines **Repositorys** in dieser GitHub Organisation
3. Erstellung eines **SonarCloud** Accounts: https://sonarcloud.io/projects
4. Verbindung der Organisation mit SonarCloud
5. Selektion der mit SonarCloud zu analysierenden Repositories in dieser Organisation
6. Bei SonarCloud dann auf **+** klicken und **"Analyze new project"**. Hier wird die Organisation ausgewählt und dann die Repositorys, die von SonarCloud getrackt werden sollen
7. Nach der Erstellung und dem erstmaligen Scannen muss unter ``Administration -> Analysis Method "Automatic Analysis"`` **deaktiviert** werden

## Analyse via GitHub CI
- Unter **Analysis Methods** bei SonarCloud muss nun **With GitHub Actions** ausgewählt werden
- Danach muss der SonarCloud **Secret-Key** im GitHub Repository hinterlegt werden
  - In diesem Fall: https://github.com/uni-aau/github-ci/settings/secrets/actions
- Ins Feld **Name** kommt ``SONAR_TOKEN`` und bei **Value** der ``Key`` von SonarCloud
  
## Gradle Project Änderungen
- **Projekt-Spezifikationen**:
  - **Gradle Version** 8.13
  - **Android Gradle Plugin Version (agp)** 8.13.0
  - **SDK Version (target)** 36
  - **SDK Version (min)** 30
  - **Kotlin DSL**
- Im Hauptordner wird ein **.github/workflows** Ordner hinzugefügt
- In diesen wird eine **build.yml** Datei erstellt. Diese kann via ``Administration -> Analysis Method -> Github Actions -> Gradle -> build.yml`` kopiert werden:
```yml name: SonarCloud
name: SonarQube
on:
  push:
    branches:
      - main # CHECK YOUR BRANCH NAME
  pull_request:
    types: [opened, synchronize, reopened]
jobs:
  build:
    name: Build and analyze
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v6
        with:
          fetch-depth: 0 
      - name: Set up JDK 17
        uses: actions/setup-java@v5
        with:
          java-version: 17
          distribution: 'zulu'
      - name: Cache SonarQube packages
        uses: actions/cache@v5
        with:
          path: ~/.sonar/cache
          key: ${{ runner.os }}-sonar
          restore-keys: ${{ runner.os }}-sonar
      - name: Cache Gradle packages
        uses: actions/cache@v5
        with:
          path: ~/.gradle/caches
          key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle') }}
          restore-keys: ${{ runner.os }}-gradle
      - name: Build and analyze
        env:
          SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}
        run: ./gradlew build sonar --info
```
**Hinweis**: Auf den korrekten **Branch-Namen** muss geachtet werden
- Weiters muss die **app/build.gradle.kts** Datei erweitert werden. Da diese (hier in SE2) ebenso um Jacoco erweitert wird, muss dies ebenfalls noch beachtet werden
- Im Folgenden File werden die Änderungen mit Kommentaren markiert
```gradle
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    // --Beide ids hinzufügen-- Auf sonarqube Version achten (>5.X) ist derzeit (März 2026) nicht kompatibel!
    id("jacoco")
    id("org.sonarqube") version "5.1.0.4882"
}

android {
    // Werte müssen mit diesen aus der eigenen App übernommen werden
    namespace = "net.jamnig.testapp"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "net.jamnig.testapp"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        viewBinding = true
    }
    
    // --Hinzufügen--
    testOptions {
        unitTests {
            all {
                it.useJUnitPlatform()
                it.finalizedBy(tasks.named("jacocoTestReport"))
            }
        }
    }
}

kotlin {
    jvmToolchain(17)
}

tasks.register<JacocoReport>("jacocoTestReport") {
    group = "verification"
    description = "Generates code coverage report for the test task."
    dependsOn("testDebugUnitTest")

    reports {
        xml.required.set(true)
        xml.outputLocation.set(file("${project.projectDir}/build/reports/jacoco/jacocoTestReport/jacocoTestReport.xml"))
    }

    val fileFilter = listOf(
        "**/R.class",
        "**/R$*.class",
        "**/BuildConfig.*",
        "**/Manifest*.*",
        "**/*Test*.*",
        "android/**/*.*"
    )

    val debugTree =
        fileTree("${project.layout.buildDirectory.get().asFile}/tmp/kotlin-classes/debug") {
            exclude(fileFilter)
        }

    val javaDebugTree =
        fileTree("${project.layout.buildDirectory.get().asFile}/intermediates/javac/debug") {
            exclude(fileFilter)
        }

    val mainSrc = listOf(
        "${project.projectDir}/src/main/java",
        "${project.projectDir}/src/main/kotlin"
    )

    sourceDirectories.setFrom(files(mainSrc))
    classDirectories.setFrom(files(debugTree, javaDebugTree))
    executionData.setFrom(fileTree(project.layout.buildDirectory.get().asFile) {
        include("jacoco/testDebugUnitTest.exec")
        include("outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec")
    })
}

// Sonarqube-Werte müssen von Sonarcloud unter Gradle kopiert werden. Diese sind individuell 
// --Hinweis-- Darauf achten, dass Jacoco ebenfalls hinzugefügt wird
sonar {
    properties {
        property("sonar.projectKey", "uni-aau_github-ci")
        property("sonar.organization", "uni-aau")
        property("sonar.host.url", "https://sonarcloud.io")
        property("sonar.java.coveragePlugin", "jacoco")
        property(
            "sonar.coverage.jacoco.xmlReportPaths",
            "${project.projectDir}/build/reports/jacoco/jacocoTestReport/jacocoTestReport.xml"
        )
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(platform(libs.junit.bom)) // HINZUFÜGEN
    testImplementation(libs.junit)
    testImplementation(libs.junit.jupiter.api) // HINZUFÜGEN
    testRuntimeOnly(libs.junit.platform.launcher) // HINZUFÜGEN
    testRuntimeOnly(libs.junit.jupiter.engine) // HINZUFÜGEN
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
```

In **gradle/libs.version.toml** muss folgendes hinzugefügt werden:
```toml
junitJupiterApi = "6.0.3"

[libraries]
junit-bom = { module = "org.junit:junit-bom", version.ref = "junitJupiterApi" }
junit-platform-launcher = { module = "org.junit.platform:junit-platform-launcher", version.ref = "junitJupiterApi" }
junit-jupiter-api = { module = "org.junit.jupiter:junit-jupiter-api", version.ref = "junitJupiterApi" }
junit-jupiter-engine = { module = "org.junit.jupiter:junit-jupiter-engine", version.ref = "junitJupiterApi" }
```

Zum Schluss muss unter ``app/src/test/.../ExampleUnitTest.kt`` der richtige JUnit 6 Import genutzt werden. Dies ist ebenfalls für die restlichen Testklassen in Zukunft wichtig. JUnit 6 hat den folgenden Import (``org.junit.jupiter.api.*``)

```kotlin
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*
```




Nun kann die CI entweder mittels **GitHub CI** bei jedem Commit getriggered werden (Ist im Repository unter Actions) oder per Konsole mit dem Befehl **./gradlew build sonar --info**

## Maven Java Spring Boot Project Änderungen
- **Projekt-Spezifikationen**:
  - **Java Version** 21
- Im Hauptordner wird ein **.github/workflows** Ordner hinzugefügt
- In diesen wird eine **build.yml** Datei erstellt. Diese kann via ``Administration -> Analysis Method -> Github Actions -> Maven -> build.yml`` kopiert werden:
```yml name: SonarCloud
name: SonarQube
on:
  push:
    branches:
      - main
  pull_request:
    types: [opened, synchronize, reopened]
jobs:
  build:
    name: Build and analyze
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v6
        with:
          fetch-depth: 0
      - name: Set up JDK 21
        uses: actions/setup-java@v5
        with:
          java-version: 21
          distribution: 'zulu'
      - name: Cache SonarCloud packages
        uses: actions/cache@v5
        with:
          path: ~/.sonar/cache
          key: ${{ runner.os }}-sonar
          restore-keys: ${{ runner.os }}-sonar
      - name: Cache Maven packages
        uses: actions/cache@v5
        with:
          path: ~/.m2
          key: ${{ runner.os }}-m2-${{ hashFiles('**/pom.xml') }}
          restore-keys: ${{ runner.os }}-m2
      - name: Build and analyze
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}  # Needed to get PR information, if any
          SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}
        run: mvn -B verify org.sonarsource.scanner.maven:sonar-maven-plugin:sonar
```
**Hinweis**: Auf den korrekten **Branch-Namen** muss geachtet werden
- Weiters muss die **pom.xml** Datei erweitert werden. 
- Die pom.xml entspricht einer (via IntelliJ) neu generierten pom.xml mit **Jacoco & SonarCloud** Ergänzungen, sowie die zusätzlichen Änderungen für den Software-Engineering II Server:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <!-- ZUSÄTZLICH FÜR SE II -->
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>4.0.3</version>
        <relativePath/> <!-- lookup parent from repository -->
    </parent>

    <!-- Zu den Projekt spezifischen Daten abändern -->
    <groupId>net.jamnig</groupId>
    <artifactId>server</artifactId>
    <version>1.0-SNAPSHOT</version>
    <name>Demo</name>
    <description>Demo Server</description>

    <properties>
        <maven.compiler.source>21</maven.compiler.source>
        <maven.compiler.target>21</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <!-- HINZUFÜGEN -->
        <java.version>21</java.version>
        <sonar.organization>**COPY_FROM_SONAR_CLOUD**</sonar.organization>
      <!-- The projectKey is in the last line of the build.yml template when you create the sonar project -->
      <!-- Or just write ORGANIZATION-NAME_REPO-NAME -->
      <sonar.projectKey>*COPY_FROM_SONAR_CLOUD**</sonar.projectKey>
        <sonar.host.url>https://sonarcloud.io</sonar.host.url>
        <sonar.coverage.jacoco.xmlReportPaths>
            ${project.build.directory}/reports/jacoco/jacocoTestReport/jacocoTestReport.xml
        </sonar.coverage.jacoco.xmlReportPaths>
    </properties>

    <!-- HINZUFÜGEN -->
    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.junit</groupId>
                <artifactId>junit-bom</artifactId>
                <version>6.0.3</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <!-- ZUSÄTZLICH FÜR SE II -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-websocket</artifactId>
            <version>4.0.3</version>
        </dependency>

        <!-- ZUSÄTZLICH FÜR SE II -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <version>4.0.3</version>
            <scope>test</scope>
        </dependency>

        <!-- ZUSÄTZLICH FÜR SE II -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <version>1.18.36</version>
            <scope>provided</scope>
        </dependency>

        <!-- HINZUFÜGEN -->
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter-api</artifactId>
            <scope>test</scope>
        </dependency>

        <!-- HINZUFÜGEN -->
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter-engine</artifactId>
            <scope>test</scope>
        </dependency>
      </dependencies>

      <!-- HINZUFÜGEN -->
      <build>
        <plugins>
              <!-- ZUSÄTZLICH FÜR SE II -->
          <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
           </plugin>
          <plugin>
            <groupId>org.jacoco</groupId>
            <artifactId>jacoco-maven-plugin</artifactId>
            <version>0.8.14</version>
            <executions>
              <execution>
                <goals>
                  <goal>prepare-agent</goal>
                </goals>
              </execution>
              <execution>
                <id>report</id>
                <phase>test</phase>
                <goals>
                  <goal>report</goal>
                </goals>
              </execution>
            </executions>
          </plugin>
        </plugins>
    </build>
</project>
```

## Troubleshooting
- **./gradlew Permission denied**
  - Rechte müssen vergeben werden: ``chmod +x gradlew``
  - Oder auch ``git update-index --chmod=+x gradlew`` im Git-Ordner


Bei Fehlern bitte Issue erstellen!
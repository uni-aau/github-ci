# Gitlab CI Erklärung
## Account Einrichtungen und erste Projekterstellungen
Kurze Erklärungsschritte für zukünftige CIs in diese Richtung. Zum Ausgeben der Metriken wird Sonarcloud verwendet

- Erstellung einer neuen **GitHub Organisation**
- Erstellung eines **Repositorys** in dieser GitHub Organisation
- Erstellung eines **Sonarcloud** Accounts: https://sonarcloud.io/projects
- Bei Sonarcloud dann auf **+** klicken und **"Analyze new project"**. Hier wird die Organisation ausgewählt und dann die Repositorys, die von Sonarcloud getrackt werden sollen
- Nach der Erstellung und dem erstmaligen Scannen muss unter ``Administration -> Analysis Method "Automatic Analysis"`` **deaktiviert** werden

## Analyse via GitHub CI
- Unter **Analysis Methods** bei Sonarcloud muss nun das **GitHub Tutorial** ausgewählt werden
- Danach muss der Sonarcloud **Secret-Key** im GitHub Repository hinterlegt werden
  - In diesem Fall: https://github.com/testorg-djamn/Gitlab-CI/settings/secrets/actions
  
## Gradle Project Änderungen
- In diesem Repository wurde **Java Version 11**, **Gradle Version 7.5**, **Android Gradle Plugin Version 7.4.1** und **SDK Version 33** verwendet
- Im Hauptordner wird ein **.github/workflows** Ordner hinzugefügt
- In diesen wird eine **android.yml** (oder build.yml) Datei erstellt. Diese kann via ``Administration -> Analysis Method -> Github Actions -> Gradle -> build.yml`` kopiert werden:
```java name: SonarCloud
on:
  push:
    branches:
      - master
  pull_request:
    types: [opened, synchronize, reopened]
jobs:
  build:
    name: Build and analyze
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
        with:
          fetch-depth: 0  # Shallow clones should be disabled for a better relevancy of analysis
      - name: Set up JDK 11
        uses: actions/setup-java@v3
        with:
          java-version: 11
          distribution: 'zulu' # Alternative distribution options are available
      - name: Cache SonarCloud packages
        uses: actions/cache@v3
        with:
          path: ~/.sonar/cache
          key: ${{ runner.os }}-sonar
          restore-keys: ${{ runner.os }}-sonar
      - name: Cache Gradle packages
        uses: actions/cache@v3
        with:
          path: ~/.gradle/caches
          key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle') }}
          restore-keys: ${{ runner.os }}-gradle
      - name: Build and analyze
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}  # Needed to get PR information, if any
          SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}
        run: ./gradlew build sonarqube --info
```
**Hinweis**: Auf den korrekten **Branch-Namen** muss geachtet werden
- Weiters muss die **app/gradle.build** Datei erweitert werden. Da diese (hier in SE2) ebenso um Jacoco erweitert wird, muss dies ebenfalls noch beachtet werden
- Im Folgenden File werden die Änderungen mit Kommentaren markiert
```java
plugins {
    id 'com.android.application'
    // Beide ids hinzufügen - Auf sonarqube Version achten (Siehe Sonarcloud -> Gradle)
    id 'jacoco'
    id 'org.sonarqube' version '3.5.0.2730'
}

android {
    // Werte müssen mit diesen aus der eigenen App übernommen werden
    namespace 'net.jamnig.testappnew'
    compileSdk 33

    defaultConfig {
        applicationId "net.jamnig.testappnew"
        minSdk 29
        targetSdk 33
        versionCode 1
        versionName "1.0"

        testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_11
        targetCompatibility JavaVersion.VERSION_11
    }

    // Hinzufügen
    testOptions {
        unitTests.all {
            useJUnitPlatform()
            finalizedBy jacocoTestReport
        }
    }
}
// Hinzufügen + Überprüfen, ob xml.destination Path korrekt ist
task jacocoTestReport(type: JacocoReport, dependsOn: 'testDebugUnitTest') {

    reports {
        xml.enabled true
        xml.destination file("${project.projectDir}/build/reports/jacoco/jacocoTestReport/jacocoTestReport.xml")
    }

    def fileFilter = ['**/R.class', '**/R$*.class', '**/BuildConfig.*', '**/Manifest*.*', '**/*Test*.*', 'android/**/*.*']
    def debugTree = fileTree(dir: "${buildDir}/intermediates/javac/debug", excludes: fileFilter)
    def mainSrc = "${project.projectDir}/src/main/java"

    sourceDirectories.from = files([mainSrc])
    classDirectories.from = files([debugTree])
    executionData.from = files("${buildDir}/jacoco/testDebugUnitTest.exec")
}

// Sonarqube-Werte müssen von Sonarcloud unter Gradle kopiert werden. Diese sind individuell 
//(Hinweis - Darauf achten, dass Jacoco mitkopiert wird)
sonarqube {
  properties {
    property "sonar.projectKey", "testorg-djamn_testrepo2"
    property "sonar.organization", "testorg-djamn"
    property "sonar.host.url", "https://sonarcloud.io"
    property "sonar.java.coveragePlugin", "jacoco"
    property "sonar.coverage.jacoco.xmlReportPaths", "${project.projectDir}/build/reports/jacoco/jacocoTestReport/jacocoTestReport.xml"
  }
}

// Überprüfen, ob Dependencies korrekt (und wsl höher) sind und ggf. anpassen
// Hinweis - Mit JUnit 5 wird gearbeitet (für jacoco)
dependencies {

    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'com.google.android.material:material:1.8.0'
    implementation 'androidx.constraintlayout:constraintlayout:2.1.4'
    testImplementation 'org.junit.jupiter:junit-jupiter-api:5.7.0'
    testRuntimeOnly 'org.junit.jupiter:junit-jupiter-engine:5.7.0'
    androidTestImplementation 'androidx.test.ext:junit:1.1.5'
    androidTestImplementation 'androidx.test.espresso:espresso-core:3.5.1'
}
```
Nun kann die CI entweder mittels **GitHub CI** bei jedem Commit getriggered werden (Ist im Repository unter Actions) oder per Konsole mit dem Befehl **./gradlew build sonarqube --info**

## Troubleshooting
- Da die App mit einer leeren Aktivität mit Android Studio erstellt wurde, wird automatisch ein Testfall hinzugefügt
  - Unter ``app/src/test/.../ExampleUnitTest.java`` muss dieser **entfernt** werden, da der Testfall noch mit JUnit 4 läuft
- Eine laufende CI kann in diesem Repository gefunden werden
- ./gradlew Permission denied
  - Rechte müssen vergeben werden: ``chmod +x gradlew``
  - Oder auch ``git update-index --chmod=+x gradlew``


Bei Fehlern bitte Issue erstellen!
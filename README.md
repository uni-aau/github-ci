# GitHub CI-SonarCloud Erklärung
>:warning: Eine funktionierende CI mit den Konfigurationen unten beschrieben kann in diesem Repository gefunden werden :warning:
## Account Einrichtungen und erste Projekterstellungen
Kurze Erklärungsschritte für zukünftige CIs in diese Richtung. Zum Ausgeben der Metriken wird Sonarcloud verwendet

- Erstellung einer neuen **GitHub Organisation**
- Erstellung eines **Repositorys** in dieser GitHub Organisation
- Erstellung eines **SonarCloud** Accounts: https://sonarcloud.io/projects
- Bei SonarCloud dann auf **+** klicken und **"Analyze new project"**. Hier wird die Organisation ausgewählt und dann die Repositorys, die von SonarCloud getrackt werden sollen
- Nach der Erstellung und dem erstmaligen Scannen muss unter ``Administration -> Analysis Method "Automatic Analysis"`` **deaktiviert** werden

## Analyse via GitHub CI
- Unter **Analysis Methods** bei SonarCloud muss nun **With GitHub Actions** ausgewählt werden
- Danach muss der SonarCloud **Secret-Key** im GitHub Repository hinterlegt werden
  - In diesem Fall: https://github.com/uni-aau/github-ci/settings/secrets/actions
- Ins Feld **Name** kommt ``SONAR_TOKEN`` und bei **Value** der ``Key`` von SonarCloud
  
## Gradle Project Änderungen
- In diesem Repository wurde **Java Version 17**, **Gradle Version 8.0**, **Android Gradle Plugin Version 8.1.0**, **SDK Version (target) 34** **SDK Version (min) 29** verwendet
- Im Hauptordner wird ein **.github/workflows** Ordner hinzugefügt
- In diesen wird eine **build.yml** Datei erstellt. Diese kann via ``Administration -> Analysis Method -> Github Actions -> Gradle -> build.yml`` kopiert werden:
```java name: SonarCloud
name: SonarCloud
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
      - name: Set up JDK 17
        uses: actions/setup-java@v3
        with:
          java-version: 17
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
        run: ./gradlew build sonar --info
```
**Hinweis**: Auf den korrekten **Branch-Namen** muss geachtet werden
- Weiters muss die **app/build.gradle** Datei erweitert werden. Da diese (hier in SE2) ebenso um Jacoco erweitert wird, muss dies ebenfalls noch beachtet werden
- Im Folgenden File werden die Änderungen mit Kommentaren markiert
```java
plugins {
    id 'com.android.application'
    // Beide ids hinzufügen - Auf sonarqube Version achten (Siehe Sonarcloud -> Gradle)
    id 'jacoco'
    id 'org.sonarqube' version '4.4.1.3373'
}

android {
    // Werte müssen mit diesen aus der eigenen App übernommen werden
    namespace 'net.jamnig.testapp'
    compileSdk 34

    defaultConfig {
        applicationId "net.jamnig.testapp"
        minSdk 29
        targetSdk 34
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
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
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
        xml.required = true
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
// Hinweis - Darauf achten, dass Jacoco mitkopiert wird
sonar {
    properties {
        property "sonar.projectKey", "uni-aau_github-ci"
        property "sonar.organization", "uni-aau"
        property "sonar.host.url", "https://sonarcloud.io"
        property "sonar.java.coveragePlugin", "jacoco"
        property "sonar.coverage.jacoco.xmlReportPaths", "${project.projectDir}/build/reports/jacoco/jacocoTestReport/jacocoTestReport.xml"
    }
}


// Überprüfen, ob Dependencies korrekt (und wsl höher) sind und ggf. anpassen
// Hinweis - Mit JUnit 5 wird gearbeitet (für jacoco)
dependencies {
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'com.google.android.material:material:1.11.0'
    implementation 'androidx.constraintlayout:constraintlayout:2.1.4'
    testImplementation 'org.junit.jupiter:junit-jupiter-api:5.7.0'
    testRuntimeOnly 'org.junit.jupiter:junit-jupiter-engine:5.7.0'
    androidTestImplementation 'androidx.test.ext:junit:1.1.5'
    androidTestImplementation 'androidx.test.espresso:espresso-core:3.5.1'
}
```
Nun kann die CI entweder mittels **GitHub CI** bei jedem Commit getriggered werden (Ist im Repository unter Actions) oder per Konsole mit dem Befehl **./gradlew build sonar --info**

## Troubleshooting
- Da die App mit einer leeren Aktivität mit Android Studio erstellt wurde, wird automatisch ein Testfall hinzugefügt
  - Unter ``app/src/test/.../ExampleUnitTest.java`` muss dieser **entfernt** werden, da der Testfall noch mit JUnit 4 läuft
- ./gradlew Permission denied
  - Rechte müssen vergeben werden: ``chmod +x gradlew``
  - Oder auch ``git update-index --chmod=+x gradlew``


Bei Fehlern bitte Issue erstellen!
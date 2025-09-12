Gradle backend + SonarQube in Jenkins:
Might be useful: https://www.freecodecamp.org/news/how-to-improve-your-code-quality-with-sonarqube/
---

### 🔹 How SonarQube works with Gradle

* SonarQube **does not run tests by itself** – it only **collects test reports and coverage reports** generated during your Gradle build.
* That means:

  1. Your **Gradle build** should run all tests.
  2. Tests must generate **test reports** (JUnit/TestNG) and **code coverage reports** (JaCoCo).
  3. SonarQube plugin then **picks up these reports** and sends results to your SonarQube server.

---

### 🔹 Steps you need

1. **Make sure tests run during Gradle build**
   In your `build.gradle` (backend project):

   ```gradle
   test {
       useJUnitPlatform()
       reports {
           junitXml.required = true
           html.required = true
       }
   }
   ```

2. **Add JaCoCo plugin for coverage**

   ```gradle
   plugins {
       id "jacoco"
       id "org.sonarqube" version "4.0.0.2929" // check your SonarQube plugin version
   }

   jacoco {
       toolVersion = "0.8.10"
   }

   test {
       finalizedBy jacocoTestReport
   }

   jacocoTestReport {
       dependsOn test
       reports {
           xml.required = true
           html.required = true
       }
   }
   ```

   ➝ This generates:

   * Unit test results (`build/test-results/test`)
   * JaCoCo coverage (`build/reports/jacoco/test/jacocoTestReport.xml`)

3. **Configure SonarQube properties**
   Either in `build.gradle`:

   ```gradle
   sonarqube {
       properties {
           property "sonar.projectKey", "my-backend"
           property "sonar.projectName", "Backend Service"
           property "sonar.projectVersion", "1.0"
           property "sonar.sources", "src/main/java"
           property "sonar.tests", "src/test/java"
           property "sonar.junit.reportPaths", "build/test-results/test"
           property "sonar.java.coveragePlugin", "jacoco"
           property "sonar.jacoco.reportPaths", "build/jacoco/test.exec"
           property "sonar.coverage.jacoco.xmlReportPaths", "build/reports/jacoco/test/jacocoTestReport.xml"
       }
   }
   ```

   Or in `sonar-project.properties`.

4. **Update Jenkinsfile**
   In your pipeline:

   ```groovy
   stage("Build & Test") {
       steps {
           sh './gradlew clean build test jacocoTestReport'
       }
   }

   stage("SonarQube Scan") {
       steps {
           withSonarQubeEnv('Sonarqube') {
               sh './gradlew sonarqube'
           }
       }
   }
   ```

---

### 🔹 What you’ll get in SonarQube

* Code quality issues (bugs, vulnerabilities, code smells)
* Test results (pass/fail, counts, duration)
* Coverage % from JaCoCo
* Duplications, maintainability, reliability/security ratings
---

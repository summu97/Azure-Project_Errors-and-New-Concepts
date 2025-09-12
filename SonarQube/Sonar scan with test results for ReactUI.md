Perfect 👍 thanks for clarifying — ReactUI with **npm/yarn build + SonarQube** is a bit different than backend Java/Gradle.

Here’s how you should set it up:

---

## 🔹 How SonarQube works for React (JavaScript/TypeScript)

* Just like with Gradle, **SonarQube doesn’t run tests by itself**.
* It collects **linting issues, code smells, coverage, and test results** — but only if you generate those reports during your build.

So you need:

1. **Run build + tests with npm**
2. **Generate coverage reports (lcov)** with Jest (or your chosen test runner).
3. **Configure SonarQube** to pick up those reports.

---

## 🔹 Steps

### 1. Configure Jest for Coverage

In `package.json` (React project):

```json
"scripts": {
  "test": "jest --coverage",
  "build": "react-scripts build"
},
"jest": {
  "collectCoverage": true,
  "coverageReporters": ["lcov", "text", "cobertura"]
}
```

This ensures:

* Tests run with `npm test`
* Coverage report generated at `coverage/lcov.info`

---

### 2. Add SonarQube Configuration

Create a file `sonar-project.properties` in your React project root:

```properties
sonar.projectKey=react-ui
sonar.projectName=React UI
sonar.sources=src
sonar.tests=src
sonar.test.inclusions=**/*.test.js,**/*.test.jsx,**/*.test.ts,**/*.test.tsx
sonar.javascript.lcov.reportPaths=coverage/lcov.info
sonar.coverage.exclusions=**/*.test.js,**/*.test.jsx,**/*.test.ts,**/*.test.tsx
```

---

### 3. Jenkins Pipeline

```groovy
pipeline {
    agent any

    stages {
        stage("Build ReactUI") {
            steps {
                sh '''
                  npm install
                  npm run build
                '''
            }
        }

        stage("Run Tests with Coverage") {
            steps {
                sh 'npm test -- --coverage'
            }
        }

        stage("SonarQube Scan") {
            steps {
                withSonarQubeEnv('Sonarqube') {
                    sh 'sonar-scanner'
                }
            }
        }
    }
}
```

---

## 🔹 What you’ll get in SonarQube

* **Bugs, vulnerabilities, code smells** (from JS/TS rules)
* **Unit test results** (from Jest)
* **Code coverage %** (from `lcov.info`)
* **Duplications, maintainability, security hotspots**

---

👉 Recommendation: keep **tests/coverage in a separate stage** from SonarQube, same as backend, so that if build/tests fail, you don’t confuse it with Sonar analysis errors.

---

Do you want me to also show you how to **integrate this into your existing Jenkinsfile** where you already run backend + frontend SonarQube scans?

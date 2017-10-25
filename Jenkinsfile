node ('java') {
    stage('test project') {
        checkout scm
        sh 'chmod +x ./gradlew'
        sh './gradlew clean test'
        junit healthScaleFactor: 100.0, testResults: '**/test-results/**/*.xml'
    }
}

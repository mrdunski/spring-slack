node ('java') {
    stage('test project') {
        checkout scm
        sh 'chmod +x ./gradlew'
        sh './gradlew clean test'
        junit healthScaleFactor: 100.0, testResults: '**/test-results/**/*.xml'
    }
    stage('upload artifact') {
        withCredentials([usernamePassword(credentialsId: 'artifactory', passwordVariable: 'password', usernameVariable: 'user')]) {
            checkout scm
            sh 'chmod +x ./gradlew'
            sh "./gradlew clean upload -Pv=$BUILD_NUMBER -PmavenUser=$user -PmavenPassword=$password"
        }
    }
}

//common helper for invoking SBT tasks
void sbtAction(String task) {
  echo "Executing ${task} on SBT"
  sh '''
      echo "realm=Sonatype Nexus Repository Manager\nhost=${NEXUS}\nuser=${NEXUS_CREDENTIALS_USR}\npassword=${NEXUS_CREDENTIALS_PSW}" > ~/.sbt/.credentials
     '''
  //using both interpolation and string concatenation to avoid Jenkins security warnings
  sh 'sbt -Dsbt.log.noformat=true -Djavax.net.ssl.trustStore=./PDNDTrustStore -Djavax.net.ssl.trustStorePassword=${PDND_TRUST_STORE_PSW} generateCode "project root" ' + "${task}"
}

pipeline {

  agent none

  stages {
    stage('Initializing build') {
      agent { label 'sbt-template' }
      environment {
        PDND_TRUST_STORE_PSW = credentials('pdnd-interop-trust-psw')
      }
      steps {
        withCredentials([file(credentialsId: 'pdnd-interop-trust-cert', variable: 'pdnd_certificate')]) {
          sh '''
             cat \$pdnd_certificate > gateway.interop.pdnd.dev.cer
             keytool -import -file gateway.interop.pdnd.dev.cer -alias pdnd-interop-gateway -keystore PDNDTrustStore -storepass ${PDND_TRUST_STORE_PSW} -noprompt
             cp $JAVA_HOME/jre/lib/security/cacerts main_certs
             keytool -importkeystore -srckeystore main_certs -destkeystore PDNDTrustStore -srcstorepass ${PDND_TRUST_STORE_PSW} -deststorepass ${PDND_TRUST_STORE_PSW}
           '''
          stash includes: "PDNDTrustStore", name: "pdnd_trust_store"
        }
      }
    }

    stage('Test and Publish') {
      agent { label 'sbt-template' }
      environment {
        NEXUS = 'gateway.interop.pdnd.dev'
        DOCKER_REPO = 'ghcr.io'
        MAVEN_REPO = 'gateway.interop.pdnd.dev'
        NEXUS_CREDENTIALS = credentials('pdnd-nexus')
        GITHUB_PAT = credentials('github-bot-rw')
        PDND_TRUST_STORE_PSW = credentials('pdnd-interop-trust-psw')
      }
      steps {
        container('sbt-container') {
          unstash "pdnd_trust_store"
          script {
            sh '''echo $GITHUB_PAT | docker login $DOCKER_REPO -u USERNAME --password-stdin'''
            sbtAction 'test docker:publish "project client" publish'
          }
        }
      }
    }

  }
}
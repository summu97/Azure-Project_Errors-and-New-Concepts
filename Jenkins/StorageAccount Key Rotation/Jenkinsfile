pipeline {
    agent any

    environment {
        TEMP_FILE = 'params.yaml'
        KEY_VAULT_NAME = 'key-vault-adq'
    }

    stages {

        stage('Azure Login with Managed Identity') {
            steps {
                script {
                    echo '🔐 Logging in to Azure using VM Managed Identity...'
                    sh 'az login --identity'
                }
            }
        }

        stage('Fetch Parameters File') {
            steps {
                git branch: 'main', url: 'https://github.com/summu97/Azure-JenkinsKeyRotationProject.git'
                script {
                    echo "📥 Downloaded parameter file"
                    sh 'cat ${TEMP_FILE}'
                }
            }
        }

        stage('Fetch and Compare Keys') {
            steps {
                script {
                    def secrets = readYaml file: "${TEMP_FILE}"
                    secrets.secrets.each { secret ->
                        def keyVaultValue = sh(
                            script: "az keyvault secret show --name ${secret.name} --vault-name ${KEY_VAULT_NAME} --query value -o tsv",
                            returnStdout: true
                        ).trim()

                        def liveKey = sh(
                            script: "az storage account keys list --account-name ${secret.storageAccountName} --query \"[?keyName=='${secret.keyType}'].value\" -o tsv",
                            returnStdout: true
                        ).trim()

                        if (keyVaultValue == liveKey) {
                            echo "✅ Keys matched for ${secret.name}"
                            input message: "Keys matched for ${secret.name}. Do you want to rotate them?", ok: "Yes, rotate"
                        } else {
                            echo "⚠️ Keys already different for ${secret.name}, skipping input."
                        }
                    }
                }
            }
        }

        stage('Rotate Keys') {
            steps {
                script {
                    def secrets = readYaml file: "${TEMP_FILE}"
                    secrets.secrets.each { secret ->
                        sh """
                            az storage account keys renew \
                            --account-name ${secret.storageAccountName} \
                            --key ${secret.keyType}
                        """
                        echo "🔁 Rotated key ${secret.keyType} for ${secret.storageAccountName}"
                    }
                }
            }
        }

        stage('Update KeyVault with New Keys') {
            steps {
                script {
                    def secrets = readYaml file: "${TEMP_FILE}"
                    secrets.secrets.each { secret ->
                        def newKey = sh(
                            script: "az storage account keys list --account-name ${secret.storageAccountName} --query \"[?keyName=='${secret.keyType}'].value\" -o tsv",
                            returnStdout: true
                        ).trim()

                        sh """
                            az keyvault secret set \
                            --vault-name ${KEY_VAULT_NAME} \
                            --name ${secret.name} \
                            --value '${newKey}'
                        """
                        echo "🔑 Updated KeyVault secret: ${secret.name}"
                    }
                }
            }
        }
    }

    post {
        success {
            echo '✅ Key rotation completed successfully.'
        }
        failure {
            echo '❌ Pipeline failed. Check logs.'
        }
    }
}

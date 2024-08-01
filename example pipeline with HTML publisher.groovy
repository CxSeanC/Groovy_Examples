pipeline {
    agent any

    environment {
        CX_API_KEY = '<API Key from CxONE platform>'
        REPO_URL = 'https://github.com/CxSeanOrg/WebGoat.git'
        CXONE_CLI_URL_WIN = 'https://github.com/Checkmarx/ast-cli/releases/download/2.0.58/ast-cli_2.0.58_windows_x64.zip'
        CXONE_CLI_URL_LINUX = 'https://github.com/Checkmarx/ast-cli/releases/download/2.0.58/ast-cli_2.0.58_linux_x64.tar.gz'
    }

    stages {
        stage('Diagnostics') {
            steps {
                script {
                    echo '--- Starting Diagnostics ---'

                    // 1. Logging: Log basic information
                    echo "Running on ${env.NODE_NAME} with executor ${env.EXECUTOR_NUMBER}"
                    echo "Workspace is ${env.WORKSPACE}"
                    echo "Jenkins URL: ${env.JENKINS_URL}"
                    echo "Build URL: ${env.BUILD_URL}"

                    // 2. Environment Information: Dump environment variables
                    echo '--- Environment Variables ---'
                    sh 'printenv'

                    echo '--- Diagnostics Completed ---'
                }
            }
        }

        stage('Checkout') {
            steps {
                git url: "${REPO_URL}", branch: 'develop'
            }
        }

        stage('Setup Checkmarx CLI') {
            steps {
                script {
                    def isWindows = isUnix() == false
                    if (isWindows) {
                        // Windows Setup
                        if (!fileExists("CxONE_CLI\\cx.exe")) {
                            powershell """
                                Invoke-WebRequest -Uri ${CXONE_CLI_URL_WIN} -OutFile CxONE_CLI.zip
                                Expand-Archive -Path CxONE_CLI.zip -DestinationPath CxONE_CLI
                                Remove-Item CxONE_CLI.zip
                            """
                        }
                    } else {
                        // Linux Setup
                        if (!fileExists("CxONE_CLI/cx")) {
                            sh """
                                curl -LO ${CXONE_CLI_URL_LINUX}
                                mkdir -p CxONE_CLI
                                tar -xzf ast-cli_2.0.58_linux_x64.tar.gz -C CxONE_CLI
                                rm ast-cli_2.0.58_linux_x64.tar.gz
                            """
                        }
                    }
                }
            }
        }

        stage('Scan with CheckmarxONE') {
            steps {
                script {
                    def isWindows = isUnix() == false
                    def scanCommand = isWindows ? "CxONE_CLI\\cx.exe" : "./CxONE_CLI/cx"
                    def outputName = "cx_scan_summary.html"

                    if (isWindows) {
                        powershell """
                            ${scanCommand} scan create --project-name 'WebGoat' -s ${env.WORKSPACE} --branch 'main' --apikey ${CX_API_KEY} --report-format summaryHTML --output-path . --output-name ${outputName}
                        """
                    } else {
                        sh """
                            ${scanCommand} scan create --project-name 'WebGoat' -s ${env.WORKSPACE} --branch 'main' --apikey ${CX_API_KEY} --report-format summaryHTML --output-path . --output-name ${outputName}
                        """
                    }
                }
            }
        }

        stage('Publish HTML Report') {
            steps {
                script {
                    publishHTML([
                        allowMissing: false,
                        alwaysLinkToLastBuild: true,
                        keepAll: true,
                        reportDir: '.',
                        reportFiles: 'cx_scan_summary.html',
                        reportName: 'Checkmarx Scan Report'
                    ])
                }
            }
        }
    }

    post {
        always {
            echo 'Scan completed!'
        }
    }
}
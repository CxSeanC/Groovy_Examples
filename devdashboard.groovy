// Jenkins Pipeline Script

// Import necessary libraries
import groovy.json.JsonSlurper

// Define the generateHtmlReport function with @NonCPS annotation
@NonCPS
def generateHtmlReport(String jsonString) {
    // Parse the JSON string
    def json = new JsonSlurper().parseText(jsonString)

    // Define severity order for sorting
    def severityOrder = ['HIGH', 'MEDIUM', 'LOW', 'INFO']

    // Check if json.results is a list
    def resultsList = json.results instanceof List ? json.results : []

    // Sort vulnerabilities by severity without using closures
    def sortedResults = resultsList.sort { a, b ->
        severityOrder.indexOf(a.severity ?: 'INFO') <=> severityOrder.indexOf(b.severity ?: 'INFO')
    }

    // Group vulnerabilities by type without using groupBy
    def vulnerabilitiesByType = [:].withDefault { [] }
    for (def result : sortedResults) {
        def type = result.type ?: 'N/A'
        vulnerabilitiesByType[type] << result
    }

    // Build vulnerability tables by type
    def vulnerabilityTables = ''

    vulnerabilitiesByType.each { type, vulnerabilities ->
        // Only proceed if type is 'sast', 'kics', or 'sca'
        if (['sast', 'kics', 'sca'].contains(type)) {
            def typeLabel = type.toUpperCase()
            // Build the table for this type
            def tableContent = """
            <h3>${typeLabel} Vulnerabilities</h3>
            <table border="1" cellpadding="5" cellspacing="0">
                <thead>
                    <tr>
                        <th>File</th>
                        <th>Line</th>
                        <th>Severity</th>
                        <th>Vulnerability Type</th>
                        <th>Description</th>
                    </tr>
                </thead>
                <tbody>
            """

            def hasRows = false
            for (def result : vulnerabilities) {
                def nodes = result?.data?.nodes
                def file = 'N/A'
                def line = 'N/A'
                if (nodes && nodes.size() > 0) {
                    file = nodes[0]?.fileName ?: 'N/A'
                    line = nodes[0]?.line ?: 'N/A'
                } else if (result?.data?.filename) {
                    file = result.data.filename
                    line = result.data.line ?: 'N/A'
                }
                // Exclude entries where file is 'N/A'
                if (file != 'N/A') {
                    def severity = result?.severity ?: 'N/A'
                    def description = result?.description ?: 'N/A'
                    def queryName = result?.data?.queryName ?: 'N/A'

                    tableContent += """
                    <tr>
                        <td>${file}</td>
                        <td>${line}</td>
                        <td>${severity}</td>
                        <td>${queryName}</td>
                        <td>${description}</td>
                    </tr>
                    """
                    hasRows = true
                }
            }

            tableContent += """
                </tbody>
            </table>
            """

            // Append this table only if there are entries
            if (hasRows) {
                vulnerabilityTables += tableContent
            }
        }
    }

    // Check if vulnerabilityTables is empty
    if (!vulnerabilityTables?.trim()) {
        vulnerabilityTables = "<p>No vulnerabilities with file information were found.</p>"
    }

    // Extract data with null checks for summary
    def riskLevel = resultsList*.severity?.max { severityOrder.indexOf(it ?: 'INFO') } ?: 'N/A'
    def totalVulnerabilities = json.totalCount ?: resultsList.size()
    def highVulns = resultsList.count { it.severity == 'HIGH' }
    def mediumVulns = resultsList.count { it.severity == 'MEDIUM' }
    def lowVulns = resultsList.count { it.severity == 'LOW' }

    def sastVulns = resultsList.count { it.type == 'sast' }
    def kicsVulns = resultsList.count { it.type == 'kics' }
    def scaVulns = resultsList.count { it.type == 'sca' }

    // Build the full HTML report
    def reportHtml = """
    <html>
    <head>
        <style>
            body { font-family: Arial, sans-serif; }
            h1 { color: #333; }
            .high-risk { background-color: #ff4d4d; color: white; padding: 10px; font-size: 24px; text-align: center; }
            .vuln-table { margin-top: 20px; width: 100%; }
            .vuln-table td, .vuln-table th { padding: 10px; text-align: center; font-size: 16px; border: 1px solid #ddd; }
            .vulnerabilities { margin-top: 30px; }
            table { border-collapse: collapse; width: 100%; }
            th { background-color: #f2f2f2; }
        </style>
    </head>
    <body>
        <h1>Checkmarx Scan Results</h1>
        <div class="high-risk">Risk Level: ${riskLevel}</div>

        <table class="vuln-table">
            <tr>
                <td>Total Vulnerabilities</td>
                <td>${totalVulnerabilities}</td>
            </tr>
            <tr>
                <td>High</td>
                <td>${highVulns}</td>
            </tr>
            <tr>
                <td>Medium</td>
                <td>${mediumVulns}</td>
            </tr>
            <tr>
                <td>Low</td>
                <td>${lowVulns}</td>
            </tr>
        </table>

        <table class="vuln-table">
            <tr>
                <td>SAST Vulnerabilities</td>
                <td>${sastVulns}</td>
            </tr>
            <tr>
                <td>KICS (IAC) Vulnerabilities</td>
                <td>${kicsVulns}</td>
            </tr>
            <tr>
                <td>SCA Vulnerabilities</td>
                <td>${scaVulns}</td>
            </tr>
        </table>

        <div class="vulnerabilities">
            <h2>Detailed Vulnerabilities</h2>
            ${vulnerabilityTables}
        </div>

        <a href="https://checkmarx.one.url">More Details on Checkmarx One</a>
    </body>
    </html>
    """

    return reportHtml ?: ''
}

// Begin the Scripted Pipeline
node {
    // Set environment variables
    env.CX_API_KEY = 'your_apikey_here' // Replace with your actual API key
    env.REPO_URL = 'https://github.com/CxSeanOrg/WebGoat.git'
    env.CXONE_CLI_URL_WIN = 'https://github.com/Checkmarx/ast-cli/releases/download/2.0.58/ast-cli_2.0.58_windows_x64.zip'
    env.CXONE_CLI_URL_LINUX = 'https://github.com/Checkmarx/ast-cli/releases/download/2.0.58/ast-cli_2.0.58_linux_x64.tar.gz'

    try {
        stage('Checkout') {
            checkout([$class: 'GitSCM', branches: [[name: '*/develop']], userRemoteConfigs: [[url: env.REPO_URL]]])
        }

        stage('Download CLI') {
            def isWindows = isUnix() == false
            if (isWindows) {
                powershell "Invoke-WebRequest -Uri ${env.CXONE_CLI_URL_WIN} -OutFile CxONE_CLI.zip"
                powershell 'Expand-Archive -Path CxONE_CLI.zip -DestinationPath ./CxONE_CLI -Force'
            } else {
                sh "wget ${env.CXONE_CLI_URL_LINUX} -O CxONE_CLI.tar.gz"
                sh 'mkdir -p CxONE_CLI && tar -xzf CxONE_CLI.tar.gz -C ./CxONE_CLI'
            }
        }

        stage('Scan with CheckmarxONE') {
            def isWindows = isUnix() == false
            if (isWindows) {
                powershell """CxONE_CLI\\cx.exe scan create --project-name 'WebGoat' -s '${env.WORKSPACE}' --branch 'main' --apikey '${env.CX_API_KEY}' --report-format json --output-name result --output-path ."""
            } else {
                sh """./CxONE_CLI/cx scan create --project-name 'WebGoat' -s '${env.WORKSPACE}' --branch 'main' --apikey '${env.CX_API_KEY}' --report-format json --output-name result --output-path ."""
            }
        }

        stage('Generate HTML Report') {
            if (fileExists('result.json')) {
                def resultJson = readFile 'result.json'
                if (resultJson?.trim()) {
                    try {
                        def resultHtml = generateHtmlReport(resultJson)
                        if (resultHtml?.trim()) {
                            writeFile file: 'checkmarx_report.html', text: resultHtml
                        } else {
                            error 'Failed to generate HTML report: resultHtml is empty.'
                        }
                    } catch (Exception e) {
                        echo "Error generating HTML report: ${e}"
                        error 'Failed to generate HTML report due to an exception.'
                    }
                } else {
                    error 'result.json is empty.'
                }
            } else {
                error 'result.json file does not exist.'
            }
        }

        stage('Publish HTML Report') {
            publishHTML(target: [
                reportDir: '.',
                reportFiles: 'checkmarx_report.html',
                reportName: 'Checkmarx Scan Results',
                allowMissing: false,
                keepAll: true
            ])
        }
    } catch (Exception e) {
        echo "Pipeline failed: ${e}"
        currentBuild.result = 'FAILURE'
        throw e
    } finally {
        echo 'Scan completed!'
    }
}

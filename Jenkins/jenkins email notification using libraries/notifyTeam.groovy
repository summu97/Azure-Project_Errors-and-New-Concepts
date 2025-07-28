def call(Map config = [:]) {
    def env = config.env ?: 'Unknown'
    def service = config.serviceName ?: 'Unknown Service'
    def buildNumber = config.buildNumber ?: 'N/A'
    def buildUrl = config.buildUrl ?: '#'
    def type = config.type ?: 'info'

    def recipients = [
        'vbboya@afmsagaftrafund.org',
        'vsboyina@afmsagaftrafund.org',
        'dkgiddaluri@afmsagaftrafund.org',
        'nganesh@afmsagaftrafund.org',
        'vkjuvvadi@afmsagaftrafund.org',
        'vkrmuppidi@afmsagaftrafund.org',
        'psagar@afmsagaftrafund.org',
        'sradhamolla@afmsagaftrafund.org',
        'bpnunna@afmsagaftrafund.org',
        'sbade@afmsagaftrafund.org',
        'dkundo@afmsagaftrafund.org',
        'rkrishnavalluri@afmsagaftrafund.org'
    ].join(',')

    def subject = ''
    def body = ''

    switch (type) {
        case 'approval':
            subject = "🚦 Approval Required: Deploy ${service} to ${env}"
            body = """
                <h3>🚦 Jenkins Build Approval Required</h3>
                <p><strong>Service:</strong> ${service}</p>
                <p><strong>Environment:</strong> ${env}</p>
                <p><strong>Build Number:</strong> ${buildNumber}</p>
                <p><a href="${buildUrl}">Click here to review and approve</a></p>
                <hr/>
                <p>Please approve the deployment to proceed.</p>
            """
            break

        case 'success':
            subject = "✅ Success: ${service} deployed to ${env}"
            body = """
                <h3>✅ Deployment Successful</h3>
                <p><strong>Service:</strong> ${service}</p>
                <p><strong>Environment:</strong> ${env}</p>
                <p><strong>Build Number:</strong> ${buildNumber}</p>
                <p><a href="${buildUrl}">View Pipeline</a></p>
            """
            break

        case 'failure':
            subject = "❌ Failed: ${service} build/deploy in ${env}"
            body = """
                <h3>❌ Build or Deployment Failed</h3>
                <p><strong>Service:</strong> ${service}</p>
                <p><strong>Environment:</strong> ${env}</p>
                <p><strong>Build Number:</strong> ${buildNumber}</p>
                <p><a href="${buildUrl}">View Pipeline</a></p>
                <hr/>
                <p>Please check the logs and take necessary action.</p>
            """
            break

        default:
            subject = "ℹ️ Notification: ${service} in ${env}"
            body = "<p>No additional details.</p>"
    }

    echo "📧 Sending ${type} notification email to ${recipients}"
    mail(
        to: recipients,
        subject: subject,
        body: body,
        mimeType: 'text/html'
    )
}

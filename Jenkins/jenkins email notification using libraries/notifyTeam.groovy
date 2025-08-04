def call(Map config = [:]) {
    def env = config.env ?: 'Unknown'
    def service = config.serviceName ?: 'Unknown Service'
    def buildNumber = config.buildNumber ?: 'N/A'
    def buildUrl = config.buildUrl ?: '#'
    def type = config.type ?: 'info'
    def approver    = config.approvedBy ?: 'N/A'
    def buildUser   = config.triggeredBy ?: 'N/A'

    // Git commit info
    def commitMessage = sh(script: "git log -1 --pretty=%s", returnStdout: true).trim()
    def commitHash    = sh(script: "git log -1 --pretty=%h", returnStdout: true).trim()
    def commitAuthor  = sh(script: "git log -1 --pretty=%an", returnStdout: true).trim()                        


    // Email Recipients
   // def recipients = [
  //      'vbboya@afmsagaftrafund.org'
  //  ].join(',')

    def subject = ''
    def body = ''
    def teamsMessage = ''
    def color = '#808080'  // default gray

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
            teamsMessage = "**🔔 Deployment Approval Needed**\n\n📦 Service : **${service}**\n\n🌍 Environment : **${env}**\n\n👤 Triggered By : **${buildUser}**\n\n📝 Commit Message : **${commitMessage}**\n\n🧑‍💻 Commit Author : **${commitAuthor}**\n\n"
            color = '#FFA500'  // orange
            break

        case 'success':
            subject = "**🥳: ${service} deployed to ${env}"
            body = """
                <h3>✅ Deployment Successful</h3>
                <p><strong>Service:</strong> ${service}</p>
                <p><strong>Environment:</strong> ${env}</p>



                <p><strong>Build Number:</strong> ${buildNumber}</p>
                <p><a href="${buildUrl}">View Pipeline</a></p>
            """
            teamsMessage = "**🥳 Deployment Success**\n\n📦 Service : **${service}**\n\n🌍 Environment : **${env}**\n\n👤 Triggered By : **${buildUser}**\n\n📝 Commit Message : **${commitMessage}**\n\n🧑‍💻 Commit Author : **${commitAuthor}**\n\n"
            color = '#28a745'  // green
            break

        case 'failure':
            subject = "😭 Failed: ${service} build/deploy in ${env}"
            body = """
                <h3>❌ Build or Deployment Failed</h3>
                <p><strong>Service:</strong> ${service}</p>
                <p><strong>Environment:</strong> ${env}</p>



                <p><strong>Build Number:</strong> ${buildNumber}</p>
                <p><a href="${buildUrl}">View Pipeline</a></p>
                <hr/>
                <p>Please check the logs and take necessary action.</p>
            """
            teamsMessage = "**😭 Deployment Failed**\n\n📦 Service : **${service}**\n\n🌍 Environment : **${env}**\n\n👤 Triggered By : **${buildUser}**\n\n📝 Commit Message : **${commitMessage}**\n\n🧑‍💻 Commit Author : **${commitAuthor}**\n\n"
            color = '#dc3545'  // red
            break

        default:
            subject = "ℹ️ Notification: ${service} in ${env}"
            body = "<p>No additional details.</p>"
            teamsMessage = "ℹ️ **Notification** for ${service} in ${env}"
    }

    // Send Email
   // echo "📧 Sending ${type}  email to ${recipients}"
    // mail(
    //     to: recipients,
    //     subject: subject,
    //     body: body,
    //     mimeType: 'text/html'
    // )




    // Send Teams Notification
    def teamsWebhook = 'https://prod-13.centralindia.logic.azure.com:443/workflows/db59c2860a05449081480ba631850e88/triggers/manual/paths/invoke?api-version=2016-06-01&sp=%2Ftriggers%2Fmanual%2Frun&sv=1.0&sig=CDckBf0gKwFAC8ddpcW-rMurNuHgBnRvojiIz69CHe8'

    office365ConnectorSend(
        webhookUrl: teamsWebhook,
        message: teamsMessage,
        status: type.capitalize(),
        color: color,
        adaptiveCards: true
    )
}
 

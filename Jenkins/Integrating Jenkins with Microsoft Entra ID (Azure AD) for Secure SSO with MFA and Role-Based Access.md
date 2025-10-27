Microsoft Entra ID (Azure AD) SSO Integration with Jenkins:


---

# 🧩 **Full Jenkins + Microsoft Entra ID (Azure AD) SSO Setup Guide**

---

## 🧱 **Requirements**

Before starting, ensure you have:

* A working **Jenkins server** (HTTPS recommended)
* **Admin access** to Jenkins
* **Microsoft Entra ID (Azure AD)** admin access
* **Internet connectivity** from Jenkins to `login.microsoftonline.com`

---

# ⚙️ STEP 1 — Install the Azure AD Plugin in Jenkins

1. Go to Jenkins → **Manage Jenkins → Plugins → Available plugins**
2. Search for:

   ```
   Azure AD Plugin
   ```
3. Click **Install** and restart Jenkins.

> 🧠 The plugin uses OpenID Connect (OIDC) under the hood to integrate Microsoft Entra ID with Jenkins securely.

---

# 🪪 STEP 2 — Register Jenkins as an App in Microsoft Entra ID

1. In the **Azure Portal**, go to:
   `Microsoft Entra ID → App registrations → + New registration`

2. Fill out the form:

   | Field                        | Value                                                  |
   | ---------------------------- | ------------------------------------------------------ |
   | **Name**                     | `Jenkins SSO`                                          |
   | **Supported account types**  | *Accounts in this organizational directory only*       |
   | **Redirect URI (type: Web)** | `https://<your-jenkins-url>/securityRealm/finishLogin` |

   > Example:
   > `https://jenkins.company.com/securityRealm/finishLogin`

3. Click **Register**.

---

# 📋 STEP 3 — Get App Credentials

After registering:

1. Open the app you just created.
2. Copy:

   * **Application (client) ID**
   * **Directory (tenant) ID**
3. Go to **Certificates & secrets → + New client secret**

   * Add description: `JenkinsSecret`
   * Expiry: choose 12 or 24 months
   * Copy the **secret value** (you’ll need it in Jenkins)

> ⚠️ You cannot view this secret again after leaving the page. Save it securely.

---

# ⚙️ STEP 4 — Configure Jenkins to Use Microsoft Entra ID

1. Go to:

   ```
   Manage Jenkins → Configure Global Security
   ```

2. Under **Security Realm**, select:

   ```
   Login with Azure Active Directory
   ```

3. Fill the fields with values from Azure:

   | Field                               | Value                       |
   | ----------------------------------- | --------------------------- |
   | **Client ID**                       | `<Application (client) ID>` |
   | **Client Secret**                   | `<Client Secret Value>`     |
   | **Tenant ID**                       | `<Directory (tenant) ID>`   |
   | **Azure Environment**               | Azure Public Cloud          |
   | ✅ Allow users from this tenant only | Checked                     |

4. Scroll down → Click **Save**

✅ Jenkins is now connected to Microsoft Entra ID for authentication.

---

# ADD API Permissions

Application.Read.All	Delegated	Read applications		Yes	Granted for AFM & SAG AFTRA IPRD FUND
Directory.Read.All	Delegated	Read directory data		Yes	Granted for AFM & SAG AFTRA IPRD FUND
Directory.Read.All	Application	Read directory data		Yes	Granted for AFM & SAG AFTRA IPRD FUND
email			Delegated	View users' email address	No	Granted for AFM & SAG AFTRA IPRD FUND
Group.Read.All		Application	Read all groups			Yes	Granted for AFM & SAG AFTRA IPRD FUND
profile			Delegated	View users' basic profile	No	Granted for AFM & SAG AFTRA IPRD FUND
User.Read		Delegated	Sign in and read user profile	No	Granted for AFM & SAG AFTRA IPRD FUND
User.Read.All		Delegated	Read all users' full profiles	Yes	Granted for AFM & SAG AFTRA IPRD FUND
User.Read.All		Application	Read all users' full profiles	Yes	Granted for AFM & SAG

---


# 👥 STEP 6 — Configure Group-Based Access Control (Optional but Recommended)

This step lets you control **who gets what access** in Jenkins using Entra ID groups (e.g., Admins vs Developers).

---

## 🧩 6.1 — Add Group Claims in Azure

By default, Microsoft Entra ID tokens don’t include user group info.
We’ll enable that:

1. Go to **Azure Portal → Microsoft Entra ID → App registrations → Jenkins SSO App**
2. Go to **Token configuration → + Add group claim**

Configure it like this:

| Setting                             | Value                 |
| ----------------------------------- | --------------------- |
| **Which groups should be included** | Security groups       |
| **ID format**                       | Group name            |
| **Token type**                      | ID token              |
| ✅ Emit groups as roles              | (Optional but useful) |

Click **Add**.

> 💡 Now, when users log in, their group names will be sent to Jenkins.

---

## 🧩 6.2 — Create Security Groups in Entra ID

Go to **Microsoft Entra ID → Groups → + New group**

Create groups like:

| Group Name         | Purpose        |
| ------------------ | -------------- |
| Jenkins-Admins     | Full access    |
| Jenkins-Developers | Limited access |

Add members accordingly.

---

## 🧩 6.3 — Enable Group-Based Authorization in Jenkins

1. Go to:

   ```
   Manage Jenkins → Configure Global Security
   ```

2. Under **Authorization**, select:

   ```
   Azure Active Directory Matrix-based security
   ```

3. Add your Entra ID groups exactly as named:

   ```
   Jenkins-Admins
   Jenkins-Developers
   ```

4. Assign permissions:

   **Example setup:**

   | Group              | Permission         | Access |
   | ------------------ | ------------------ | ------ |
   | Jenkins-Admins     | Overall/Administer | ✅      |
   | Jenkins-Developers | Job/Build          | ✅      |
   | Jenkins-Developers | Job/Read           | ✅      |
   | Jenkins-Developers | Job/Workspace      | ✅      |

Click **Save**.

---

# 👤 STEP 5 — Test SSO Login

1. Log out of Jenkins.
2. You’ll now see a **“Sign in with Microsoft”** button.
3. Click it → you’ll be redirected to the Microsoft login page.
4. Sign in with your Entra ID account → you’ll be redirected back to Jenkins.

✅ Congratulations!
Your Jenkins SSO with Microsoft Entra ID is working 🎉

---

## 🧩 6.4 — Test Group Permissions

1. Log out.
2. Log in as a **Jenkins-Admin** user → should have full admin access.
3. Log in as a **Jenkins-Developer** user → should see limited access.

✅ Success — Jenkins now respects Entra ID group permissions!

---

# 🧾 **Summary of Values Used**

| Parameter     | Example                                                 |
| ------------- | ------------------------------------------------------- |
| Jenkins URL   | `https://jenkins.company.com`                           |
| Redirect URI  | `https://jenkins.company.com/securityRealm/finishLogin` |
| Tenant ID     | `f1a23b4c-xxxx-xxxx-xxxx-9876543210ab`                  |
| Client ID     | `b12c34d5-xxxx-xxxx-xxxx-123456abcdef`                  |
| Client Secret | `SuperSecretValue123!`                                  |

---

# 🧠 **Troubleshooting Tips**

| Problem                            | Cause                     | Fix                                |
| ---------------------------------- | ------------------------- | ---------------------------------- |
| ❌ “Redirect URI mismatch”          | URI not matching in Azure | Match exactly with Jenkins         |
| ❌ “invalid_client”                 | Wrong Client ID or Secret | Verify Azure credentials           |
| ❌ 403 after login                  | No permissions set        | Give matrix-based permissions      |
| ❌ Groups not showing               | Group claim missing       | Add “Group claims” in token config |
| ❌ Shows UUID instead of group name | Wrong ID format           | Use “Group name” in claim settings |

---

# 🧰 **Bonus Tip: Default Access**

If you want all logged-in users to at least view Jenkins:

* Add a “logged-in users” entry in the Matrix.
* Grant **Overall → Read** and **Job → Read**.

---

# 🔒 **Security Best Practices**

✅ Use HTTPS for Jenkins
✅ Rotate the Azure client secret regularly
✅ Restrict access to known domains
✅ Enable “Allow users from this tenant only”
✅ Use Entra groups for fine-grained role control

---

# 🖼️ **Architecture Overview**

```
+---------------------+          +----------------------------+
| Jenkins Server      |  <-----> | Microsoft Entra ID (Azure) |
| (Azure AD Plugin)   |  OIDC    | App Registration (OIDC)    |
+---------------------+          +----------------------------+
        |                                  |
        |   Login redirect + token         |
        +----------------------------------+
                    |
                    v
           Jenkins validates token
             ↳ assigns permissions
             ↳ loads Entra groups
```

---

✅ **Result:**

* Microsoft Entra ID provides authentication (SSO)
* Jenkins maps Entra groups to permissions
* Secure, centralized, and enterprise-ready login system

---


	 
 

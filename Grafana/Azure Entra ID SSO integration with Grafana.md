---

# 📘 **Documentation: Azure AD SSO Integration with Grafana using Managed Identity and Azure Key Vault**

---

## 🧩 **Overview**

This document provides a **step-by-step implementation guide** to integrate **Grafana** with **Microsoft Entra ID (Azure AD)** for Single Sign-On (SSO), and securely retrieve credentials (`CLIENT_ID`, `CLIENT_SECRET`, and SMTP password) from **Azure Key Vault** using the **VM’s Managed Identity**.

---

## ⚙️ **Architecture Summary**

| Component                     | Description                                                              |
| ----------------------------- | ------------------------------------------------------------------------ |
| **Grafana VM**                | Hosts Grafana server. Managed Identity enabled.                          |
| **Azure Key Vault**           | Stores secrets (Client ID, Client Secret, SMTP Password).                |
| **Azure AD App Registration** | Acts as OAuth provider for Grafana authentication.                       |
| **Systemd Service**           | Fetches secrets from Key Vault on startup and injects them into Grafana. |

---

## 🧭 **Implementation Steps**

---

### 🔁 **Step 1: Register Grafana in Azure Entra ID**

1. Navigate to **Azure Portal** → **Microsoft Entra ID** → **App registrations** → **New registration**
2. Fill details:

   * **Name:** `Grafana`
   * **Redirect URI**

     * **Type:** Web
     * **Value:** `https://monitoring.afmsagaftrafund.org/login/generic_oauth`
3. Click **Register**.

---

### 🔑 **Step 2: Create a Client Secret**

1. Open your **Grafana App Registration** → **Certificates & Secrets**
2. Click **New client secret**
3. Add description and expiry duration
4. Copy the **secret value** — it will only be shown once!

---

### 📋 **Step 3: Note Down Required Values**

| Parameter       | Source                      | Example                                                               |
| --------------- | --------------------------- | --------------------------------------------------------------------- |
| `client_id`     | App registration → Overview | `xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx`                                |
| `client_secret` | Certificates & Secrets      | (secret value)                                                        |
| `tenant_id`     | Azure AD → Overview         | `xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx`                                |
| `auth_url`      | Constructed                 | `https://login.microsoftonline.com/<tenant_id>/oauth2/v2.0/authorize` |
| `token_url`     | Constructed                 | `https://login.microsoftonline.com/<tenant_id>/oauth2/v2.0/token`     |

---

### 4️⃣ **Add Redirect URI for Logout (Optional)**

1. Go to **Authentication** → **Add URI**
2. Add:
   `https://<your-grafana-domain>/logout`
3. Click **Save**

---

### 5️⃣ **Assign Users / Groups to Grafana**

1. Go to **Enterprise Applications** → Select your **Grafana SSO app**
2. Under **Users and groups**, click **Add user/group**
3. Assign appropriate users or groups who should access Grafana

---

### 6️⃣ **Configure Grafana for Azure AD OAuth**

Edit the configuration file:

```bash
sudo vim /etc/grafana/grafana.ini
```

Update with the following:

```ini
[server]
root_url = https://<your-grafana-domain>

[auth.generic_oauth]
enabled = true
name = Azure AD
allow_sign_up = true
client_id = ${CLIENT_ID}
client_secret = ${CLIENT_SECRET}
scopes = openid email profile
auth_url = https://login.microsoftonline.com/<YOUR_TENANT_ID>/oauth2/v2.0/authorize
token_url = https://login.microsoftonline.com/<YOUR_TENANT_ID>/oauth2/v2.0/token
api_url = https://graph.microsoft.com/oidc/userinfo
role_attribute_path = contains(groups[*], '<group_id_of_admins>') && 'Admin' || contains(groups[*], '<group_id_of_editors>') && 'Editor'

org_mapping = [ "<group_id_of_admins>:Main Org.:Admin", "<group_id_of_editors>:Main Org.:Editor" ]

[smtp]
enabled = true
host = smtp.cloudmail.email:587
user = coda@afmsagaftrafund.org
password = ${CODADEV_SMTP_PASSWORD}
from_address = coda@afmsagaftrafund.org
from_name = Grafana Alerts
skip_verify = false

[auth]
disable_login_form = true
oauth_auto_login = true

[auth.basic]
enabled = false
```

---

## 🔐 **Fetching Secrets Securely from Azure Key Vault**

### **1️⃣ Prerequisites**

* Grafana VM has **System-Assigned Managed Identity** enabled.
* Azure Key Vault contains:

  * `grafana-client-id`
  * `grafana-sso-client-secret`
  * `CODADEVSMTPPASSWORD`
* Managed Identity has **Get** and **List** secret permissions.

---

### **2️⃣ Grant Key Vault Access to VM Managed Identity**

Get the VM’s identity object ID:

```bash
az vm show \
  --resource-group <RG_NAME> \
  --name <VM_NAME> \
  --query identity.principalId \
  --output tsv
```

Assign permissions:

```bash
az keyvault set-policy \
  --name <YOUR_KEYVAULT_NAME> \
  --object-id <VM_ManagedIdentity_ObjectID> \
  --secret-permissions get list
```

---

### **3️⃣ Validate Access**

SSH into the Grafana VM and run:

```bash
TENANT_ID=$(az keyvault secret show --vault-name <YOUR_KEYVAULT_NAME> --name TENANT_ID --query value -o tsv --identity)
CLIENT_ID=$(az keyvault secret show --vault-name <YOUR_KEYVAULT_NAME> --name CLIENT_ID --query value -o tsv --identity)
CLIENT_SECRET=$(az keyvault secret show --vault-name <YOUR_KEYVAULT_NAME> --name CLIENT_SECRET --query value -o tsv --identity)
```

✅ If you see secret values printed, access works correctly.

---

### **4️⃣ Create Secret Fetch Script**

Create the file:

```bash
sudo vim /usr/local/bin/grafana-azuread-secrets.sh
```

Add:

```bash
#!/bin/bash
set -euo pipefail

echo "[$(date)] Fetching Grafana secrets from Azure Key Vault..."

GRAFANA_CLIENT_ID=$(az keyvault secret show --vault-name CODA-PROD-Azure-KeyVault --name grafana-client-id --query value -o tsv)
GRAFANA_CLIENT_SECRET=$(az keyvault secret show --vault-name CODA-PROD-Azure-KeyVault --name grafana-sso-client-secret --query value -o tsv)
CODADEV_SMTP_PASSWORD=$(az keyvault secret show --vault-name CODA-PROD-Azure-KeyVault --name CODADEVSMTPPASSWORD --query value -o tsv)

cat <<EOF >/run/grafana-secrets.env
CLIENT_ID=${GRAFANA_CLIENT_ID}
CLIENT_SECRET=${GRAFANA_CLIENT_SECRET}
CODADEV_SMTP_PASSWORD=${CODADEV_SMTP_PASSWORD}
EOF

chmod 600 /run/grafana-secrets.env
echo "[$(date)] Secrets prepared in memory for Grafana."
```

Make executable:

```bash
sudo chmod +x /usr/local/bin/grafana-azuread-secrets.sh
```

---

### **5️⃣ Create Systemd Service to Fetch Secrets**

```bash
sudo vim /etc/systemd/system/grafana-fetch-secrets.service
```

Add:

```ini
[Unit]
Description=Fetch Grafana secrets from Azure Key Vault
Before=grafana-server.service
Wants=grafana-server.service

[Service]
Type=oneshot
ExecStart=/usr/local/bin/grafana-azuread-secrets.sh
ExecStop=/usr/bin/rm -f /run/grafana-secrets.env
RemainAfterExit=true

[Install]
WantedBy=grafana-server.service
```
### **NOTE: "grafana-secrets.env" file will only be available if "grafana-fetch-secrets.service" is up and running **
---

### **6️⃣ Attach Environment File to Grafana Service**

```bash
sudo mkdir -p /etc/systemd/system/grafana-server.service.d
sudo vim /etc/systemd/system/grafana-server.service.d/env.conf
```

Add:

```ini
[Service]
EnvironmentFile=/run/grafana-secrets.env
```

---

### **7️⃣ Reload and Start Services**

```bash
sudo systemctl daemon-reload
sudo systemctl enable grafana-fetch-secrets.service
sudo systemctl start grafana-fetch-secrets.service
sudo systemctl start grafana-server
sudo systemctl enable grafana-server
```

---

### **8️⃣ Verify and Troubleshoot**

Check environment file:

```bash
sudo cat /run/grafana-secrets.env
```

View logs:

```bash
sudo journalctl -u grafana-fetch-secrets.service -n 20
sudo journalctl -u grafana-server.service -n 20
```

Restart services if needed:

```bash
sudo systemctl restart grafana-server
sudo systemctl restart grafana-fetch-secrets.service
```

---

## 🧠 **Security Notes**

* `/run` is a **RAM-backed** filesystem — data is cleared on reboot.
* The environment file is created with **root-only permissions (chmod 600)**.
* Secrets are never stored on disk persistently.
* Grafana automatically loads secrets into environment variables during startup.

---

## ✅ **Outcome**

After completing this process:

* Grafana authenticates users via **Azure AD SSO**.
* Secrets (`CLIENT_ID`, `CLIENT_SECRET`, `SMTP_PASSWORD`) are **securely pulled at boot** from **Azure Key Vault**.
* **No static credentials** are stored on the VM.

---


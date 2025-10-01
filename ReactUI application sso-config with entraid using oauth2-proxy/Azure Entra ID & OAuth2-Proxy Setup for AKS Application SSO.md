Here’s the updated detailed document with your final steps added:

---

# Azure Entra ID & OAuth2-Proxy Setup for AKS Application SSO

This document outlines the end-to-end steps to configure Azure Entra ID (Azure AD) App Registration and integrate it with an AKS-hosted application using **oauth2-proxy**.

---

## **Step 0: Azure Entra ID (App Registration)**

1. **Register a new application in Azure Portal**:

   * Navigate to: **Azure Portal → Microsoft Entra ID → App registrations → New registration**
   * Fill in:

     * **Name:** `testapp-aks-sso`
     * **Supported account types:** Choose the tenant type that applies to your organization.
     * **Redirect URI (Web):** `https://app2.10.0.2.10.nip.io/oauth2/callback`

2. **Create a Client Secret**:

   * Go to **Certificates & secrets → New client secret**
   * Copy the secret value immediately (this is your `CLIENT_SECRET`).

3. **Take note of key IDs**:

   * **Application (client) ID** → `CLIENT_ID`
   * **Directory (tenant) ID** → `TENANT_ID`
   * Under **Authentication**, ensure:

     * The redirect URI is listed.
     * ID tokens are enabled if your SSO requires them.

---

## **Step 1: Create Kubernetes Secrets**

Create a Kubernetes secret for **oauth2-proxy** containing the client and cookie secrets:

```bash
kubectl create secret generic oauth2-proxy-secrets \
  -n test-ns \
  --from-literal=client-secret='<YOUR-CLIENT-SECRET>' \
  --from-literal=cookie-secret='<YOUR-COOKIE-SECRET>'
```

Verify the secret:

```bash
kubectl get secret oauth2-proxy-secrets -n test-ns -o yaml
```

Generate cookie secret:

```bash
openssl rand -base64 32 | tr -d '=+/ ' | cut -c1-32
```
---

## **Step 2: Configure oauth2-proxy Helm Chart**

Create or edit the `oauth2-proxy.values.yaml` file:

```yaml
# oauth2-proxy.values.yaml
replicaCount: 1

image:
  repository: quay.io/oauth2-proxy/oauth2-proxy
  tag: v7.6.0
  pullPolicy: IfNotPresent

config:
  clientID: "<APP-REG-CLIENT-ID>"
  cookie_domain: "<YOUR-COOKIE-DOMAIN>" # e.g., 'codatest.afmsagaftrafund.org'
  upstreams:
    - "http://<APP-SVC>.<APP-NAMESPACE>.svc.cluster.local:<APP_PORT>/"
  pass-basic-auth: true
  pass-access-token: true
  configFile: |
    provider="azure"
    oidc_issuer_url="https://login.microsoftonline.com/<TENANT_ID>/v2.0"
    scope="openid email profile"
    email_domains = ["afmsagaftrafund.org"]
    skip_provider_button = true
    redirect_url = "https://<APP-DOMAIN-NAME>/oauth2/callback"
    cookie_expire = "8h"                # Session timeout
    cookie_refresh = "1h"               # Optional: refresh session every 1h
    #logout_url = "https://login.microsoftonline.com/<TENANT_ID>/oauth2/v2.0/logout?post_logout_redirect_uri=https://<APP-DOMAIN-NAME>"

extraEnv:
  - name: OAUTH2_PROXY_CLIENT_SECRET
    valueFrom:
      secretKeyRef:
        name: oauth2-proxy-secrets
        key: client-secret
  - name: OAUTH2_PROXY_COOKIE_SECRET
    valueFrom:
      secretKeyRef:
        name: oauth2-proxy-secrets
        key: cookie-secret

ingress:
  enabled: true
  className: "nginx"
  annotations:
    nginx.ingress.kubernetes.io/ssl-redirect: "true"
    nginx.ingress.kubernetes.io/force-ssl-redirect: "true"
    nginx.ingress.kubernetes.io/proxy-buffer-size: "8k"
    nginx.ingress.kubernetes.io/proxy-buffers-number: "4"
  hosts:
    - <APP-DOMAIN-NAME>
  path: /oauth2
  pathType: Prefix
  tls:
    - secretName: <APP-TLS-SECRET>
      hosts:
        - <APP-DOMAIN-NAME>
```

---

## **Step 3: Install/Upgrade oauth2-proxy via Helm**

1. Add and update the Helm repo:

```bash
helm repo add oauth2-proxy https://oauth2-proxy.github.io/manifests
helm repo update
```

2. Install **oauth2-proxy**:

```bash
helm install oauth2-proxy oauth2-proxy/oauth2-proxy -f oauth2-proxy.values.yaml -n <APP-NAMESPACE>
```

3. Upgrade **oauth2-proxy** if needed:

```bash
helm upgrade oauth2-proxy oauth2-proxy/oauth2-proxy -f oauth2-proxy.values.yaml -n <APP-NAMESPACE>
```

4. Uninstall (if required):

```bash
helm uninstall oauth2-proxy -n <APP-NAMESPACE>
```

---

## **Step 4: Configure Ingress for Application**

Create an Ingress YAML for your app with oauth2-proxy authentication:

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: app-ingress
  namespace: <APP-NAMESPACE>
  annotations:
    nginx.ingress.kubernetes.io/auth-url: "http://oauth2-proxy.<APP-NAMESPACE>.svc.cluster.local/oauth2/auth"
    nginx.ingress.kubernetes.io/auth-signin: "https://<APP-DOMAIN-NAME>/oauth2/start?rd=$request_uri"
    nginx.ingress.kubernetes.io/auth-response-headers: "X-Auth-Request-Access-Token,Authorization,X-Auth-Request-User,X-Auth-Request-Email, X-Auth-Request-Preferred-Username"
    nginx.ingress.kubernetes.io/proxy-buffer-size: "8k"
    nginx.ingress.kubernetes.io/proxy-buffers-number: "4"
spec:
  ingressClassName: nginx
  tls:
    - hosts:
        - <APP-DOMAIN-NAME>
      secretName: <APP-TLS-SECRET>
  rules:
    - host: <APP-DOMAIN-NAME>
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: <APP-SVC>
                port:
                  number: <APP-PORT>
```

---

## **Step 5: Apply Ingress and Test**

1. Apply the Ingress configuration:

```bash
kubectl apply -f ingress.yaml
```

2. Test access:

* Open a browser and navigate to:

  ```
  https://<APP-DOMAIN-NAME>/
  ```
* You should be redirected to Azure Entra ID login and, upon success, reach your application.

---
## **Step 6: Apply TLS secrets(Optional)**

1. Apply the Ingress configuration:

```bash
kubectl create secret tls wildcard-secret --cert="D:\Aftra\Certificates\_.afmsagaftrafund.org - 2025\ec4c331dee580282.crt" --key="D:\Aftra\Certificates\_.afmsagaftrafund.org - 2025\_.afmsagaftrafund.org.private.key" -n codadev
 
kubectl create secret generic ca-secret --from-file="D:\Aftra\Certificates\_.afmsagaftrafund.org - 2025\ec4c331dee580282.pem" --from-file="D:\Aftra\Certificates\_.afmsagaftrafund.org - 2025\gd-g2_iis_intermediates.p7b" -n codadev
```
---


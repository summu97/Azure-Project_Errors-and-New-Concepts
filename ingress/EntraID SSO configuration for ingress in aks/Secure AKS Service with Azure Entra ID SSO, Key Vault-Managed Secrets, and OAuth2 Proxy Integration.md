0) Azure Entra ID (App Registration) — What to set

Register new app in Azure Portal → Microsoft Entra ID → App registrations → New registration
Name: myapp-aks-sso
Supported account types: choose your tenant type
Redirect URI (type Web): https://myapp.example.com/oauth2/callback
Under Certificates & secrets → New client secret → copy value (CLIENT_SECRET)

Note: Application (client) ID → CLIENT_ID
Note: Directory (tenant) ID → TENANT_ID
Under Authentication ensure the redirect URI is allowed and ID tokens enabled if required.

---
Step 1. Create Secrets in Key Vault

az keyvault secret set --vault-name <your-keyvault> --name "OAUTH2-CLIENT-SECRET" --value "<your-client-secret>"

To generate the cookie secret: openssl rand -base64 32
az keyvault secret set --vault-name <your-keyvault> --name "OAUTH2-COOKIE-SECRET" --value "<32-byte-random-base64>"

---
Step 2. Install CSI Driver + Azure Provider

helm repo add csi-secrets-store-provider-azure https://azure.github.io/secrets-store-csi-driver-provider-azure/charts
helm repo update
helm install csi-secrets-store-provider-azure \
  csi-secrets-store-provider-azure/csi-secrets-store-provider-azure \
  --namespace kube-system

---
Step 3. Create SecretProviderClass

This tells CSI Driver which secrets to pull from Key Vault:
apiVersion: secrets-store.csi.x-k8s.io/v1
kind: SecretProviderClass
metadata:
  name: oauth2-secrets
  namespace: default
spec:
  provider: azure
  parameters:
    keyvaultName: "<your-keyvault>"
    objects: |
      array:
        - |
          objectName: OAUTH2-CLIENT-SECRET
          objectType: secret
        - |
          objectName: OAUTH2-COOKIE-SECRET
          objectType: secret
    tenantId: "<your-tenant-id>"
  secretObjects:
    - secretName: oauth2-secret
      type: Opaque
      data:
        - objectName: OAUTH2-CLIENT-SECRET
          key: clientSecret
        - objectName: OAUTH2-COOKIE-SECRET
          key: cookieSecret

This will create a Kubernetes secret called oauth2-secret with two keys:
clientSecret
cookieSecret

---
Step 4. oauth2-proxy values.yaml

# oauth2-proxy-values.yaml
config:
  # Azure Entra (OIDC) setup
  clientID: "<CLIENT_ID>"   # <-- from App Registration
  provider: "oidc"
  oidcIssuerURL: "https://login.microsoftonline.com/<TENANT_ID>/v2.0"

  # Restrict allowed domains (optional)
  emailDomains:
    - "*"   # allow all users; replace with yourdomain.com if you want restriction

  # Where oauth2-proxy listens
  cookieSecure: true
  cookieSameSite: "lax"
  cookieDomains:
    - "myapp.example.com"
  cookieExpire: "168h"

# Pull client secret & cookie secret from Kubernetes Secret created by CSI driver
extraEnv:
  - name: OAUTH2_PROXY_CLIENT_SECRET
    valueFrom:
      secretKeyRef:
        name: oauth2-secret
        key: clientSecret
  - name: OAUTH2_PROXY_COOKIE_SECRET
    valueFrom:
      secretKeyRef:
        name: oauth2-secret
        key: cookieSecret

ingress:
  enabled: true
  className: nginx
  hosts:
    - host: myapp.example.com
      paths:
        - path: /oauth2
          pathType: Prefix

resources:
  requests:
    cpu: 100m
    memory: 128Mi
  limits:
    cpu: 200m
    memory: 256Mi


---
Step 5. Deploy

helm repo add oauth2-proxy https://oauth2-proxy.github.io/manifests
helm repo update
helm install oauth2-proxy oauth2-proxy/oauth2-proxy -f oauth2-proxy-values.yaml -n default

---
Result:
Secrets live only in Key Vault.
They’re synced securely into AKS via CSI driver.
oauth2-proxy uses them without ever writing them into Git.

---
Example Ingress Manifest:

apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: myapp-ingress
  namespace: default
  annotations:
    nginx.ingress.kubernetes.io/auth-url: "https://myapp.example.com/oauth2/auth"
    nginx.ingress.kubernetes.io/auth-signin: "https://myapp.example.com/oauth2/start?rd=$request_uri"
    nginx.ingress.kubernetes.io/auth-response-headers: X-Auth-Request-Email, X-Auth-Request-User
    nginx.ingress.kubernetes.io/configuration-snippet: |
      proxy_set_header X-Forwarded-User $auth_resp_x_auth_request_user;
      proxy_set_header X-Forwarded-Email $auth_resp_x_auth_request_email;
spec:
  ingressClassName: nginx
  tls:
    - hosts:
        - myapp.example.com
      secretName: myapp-tls-secret   # <-- replace with your TLS secret name
  rules:
    - host: myapp.example.com
      http:
        paths:
          # Protect Spring Boot backend
          - path: /
            pathType: Prefix
            backend:
              service:
                name: myapp-service   # your backend service name
                port:
                  number: 80
          # Route /oauth2/* to oauth2-proxy
          - path: /oauth2
            pathType: Prefix
            backend:
              service:
                name: oauth2-proxy
                port:
                  number: 80
------------------------------
End-to-End Flow Now:
User browses → https://myapp.example.com.
NGINX checks /oauth2/auth → oauth2-proxy says “not logged in.”
User is redirected → Entra ID login page.
User signs in → Entra issues ID token.
oauth2-proxy validates token, sets secure cookie.
User is forwarded to Spring Boot service with headers X-Forwarded-User & X-Forwarded-Email.
Service is now available only for authenticated users.
-------------------------------
By applying below ingress Spring Boot app receives Entra ID claims (like email, groups) as headers automatically? That way, your backend can enforce RBAC easily.

Great ✅ Let’s update your myapp-ingress.yaml so that your Spring Boot backend will receive Entra ID claims (email, user ID, etc.) from oauth2-proxy through headers.

Great ✅ Let’s update your `myapp-ingress.yaml` so that your Spring Boot backend will **receive Entra ID claims (email, user ID, etc.)** from `oauth2-proxy` through headers.

Here’s the improved ingress manifest:

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: myapp-ingress
  namespace: default
  annotations:
    kubernetes.io/ingress.class: nginx

    # 🔐 Authentication via oauth2-proxy
    nginx.ingress.kubernetes.io/auth-url: "https://myapp.example.com/oauth2/auth"
    nginx.ingress.kubernetes.io/auth-signin: "https://myapp.example.com/oauth2/start?rd=$request_uri"

    # Pass useful headers from oauth2-proxy → backend
    nginx.ingress.kubernetes.io/auth-response-headers: |
      X-Auth-Request-User,
      X-Auth-Request-Email,
      X-Auth-Request-Groups

    nginx.ingress.kubernetes.io/configuration-snippet: |
      proxy_set_header X-Forwarded-User $auth_resp_x_auth_request_user;
      proxy_set_header X-Forwarded-Email $auth_resp_x_auth_request_email;
      proxy_set_header X-Forwarded-Groups $auth_resp_x_auth_request_groups;

spec:
  ingressClassName: nginx
  tls:
    - hosts:
        - myapp.example.com
      secretName: myapp-tls-secret   # <-- replace with your TLS secret
  rules:
    - host: myapp.example.com
      http:
        paths:
          # Protect your backend service
          - path: /
            pathType: Prefix
            backend:
              service:
                name: myapp-service   # <-- your backend service
                port:
                  number: 80
          # Route /oauth2/* to oauth2-proxy
          - path: /oauth2
            pathType: Prefix
            backend:
              service:
                name: oauth2-proxy
                port:
                  number: 80
```

---

### 🔑 What changed

* Added `X-Auth-Request-Groups` so you can use Azure AD group claims for RBAC.
* Backend (`myapp-service`) now receives these headers:

  * `X-Forwarded-User` → UPN / object ID
  * `X-Forwarded-Email` → user’s email
  * `X-Forwarded-Groups` → Entra groups (if enabled in App Registration claims)

---

✅ With this setup:

* User goes to `https://myapp.example.com`
* NGINX enforces login via `oauth2-proxy`
* After successful login, headers are injected into the Spring Boot request
* Your app can trust those headers to enforce user identity & RBAC.

---

Do you want me to also show you how to **configure Entra ID App Registration to emit group claims** (so that `X-Forwarded-Groups` actually contains user’s Entra ID groups)?


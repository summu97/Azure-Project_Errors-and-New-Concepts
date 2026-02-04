## 🚀 NGINX Ingress ➜ Gateway API (Envoy Gateway) in Codatest cluster

![Kubernetes](https://img.shields.io/badge/Kubernetes-Gateway%20API-blue)
![Envoy](https://img.shields.io/badge/Envoy-Gateway-green)
![Status](https://img.shields.io/badge/Migration-SUCCESS-brightgreen)

---

## 🎯 Objective

Migrate **Codatest Kubernetes cluster** from **NGINX Ingress** to **Gateway API using Envoy Gateway** while:

✅ Preserving the **existing LoadBalancer IP (10.0.8.57)**  
✅ Avoiding DNS changes  
✅ Ensuring zero or near-zero downtime  
✅ Improving routing clarity, security, and scalability  

---

## 🧠 Why Gateway API?

| NGINX Ingress | Gateway API (Envoy) |
|--------------|---------------------|
| Annotation-driven | Strongly typed CRDs |
| Controller-centric | Role-based architecture |
| Hard to scale | Cloud-native & extensible |
| Limited observability | Envoy-level telemetry |

---

## 🏗️ Architecture Overview

## 🔴 BEFORE (Ingress)
Client
↓
Azure LoadBalancer (10.0.8.57)
↓
NGINX Ingress Controller
↓
Ingress Rules (Annotations)
↓
Services


## 🟢 AFTER (Gateway API)

Client
↓
Azure LoadBalancer (10.0.8.57) ← SAME IP
↓
Envoy Gateway (Data Plane)
↓
Gateway (Listeners 80/443)
↓
HTTPRoutes (Host-based routing)
↓
Services


📌 **No DNS change required**

---
```
Goal: Migrate existing NGINX Ingress–based routing to Gateway API with Envoy,
without changing DNS or LoadBalancer IP (10.0.8.57),
and prove correctness with commands and validations.
```
📌 0️⃣ Executive Summary 

✅ Migrated 13+ backend services + React UI

✅ Reused existing internal LoadBalancer IP

✅ Zero DNS change

✅ HTTP → HTTPS enforced

✅ Clear separation of infra vs routing

✅ Rollback-ready (Ingress backups preserved)
## 🛠️ Step-by-Step Migration (Deep Dive)

---
### 🧠 1️⃣ BEFORE MIGRATION – PLANNING (MOST IMPORTANT)
### 🔍 What a DevOps Engineer Must Understand First
### 1️⃣ Backup Existing Ingress (Safety First)
| Question                    | Answer                      |
| --------------------------- | --------------------------- |
| Current ingress controller? | NGINX (Helm-based)          |
| Load balancer type?         | Azure Internal LoadBalancer |
| IP in use?                  | `10.0.8.57`                 |
| TLS handling?               | NGINX terminating TLS       |
| Routing type?               | Host-based                  |
| Downtime allowed?           | ❌ No                       |
----------------------------------------------------------------
📂 1.1 Inventory Existing Ingress (Reality Check)
```
kubectl get ingress -A
```
```
NAMESPACE   NAME           CLASS           HOSTS                     ADDRESS     PORTS     AGE
codatest    oauth2-proxy   nginx    testcoda.afmsagaftrafund.org     10.0.8.57   80, 443   125d
codatest    react-webapp   <none>   testcoda.afmsagaftrafund.org     10.0.8.57   80        3d14h
k8s-lab     demo-ingress   nginx    demo.k8s-lab.internal            10.0.8.57   80, 443   86d

```

## 👉 Multiple ingresses sharing the SAME IP (10.0.8.57)
## 👉 Indicates single NGINX controller

# 📂 1.2 Backup Everything (Rollback Safety)

✔ Ensures instant rollback
✔ Required for audit & comparison
```
kubectl get ingress -A -o yaml > ingress-backup.yaml
kubectl get ingress -n codatest -o yaml > ingress-backend-backup.yaml
kubectl get svc -n ingress -o yaml > nginx-lb-backup.yaml
```
```
Why This Is Mandatory
Rollback in minutes
Audit proof
Comparison reference
```
# 📂 1.3 Understand Old Routing (Ingress → Services)
## Example (Old Ingress)
```
host: testparticipant.afmsagaftrafund.org
paths:
- path: /
  backend:
    service:
      name: participantservice
      port: 8086
```
```
🧠 Key Observations
Host-based routing
TLS via wildcard-secret
Heavy NGINX annotations
Hard to reason at scale
```
# 🧩 2️⃣ MIGRATION DESIGN (ON PAPER FIRST)
## 🎯 Design Decisions

| Area            | Decision              |
| --------------- | --------------------- |
| Controller      | Envoy Gateway         |
| Routing API     | Gateway API           |
| IP reuse        | ✅ Yes                |
| TLS             | Gateway terminates    |
| Backend routing | HTTPRoute             |
| Redirect        | Native Gateway filter |


# 🗂️ 2.1 File Mapping (Old → New)
```
| Old (Ingress)            | New (Gateway API) |
| ------------------------ | ----------------- |
| ingress-nginx-controller | Envoy Gateway     |
| Ingress                  | HTTPRoute         |
| ingressClass             | GatewayClass      |
| annotations              | Typed filters     |
| TLS in Ingress           | TLS in Gateway    |

```
#🧩 Environment Context
| Component          | Version / Detail                         |
| ------------------ | ---------------------------------------- |
| Kubernetes         | v1.30+ (AKS)                             |
| Ingress (Old)      | ingress-nginx                            |
| Gateway Controller | Envoy Gateway v1.6                       |
| Gateway API        | v1.4                                     |
| LoadBalancer Type  | Azure Internal                           |
| Target LB IP       | `10.0.8.57`                              |
| TLS                | Wildcard certificate (`wildcard-secret`) |


# ⚙️ 3️⃣ EXECUTION – STEP BY STEP (WHAT I DID)
## 2️⃣ Install Gateway API CRDs
```
kubectl apply -f https://github.com/envoyproxy/gateway/releases/download/v1.6.0/install.yaml
```
✔ Adds GatewayClass, Gateway, HTTPRoute
```
If any issues:
2️⃣ Apply using server-side apply:

kubectl apply --server-side --force-conflicts -f envoy-gateway.yaml

✅ Verification
kubectl get crd | grep gateway.networking.k8s.io
kubectl get crd | grep envoyproxy


✔ All Gateway API CRDs installed
✔ All Envoy CRDs installed
✔ No annotation errors
```



# 3️⃣ Install Envoy Gateway Controller
```
kubectl apply -f manifests/envoy-gateway.yaml
kubectl get pods -n envoy-gateway-system
```
✔ Controller running
✔ Data plane ready

# 3.3 Create GatewayClass
📄 gatewayclass-envoy.yaml
```
apiVersion: gateway.networking.k8s.io/v1
kind: GatewayClass
metadata:
  name: envoy
spec:
  controllerName: gateway.envoyproxy.io/gatewayclass-controller

```
# 3.4 Create Gateway (HTTP + HTTPS)
📄 gateway-envoy.yaml
```
apiVersion: gateway.networking.k8s.io/v1
kind: Gateway
metadata:
  name: coda-gateway
  namespace: codatest
spec:
  gatewayClassName: envoy
  listeners:
  - name: http
    protocol: HTTP
    port: 80
  - name: https
    protocol: HTTPS
    port: 443
    tls:
      mode: Terminate
      certificateRefs:
      - kind: Secret
        name: wildcard-secret
```
🔑 3.5 Allow Gateway to Read TLS Secret
📄 referencegrant-wildcard-secret.yaml
```
apiVersion: gateway.networking.k8s.io/v1beta1
kind: ReferenceGrant
metadata:
  name: allow-envoy-gateway-wildcard-secret
  namespace: codatest
spec:
  from:
  - group: gateway.networking.k8s.io
    kind: Gateway
    namespace: envoy-gateway-system
  to:
  - group: ""
    kind: Secret
    name: wildcard-secret
```
# 🔁 3.6 Reuse Existing LoadBalancer IP (CRITICAL STEP)
```
kubectl get svc envoy-codatest-coda-gateway-8b40a407 \
  -n envoy-gateway-system \
  -o yaml > envoy-lb-backup.yaml

```
📄 envoy-lb-backup.yaml
```
apiVersion: v1
kind: Service
metadata:
  name: envoy-codatest-coda-gateway-8b40a407
  namespace: envoy-gateway-system
  annotations:
    service.beta.kubernetes.io/azure-load-balancer-internal: "true"
spec:
  type: LoadBalancer
  loadBalancerIP: 10.0.8.57
  selector:
    app.kubernetes.io/component: proxy
    app.kubernetes.io/managed-by: envoy-gateway
    app.kubernetes.io/name: envoy
    gateway.envoyproxy.io/owning-gateway-name: coda-gateway
  ports:
  - name: http-80
    port: 80
    targetPort: 10080
  - name: https-443
    port: 443
    targetPort: 10443
```
🧠 What This Service Actually Is

This Service is auto-created by Envoy Gateway, not manually deployed.
| Aspect  | Explanation                               |
| ------- | ----------------------------------------- |
| Type    | `LoadBalancer`                            |
| Cloud   | Azure Internal Load Balancer              |
| IP      | **10.0.8.57 (same as old NGINX Ingress)** |
| Owner   | Envoy Gateway controller                  |
| Purpose | Expose Envoy proxy to internal network    |

👉 This Service is the actual traffic entry point after migration.
🔑 Why This File Exists (VERY IMPORTANT)

This file was backed up to:

✅ Prove zero-downtime migration
✅ Show same IP reuse (no DNS change)
✅ Enable emergency recovery
✅ Provide audit evidence

# 🎯 3.7 Migrate React UI First (Canary)
📄 httproute-reactui.yaml
```
kind: HTTPRoute
metadata:
  name: reactui-route
  namespace: codatest
spec:
  parentRefs:
  - name: coda-gateway
  hostnames:
  - testcoda.afmsagaftrafund.org
  rules:
  - matches:
    - path:
        type: PathPrefix
        value: /
    backendRefs:
    - name: react-webapp
      port: 3000
```
✅ UI verified before backend cutover
# 🌐 3.8 Migrate ALL Backend Services (Single File)
```
apiVersion: gateway.networking.k8s.io/v1
kind: HTTPRoute
metadata:
  name: all-backend-routes
  namespace: codatest
spec:
  parentRefs:
  - name: coda-gateway

  rules:

  # -------------------------------------------------
  # Service Registry (Eureka)
  # -------------------------------------------------
  - matches:
    - headers:
      - name: host
        value: testserviceregistry.afmsagaftrafund.org
      path:
        type: PathPrefix
        value: /
    backendRefs:
    - name: serviceregistry
      port: 8761

  # -------------------------------------------------
  # Participant Service
  # -------------------------------------------------
  - matches:
    - headers:
      - name: host
        value: testparticipant.afmsagaftrafund.org
      path:
        type: PathPrefix
        value: /actuator
    backendRefs:
    - name: participantservice
      port: 8086

  # -------------------------------------------------
  # Document Management
  # -------------------------------------------------
  - matches:
    - headers:
      - name: host
        value: testdocmanagement.afmsagaftrafund.org
      path:
        type: PathPrefix
        value: /actuator
    backendRefs:
    - name: docmanagement
      port: 8082

  # -------------------------------------------------
  # Cache Service
  # -------------------------------------------------
  - matches:
    - headers:
      - name: host
        value: testcache.afmsagaftrafund.org
      path:
        type: PathPrefix
        value: /actuator
    backendRefs:
    - name: cacheservice
      port: 8084

  # -------------------------------------------------
  # Spring Cloud Gateway (API Gateway)
  # -------------------------------------------------
  - matches:
    - headers:
      - name: host
        value: testcloudgateway.afmsagaftrafund.org
      path:
        type: PathPrefix
        value: /
    backendRefs:
    - name: cloudgateway
      port: 8181

  # -------------------------------------------------
  # Playlist & Title Management
  # -------------------------------------------------
  - matches:
    - headers:
      - name: host
        value: testplaylistandtitman.afmsagaftrafund.org
      path:
        type: PathPrefix
        value: /actuator
    backendRefs:
    - name: playlistandtitlemanagement
      port: 8087

  # -------------------------------------------------
  # Search Service
  # -------------------------------------------------
  - matches:
    - headers:
      - name: host
        value: testsearchservice.afmsagaftrafund.org
      path:
        type: PathPrefix
        value: /actuator
    backendRefs:
    - name: searchservice
      port: 8088

  # -------------------------------------------------
  # Security Service
  # -------------------------------------------------
  - matches:
    - headers:
      - name: host
        value: testsecurityservice.afmsagaftrafund.org
      path:
        type: PathPrefix
        value: /actuator
    backendRefs:
    - name: securityservice
      port: 8085

  # -------------------------------------------------
  # File Ingestion
  # -------------------------------------------------
  - matches:
    - headers:
      - name: host
        value: testfileingestion.afmsagaftrafund.org
      path:
        type: PathPrefix
        value: /actuator
    backendRefs:
    - name: fileingestion
      port: 8083

  # -------------------------------------------------
  # Netsuite Integration
  # -------------------------------------------------
  - matches:
    - headers:
      - name: host
        value: testnetsuiteintegration.afmsagaftrafund.org
      path:
        type: PathPrefix
        value: /actuator
    backendRefs:
    - name: netsuiteintegrationservice
      port: 8081

  # -------------------------------------------------
  # International Service
  # -------------------------------------------------
  - matches:
    - headers:
      - name: host
        value: testinternationalservice.afmsagaftrafund.org
      path:
        type: PathPrefix
        value: /actuator
    backendRefs:
    - name: internationalservice
      port: 8090


```
🔐 3.9 Enforce HTTP → HTTPS
```
apiVersion: gateway.networking.k8s.io/v1
kind: HTTPRoute
metadata:
  name: http-to-https-redirect
  namespace: codatest
spec:
  parentRefs:
  - name: coda-gateway
    sectionName: http
  rules:
  - filters:
    - type: RequestRedirect
      requestRedirect:
        scheme: https
        statusCode: 301

```
⏱️ 3.10 Backend Timeout Policy
📄 backend-traffic-policy-timeouts.yaml
```
apiVersion: gateway.envoyproxy.io/v1alpha1
kind: BackendTrafficPolicy
metadata:
  name: backend-timeouts
  namespace: codatest
spec:
  targetRefs:
  - group: gateway.networking.k8s.io
    kind: HTTPRoute
    name: all-backend-routes
  timeout:
    http:
      requestTimeout: 60s
      maxStreamDuration: 60s
```
✅ 4️⃣ VALIDATION (PROOF IT WORKS)
# 4.1 Gateway Health
```
kubectl get gateway coda-gateway -n codatest
```
✔ Accepted = True
✔ Programmed = True

# 4.2 LoadBalancer IP Validation
```
kubectl get svc -n envoy-gateway-system
```
```
 EXTERNAL-IP: 10.0.8.57
```

✔ Same IP as Ingress

# 4.3 Backend Validation (Real Traffic)
```
curl -I http://10.0.8.57 \
  -H "Host: testparticipant.afmsagaftrafund.org"
```
```
Result:

301 → HTTPS

curl -I https://testparticipant.afmsagaftrafund.org/actuator/health

200 OK

```
✔ Routing correct
✔ TLS working

#⚠️ 5️⃣ ISSUES FACED & HOW I FIXED THEM
```
Issue	Cause	Resolution
503 on cache	Redis down	App-level issue
401 on security	Auth enforced	Expected
EndpointsNotFound	Pod not ready	Verified endpoints
Redirect not working	Wrong status	Used 301
```
📊 6️⃣ BEFORE vs AFTER COMPARISON
| Aspect       | Ingress     | Gateway API |
| ------------ | ----------- | ----------- |
| Config style | Annotations | Typed CRDs  |
| Readability  | ❌ Hard     | ✅ Clear  |
| Scaling      | ❌ Risky    | ✅ Safe   |
| TLS          | Mixed        | Centralized |
| Future-proof | ❌ No        | ✅ Yes    |

# 🏁 7️⃣ FINAL CONCLUSION 
```

✅ Successfully migrated Codatest cluster from NGINX Ingress to Gateway API (Envoy)
✅ Maintained same LoadBalancer IP
✅ Zero DNS change
✅ Fully validated via live traffic
✅ Rollback preserved
```
📌 This migration is production-safe and repeatable

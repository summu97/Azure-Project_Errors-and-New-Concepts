Below is a **complete, practical, production-style guide** to apply **Istio service mesh** to your services running in the **`coda-application` namespace**.

Services: **serviceA**, **serviceB**, **serviceC**
Namespace: **`coda-application`**

---

# 🟦 **STEP 1 — Install Istio (if not installed)**

Use the official Istio CLI:

```bash
curl -L https://istio.io/downloadIstio | sh -
cd istio-1.*
export PATH=$PWD/bin:$PATH
```

Install the default profile:

```bash
istioctl install --set profile=default -y
```

This creates the **istio-system** namespace and deploys:

* istiod control plane
* ingress gateway
* egress gateway (optional)

---

# 🟦 **STEP 2 — Label the namespace for automatic sidecar injection**

```bash
kubectl label namespace coda-application istio-injection=enabled --overwrite
```

After this, **every new pod** in this namespace will automatically have an **Envoy sidecar proxy** injected.

---

# 🟦 **STEP 3 — Redeploy your services (Important)**

Istio works only when sidecars exist.

If serviceA/B/C already exist, restart them:

```bash
kubectl rollout restart deployment serviceA -n coda-application
kubectl rollout restart deployment serviceB -n coda-application
kubectl rollout restart deployment serviceC -n coda-application
```

Now your pods look like:

```
pod:
  - container: serviceA
  - container: istio-proxy (envoy)
```

This forms the **mesh**.

---

# 🟦 **STEP 4 — Create DestinationRules (required for Istio traffic)**

Create a file: **destination-rules.yaml**

```yaml
apiVersion: networking.istio.io/v1alpha3
kind: DestinationRule
metadata:
  name: serviceA
  namespace: coda-application
spec:
  host: serviceA
  trafficPolicy:
    tls:
      mode: ISTIO_MUTUAL

---
apiVersion: networking.istio.io/v1alpha3
kind: DestinationRule
metadata:
  name: serviceB
  namespace: coda-application
spec:
  host: serviceB
  trafficPolicy:
    tls:
      mode: ISTIO_MUTUAL

---
apiVersion: networking.istio.io/v1alpha3
kind: DestinationRule
metadata:
  name: serviceC
  namespace: coda-application
spec:
  host: serviceC
  trafficPolicy:
    tls:
      mode: ISTIO_MUTUAL
```

Apply it:

```bash
kubectl apply -f destination-rules.yaml
```

This forces **mTLS** between services — mesh is now secure.

---

# 🟦 **STEP 5 — Create VirtualServices (optional but recommended)**

If services talk to each other:

Example: **serviceA → serviceB → serviceC**

Create: **virtual-services.yaml**

```yaml
apiVersion: networking.istio.io/v1alpha3
kind: VirtualService
metadata:
  name: serviceA
  namespace: coda-application
spec:
  hosts:
  - serviceA
  http:
  - route:
    - destination:
        host: serviceA
        port:
          number: 80

---
apiVersion: networking.istio.io/v1alpha3
kind: VirtualService
metadata:
  name: serviceB
  namespace: coda-application
spec:
  hosts:
  - serviceB
  http:
  - route:
    - destination:
        host: serviceB
        port:
          number: 80

---
apiVersion: networking.istio.io/v1alpha3
kind: VirtualService
metadata:
  name: serviceC
  namespace: coda-application
spec:
  hosts:
  - serviceC
  http:
  - route:
    - destination:
        host: serviceC
        port:
          number: 80
```

Apply:

```bash
kubectl apply -f virtual-services.yaml
```

---

# 🟦 **STEP 6 — Add Istio Ingress Gateway (optional)**

If external traffic needs to reach serviceA:

**ingress-gateway.yaml**

```yaml
apiVersion: networking.istio.io/v1alpha3
kind: Gateway
metadata:
  name: coda-gateway
  namespace: coda-application
spec:
  selector:
    istio: ingressgateway
  servers:
  - port:
      number: 80
      name: http
      protocol: HTTP
    hosts:
    - "*"

---
apiVersion: networking.istio.io/v1alpha3
kind: VirtualService
metadata:
  name: serviceA-ingress
  namespace: coda-application
spec:
  hosts:
  - "*"
  gateways:
  - coda-gateway
  http:
  - match:
    - uri:
        prefix: /serviceA
    route:
    - destination:
        host: serviceA
        port:
          number: 80
```

Apply:

```bash
kubectl apply -f ingress-gateway.yaml
```

---

# 🟦 **STEP 7 — Verify the Mesh**

Check sidecar injection:

```bash
kubectl get pods -n coda-application -o wide
```

Each pod should show **2/2 containers**:

```
serviceA-xxx   2/2
serviceB-xxx   2/2
serviceC-xxx   2/2
```

Verify mTLS:

```bash
istioctl authn tls-check serviceA-xxx coda-application
```

Check mesh graph (if Kiali installed):

```bash
istioctl dashboard kiali
```

---

# 🟦 **HOW TRAFFIC FLOWS NOW**

Before Istio:

```
serviceA → serviceB → serviceC
```

After Istio:

```
serviceA → envoy-proxy-A → envoy-proxy-B → serviceB
         (mTLS)                    (mTLS)

serviceB → envoy-proxy-B → envoy-proxy-C → serviceC
```

The **proxies form the mesh circuit**.



Just tell me!

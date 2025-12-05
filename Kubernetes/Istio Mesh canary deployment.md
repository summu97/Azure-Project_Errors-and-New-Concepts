Here is the **exact, simplest, production-style way** to execute a **canary deployment** in Istio where:

* **serviceB v1 gets 90% traffic**
* **serviceB v2 gets 10% traffic**

This works for your namespace:

```
coda-application
```

Service name:

```
serviceB
```

---

# ⭐ Step 1 — Label your Deployments with Versions

Istio selects subsets using Kubernetes pod labels.

Update your **serviceB-v1 deployment**:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: serviceB-v1
  namespace: coda-application
spec:
  selector:
    matchLabels:
      app: serviceB
      version: v1
  template:
    metadata:
      labels:
        app: serviceB
        version: v1
    spec:
      containers:
      - name: serviceB
        image: your-image:v1
```

Update your **serviceB-v2 deployment**:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: serviceB-v2
  namespace: coda-application
spec:
  selector:
    matchLabels:
      app: serviceB
      version: v2
  template:
    metadata:
      labels:
        app: serviceB
        version: v2
    spec:
      containers:
      - name: serviceB
        image: your-image:v2
```

Apply deployments:

```bash
kubectl apply -f serviceB-v1.yaml
kubectl apply -f serviceB-v2.yaml
```

---

# ⭐ Step 2 — DestinationRule with subsets

Create:

**destination-rule-serviceB.yaml**

```yaml
apiVersion: networking.istio.io/v1alpha3
kind: DestinationRule
metadata:
  name: serviceB
  namespace: coda-application
spec:
  host: serviceB
  subsets:
  - name: v1
    labels:
      version: v1
  - name: v2
    labels:
      version: v2
  trafficPolicy:
    tls:
      mode: ISTIO_MUTUAL
```

Apply:

```bash
kubectl apply -f destination-rule-serviceB.yaml
```

---

# ⭐ Step 3 — Canary Traffic Split (90% v1 → 10% v2)

Create:

**virtual-service-serviceB-canary.yaml**

```yaml
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
        subset: v1
      weight: 90
    - destination:
        host: serviceB
        subset: v2
      weight: 10
```

Apply:

```bash
kubectl apply -f virtual-service-serviceB-canary.yaml
```

---

# ⭐ Step 4 — Validate the Canary Deployment

### 4.1 Check if both v1 and v2 pods are running:

```bash
kubectl get pods -n coda-application -l app=serviceB
```

Should see:

```
serviceB-v1-xxxx   2/2  
serviceB-v2-xxxx   2/2
```

(2/2 means Envoy sidecar injected.)

---

### 4.2 Send test traffic

If serviceA calls serviceB internally:

Run:

```bash
kubectl exec -n coda-application deploy/serviceA -- curl http://serviceB
```

Run this 10–20 times.

You should roughly see:

* **90% responses from v1**
* **10% responses from v2**

---

# ⭐ Step 5 — Gradually increase v2 traffic

Update the weights like below:

**50% v1 / 50% v2**

```yaml
- destination:
    host: serviceB
    subset: v1
  weight: 50
- destination:
    host: serviceB
    subset: v2
  weight: 50
```

Apply:

```bash
kubectl apply -f virtual-service-serviceB-canary.yaml
```

---

# ⭐ Step 6 — Full rollout to v2

Once v2 is validated:

```yaml
- destination:
    host: serviceB
    subset: v2
  weight: 100
```

Apply:

```bash
kubectl apply -f virtual-service-serviceB-canary.yaml
```

Then delete v1 deployment (optional):

```bash
kubectl delete deployment serviceB-v1 -n coda-application
```

---

# ⭐ YOU ARE DONE

Now you have:

✓ Versioned deployments
✓ Subsets
✓ Canary traffic split
✓ mTLS enforced
✓ Sidecar-controlled routing

I can generate everything cleanly for you.


````markdown
# AKS RBAC Issue and Resolution

## Issue

Running the following command:

```bash
kubectl get po
````

Returns the error:

```
Error from server (Forbidden): pods is forbidden: User "17e5c8e7-420c-4b29-ad82-bef36461b79c" cannot list resource "pods" in API group "" in the namespace "default"
```

---

## Step 1: Check which user you are authenticating as

```bash
kubectl auth whoami
```

Output:

```
ATTRIBUTE    VALUE
Username     17e5c8e7-420c-4b29-ad82-bef36461b79c
Groups       [b08ad625-5e4d-43ee-963a-18b3ac2115f7 system:authenticated]
Extra: oid   [17e5c8e7-420c-4b29-ad82-bef36461b79c]
```

---

## Meaning

* AKS Entra ID (AAD) authentication is enabled
* kubectl is using Azure CLI token
* Identity = Service Principal objectId
* RBAC is enforced

✅ Authentication ✔
❌ Authorization ❌

---

## Step 2: Login with admin consent

```bash
az aks get-credentials \
  --resource-group CODA_RG \
  --name coda-testaks \
  --admin \
  --overwrite-existing
```

This retrieves the cluster admin credentials for RBAC bypass.

---

## Step 3: Give admin access to the service principal (not recommended for production)

```bash
kubectl create clusterrolebinding jenkins-sp-admin \
  --clusterrole=cluster-admin \
  --user=17e5c8e7-420c-4b29-ad82-bef36461b79c
```

To **revert this change in the future**:

```bash
kubectl delete clusterrolebinding jenkins-sp-admin
```

---

## Step 4: Login normally and verify access

```bash
az aks get-credentials --resource-group CODA_RG --name coda-testaks --overwrite-existing
kubectl config use-context coda-testaks
kubectl get po
```

✅ At this point, you should be able to list pods and perform cluster operations according to the permissions granted.


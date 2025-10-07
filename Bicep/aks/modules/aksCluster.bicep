param location string
param clusterName string
param vnetResourceId string
param sshPublicKey string
param adminUsername string
param systemPool object
param userPools array
param serviceCidr string
param dnsServiceIP string
param kubernetesVersion string
param authorizedIpRanges array
param tagSuffix string

// -----------------------------
// AKS Cluster Resource
// -----------------------------
resource aks 'Microsoft.ContainerService/managedClusters@2025-01-01' = {
  name: clusterName
  location: location

  tags: {
    test: '${clusterName}-${tagSuffix}'
  }

  identity: {
    type: 'SystemAssigned'
  }

  properties: {
    kubernetesVersion: kubernetesVersion
    dnsPrefix: clusterName

    enableRBAC: true
    disableLocalAccounts: false

    linuxProfile: {
      adminUsername: adminUsername
      ssh: {
        publicKeys: [
          { keyData: sshPublicKey }
        ]
      }
    }

    // OIDC & workload identity
    oidcIssuerProfile: {
      enabled: true
    }

    securityProfile: {
      workloadIdentity: {
        enabled: true
      }
    }

    // API server access (authorized IPs)
    apiServerAccessProfile: {
      authorizedIPRanges: authorizedIpRanges
    }

    // Network
    networkProfile: {
      networkPlugin: 'azure'
      networkPolicy: 'azure'
      loadBalancerSku: 'Standard'
      serviceCidr: serviceCidr
      dnsServiceIP: dnsServiceIP
      outboundType: 'loadBalancer'
    }

    // System node pool defined inline
    agentPoolProfiles: [
      {
        name: systemPool.name
        vmSize: systemPool.vmSize
        count: systemPool.count
        minCount: systemPool.minCount
        maxCount: systemPool.maxCount
        enableAutoScaling: true
        mode: systemPool.mode
        osType: 'Linux'
        osSKU: 'Ubuntu'
        vnetSubnetID: '${vnetResourceId}/subnets/${systemPool.subnetName}'
        maxPods: systemPool.maxPods
      }
    ]
  }
}

// -----------------------------
// User Node Pools
// -----------------------------
module userNodePools './nodePool.bicep' = [for pool in userPools: {
  name: '${clusterName}-${pool.name}'
  params: {
    pool: pool
    vnetResourceId: vnetResourceId
    tagSuffix: tagSuffix
    clusterName: clusterName
  }
  dependsOn: [
    aks  // ensures AKS cluster and system pool are ready before user pools deploy
  ]
}]

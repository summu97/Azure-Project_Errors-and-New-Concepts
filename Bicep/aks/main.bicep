param adminUsername string
param systemPool object
param userPools array
param serviceCidr string
param dnsServiceIP string
param kubernetesVersion string
param authorizedIpRanges array

@description('Suffix appended to every resource tag value')
param tagSuffix string

module aks './modules/aksCluster.bicep' = {
  name: '${clusterName}'
  params: {
    location: location
    clusterName: clusterName
    vnetResourceId: vnetResourceId
    sshPublicKey: sshPublicKey
    adminUsername: adminUsername
    systemPool: systemPool
    userPools: userPools
    serviceCidr: serviceCidr
    dnsServiceIP: dnsServiceIP
    kubernetesVersion: kubernetesVersion
    authorizedIpRanges: authorizedIpRanges
    tagSuffix: tagSuffix
  

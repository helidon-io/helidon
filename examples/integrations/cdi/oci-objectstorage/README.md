# OCI Object Storage CDI Integration Example

## Build and run with Kubernetes (docker for desktop)

```bash
docker build -t helidon-examples-integrations-cdi-oci-objectstorage .

# make a copy of oci.secrets.template.sh and add the OCI information needed to
# connect to OCI services
cp oci.secrets.template oci.secrets.sh

# execute the script, this will create a Kubernetes secret that contains all the
OCI connectivity information
./oci.secrets.sh

# deploy the app
kubectl apply -f ../../../k8s/ingress.yaml -f app.yaml
```

Upload a file (object) to the to the storage using the [console](https://console.us-phoenix-1.oraclecloud.com)

Try the endpoint:

```
curl http://localhost/helidon-examples-integrations-cdi-oci-objectstorage/logo/{namespaceName}/{bucketName}/{objectName}
```
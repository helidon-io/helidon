# OCI Object Storage CDI Integration Example

## OCI setup

Setup your OCI SDK [configuration](https://docs.cloud.oracle.com/iaas/Content/API/Concepts/sdkconfig.htm)
 if you haven't done so already, and then run the following command:
```shell
./oci-setup.sh && source oci-env
```

This example requires an Object Storage, you can create one using the
 [OCI console](https://console.us-phoenix-1.oraclecloud.com). Once created,
 upload a file in order to exercise the example.

## Build and run

With Docker:
```shell
docker build -t helidon-examples-integrations-cdi-oci-objectstorage .
docker run --rm -d -p 8080:8080 \
    -e OCI_AUTH_PRIVATEKEY \
    -e OCI_AUTH_FINGERPRINT \
    -e OCI_AUTH_PASSPHRASE \
    -e OCI_AUTH_TENANCY \
    -e OCI_AUTH_USER \
    -e OCI_OBJECTSTORAGE_COMPARTMENT \
    -e OCI_OBJECTSTORAGE_REGION \
    --name helidon-examples-integrations-cdi-oci-objectstorage \
    helidon-examples-integrations-cdi-oci-objectstorage:latest
```

With Java:
```shell
mvn package
java -Doci.auth.fingerprint="${OCI_AUTH_FINGERPRINT}" \
    -Doci.auth.passphraseCharacters="${OCI_AUTH_PASSPHRASE}" \
    -Doci.auth.privateKey="${OCI_AUTH_PRIVATEKEY}" \
    -Doci.auth.tenancy="${OCI_AUTH_TENANCY}" \
    -Doci.auth.user="${OCI_AUTH_USER}" \
    -Doci.objectstorage.compartmentId="${OCI_OBJECTSTORAGE_COMPARTMENT}" \
    -Doci.objectstorage.region="${OCI_OBJECTSTORAGE_REGION}" \
    -jar target/helidon-examples-integrations-cdi-oci-objectstorage.jar
```

Try the endpoint:

```shell
curl http://localhost:8080/logo/{namespaceName}/{bucketName}/{objectName}
```

## Run With Kubernetes (docker for desktop)

```shell
docker build -t helidon-examples-integrations-cdi-oci-objectstorage .
./oci-setup.sh -k8s
kubectl apply \
  -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/ingress-nginx-3.15.2/deploy/static/provider/cloud/deploy.yaml \
  -f app.yaml
```

Try the endpoint:

```shell
curl http://localhost/oci-objectstorage/logo/{namespaceName}/{bucketName}/{objectName}
```

Stop the docker containers:
```shell
docker stop helidon-examples-integrations-cdi-oci-objectstorage
```

Delete the Kubernetes resources:
```shell
kubectl -f app.yaml
```

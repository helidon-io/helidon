# OCI List Regions Integration Example

## OCI setup

Setup your OCI SDK [configuration](https://docs.cloud.oracle.com/iaas/Content/API/Concepts/sdkconfig.htm)
 if you haven't done so already, and then run the following command:
```bash
./oci-setup.sh && source oci-env
```
Setup your OCI login information:

```yaml
oci:
  auth:
    user: "ocid1.user.oc1.."
    fingerprint: "7c:6d:89:fc:05:32:5b:"
    tenancy: "ocid1.tenancy.oc1.."
    keyFile: /path/to/file.pem
    region: "eu-frankfurt-1"
```

## Build and run

With Docker:
```bash
docker build -t helidon-examples-integrations-oci-objectstorage-se .
docker run --rm -d -p 8080:8080 \
    -e OCI_AUTH_PRIVATEKEY \
    -e OCI_AUTH_FINGERPRINT \
    -e OCI_AUTH_PASSPHRASE \
    -e OCI_AUTH_TENANCY \
    -e OCI_AUTH_USER \
    -e OCI_OBJECTSTORAGE_COMPARTMENT \
    -e OCI_OBJECTSTORAGE_REGION \
    --name helidon-examples-integrations-cdi-oci-listregions \
    helidon-examples-integrations-oci-objectstorage-se:latest
```

With Java:
```bash
mvn package
java -Doci.auth.fingerprint="${OCI_AUTH_FINGERPRINT}" \
    -Doci.auth.passphraseCharacters="${OCI_AUTH_PASSPHRASE}" \
    -Doci.auth.privateKey="${OCI_AUTH_PRIVATEKEY}" \
    -Doci.auth.tenancy="${OCI_AUTH_TENANCY}" \
    -Doci.auth.user="${OCI_AUTH_USER}" \
    -Doci.objectstorage.compartmentId="${OCI_OBJECTSTORAGE_COMPARTMENT}" \
    -Doci.objectstorage.region="${OCI_OBJECTSTORAGE_REGION}" \
    -jar target/helidon-examples-integrations-oci-objectstorage-se.jar
```

Try the endpoint:

```bash
curl http://localhost:8080/{namespaceName}/{bucketName}/{objectName}
```

## Run With Kubernetes (docker for desktop)

```bash
docker build -t helidon-examples-integrations-oci-objectstorage-se .
./oci-setup.sh -k8s
kubectl apply -f ../../../k8s/ingress.yaml -f app.yaml
```

Try the endpoint:

```bash
curl http://localhost:8080/{namespaceName}/{bucketName}/{objectName}
```

Stop the docker containers:
```bash
docker stop helidon-examples-integrations-oci-objectstorage-se
```
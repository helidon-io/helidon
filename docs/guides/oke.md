# Deploying to OKE

Push a Docker image of your Helidon application to Oracle Cloud Infrastructure Registry (OCIR), and deploy the image from the registry to Oracle Cloud Infrastructure Container Engine for Kubernetes (OKE).

## What You Need

|  |
|----|
| About 10 minutes |
| [Helidon prerequisites](../about/prerequisites.md) |
| An OKE cluster. See the [OKE documentation](http://www.oracle.com/webfolder/technetwork/tutorials/obe/oci/oke-full/index.html). |
| A Helidon project created from the quickstart Maven archetype. See [quickstart Maven archetype](../se/guides/quickstart.md). |

## Push Your Image to OCIR

Your account must be in the `Administrators` group or another group that has the `REPOSITORY_CREATE` permission.

Sign in to the Oracle Cloud Infrastructure (OCI) web console and generate an authentication token. See [Getting an Auth Token](https://docs.cloud.oracle.com/iaas/Content/Registry/Tasks/registrygettingauthtoken.htm).

> [!NOTE]
> Remember to copy the generated token. You won’t be able to access it again.

*Log in to the OCIR Docker registry:*

``` bash
docker login \
       -u <username> \ 
       -p <password> \ 
       <region-code>.ocir.io 
```

- The user name in the format `<tenancy_name>/<username>`.
- The password is the generated token.
- `<region-code>` is the code for the OCI region that you’re using. For example, the region code for Phoenix is `phx`. See [Regions and Availability Domains](https://docs.cloud.oracle.com/iaas/Content/General/Concepts/regions.htm).

*Tag the image that you want to push to the registry:*

``` bash
docker tag \
       helidon-quickstart-se:latest \ 
       <region-code>.ocir.io/<tenancy-name>/<repo-name>/<image-name>:<tag> 
```

- the local image to tag
- `<repo-name>` is optional. It is the name of a repository to which you want to push the image (for example, `project01`).

*Push the image to the Registry:*

``` bash
docker push \
    <region-code>.ocir.io/<tenancy-name>/<repo-name>/<image-name>:<tag>
```

You can pull your image with the image path used above, for example: `phx.ocir.io/helidon/example/helidon-quickstart-se:latest`

## Setup your K8s Cluster

Create a namespace (for example, `helidon`) for the project:

``` bash
kubectl create namespace helidon
```

The repository that you created is private. To allow Kubernetes to authenticate with the container registry and pull the private image, you must create and use an image-pull secret.

*Create an image-pull secret:*

``` bash
kubectl create secret docker-registry \
    ocirsecret \ 
    --docker-server=<region-code>.ocir.io \ 
    --docker-username='<tenancy-name>/<oci-username>' \ 
    --docker-password='<oci-auth-token>' \ 
    --docker-email='<email-address>' \
    --namespace helidon 
```

- The name of the config secret
- The docker registry (see docker tag step above)
- The user name (see docker login step above)
- The password (see docker login step above)
- The namespace created in the previous step

### Deploy the Image to Kubernetes

First, change to the `helidon-quickstart-se` directory.

Then edit `app.yaml` and add the following under `spec` in the `deployment` section:

``` yaml
spec:
  imagePullSecrets:
  - name: ocirsecret 
  containers:
  - name: helidon-quickstart-se
    image: phx.ocir.io/helidon/example/helidon-quickstart-se:latest 
    imagePullPolicy: Always
    ports:
    - containerPort: 8080
```

- The config secret name
- The image path

*Deploy the application:*

``` bash
kubectl create -f app.yaml -n helidon
```

*Get the `NodePort` number for your new pod:*

``` bash
kubectl get svc -n helidon
```

*Get the IP address for your cluster nodes:*

``` bash
kubectl get nodes
```

You can now access the application at `http://<NodeIpAddress>:<NodePort>/greet`.

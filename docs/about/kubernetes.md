# Run Kubernetes Locally for Development

For development, it can be convenient to run Kubernetes locally. A popular option that is used throughout Helidon documentation is [Docker Desktop](https://docs.docker.com/desktop/), which includes support for Kubernetes.

You can also use [minikube](https://minikube.sigs.k8s.io/docs/) for local development, though you will have to adapt any Docker Desktop-specific instructions for minikube.

For instructions on how to install and configure Docker Desktop for your platform, see the following Docker documentation:

- [Docker Desktop on Linux](https://docs.docker.com/desktop/setup/install/linux/)
- [Docker Desktop on macOS](https://docs.docker.com/desktop/setup/install/mac-install/)
- [Docker Desktop on Windows](https://docs.docker.com/desktop/setup/install/windows-install/).

After you install Docker Desktop, see [Explore the Kubernetes view](https://docs.docker.com/desktop/use-desktop/kubernetes/) in the Docker documentation for guidance on enabling and using Kubernetes in Docker Desktop.

> [!NOTE]
> Docker Desktop for Linux does not include `kubectl` by default. See [Install and Set Up kubectl on Linux](https://kubernetes.io/docs/tasks/tools/install-kubectl-linux/) in the Kubernetes documentation.

If you get errors when running `kubectl` commands, make sure the Kubernetes context is set to `docker-desktop`.

*Make sure Kubernetes context is set to docker-desktop*

``` bash
kubectl config get-contexts
kubectl config use-context docker-desktop
kubectl cluster-info
kubectl version
kubectl get nodes
```

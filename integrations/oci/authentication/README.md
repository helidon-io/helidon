# OCI Authentication Methods

OCI provides a few authentication methods that can be used when connecting to services.
Available methods depend on where your client runs.
Each method is represented through an `BasicAuthenticationDetailsProvider` implementation

Helidon supports the following Authentication Methods:

- API key based authentication
  - `config`: based on Configuration
  - `config-file`: based on OCI config file
- `instance-principal`: instance principal (Such as an OCI VM)
- `resource-prinicpal`: resource principal (Such as server-less functions)
- `oke-workload-identity`: identity of workload running on a k8s cluster

The first two types are always available through `helidon-integrations-oci` module.
Instance, resource, and workload support can be added through modules in this project module.

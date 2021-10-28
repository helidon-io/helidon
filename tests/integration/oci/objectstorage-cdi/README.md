OCI ObjectStorage Integration with MP APIs
---

This example expects an OCI ObjectStorage bucket with a file/object uploaded in it. 
You can provide configuration for those either by updating `application.yaml` or system properties or environment variables.

This example also expected that you will have OCI configuration file setup in default location `~/.oci/config`. 
Please see https://helidon.io/docs/latest/#/mp/oci/01_oci for details on this.

Once this is setup, you can run this test as
```shell
mvn clean verify
```
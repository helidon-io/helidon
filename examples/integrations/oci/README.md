# OCI SDK setup for Examples Build

OCI SDK uses JAX-RS Client 2.1.6 (javax package names), which makes it incompatible with Helidon 3 applications and any application that uses JAX-RS 3 (jakarta package naming).

Please see our [Guide](https://github.com/oracle/helidon/tree/master/docs/includes/oci.adoc) for detailed information on how to work around this issue.

Once you have this setup, you can build examples in this repository directory.

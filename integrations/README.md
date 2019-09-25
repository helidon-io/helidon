# Helidon Integrations

The Helidon Integrations project contains adapter code that integrates
various useful external services into Helidon MicroProfile.

## `serviceconfiguration`

The `serviceconfiguration` subproject provides mechanisms for
automatically configuring and in some cases provisioning services.

## `cdi`

The `cdi` subproject contains CDI
http://docs.jboss.org/cdi/spec/2.0/cdi-spec.html#spi[portable
extensions] that integrate various external services into CDI
containers.  Some subprojects make use of the `serviceconfiguration`
subproject described above.


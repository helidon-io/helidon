Security integration with IDCS
===================

This example demonstrates integration with IDCS (Oracle identity service, integrated with Open ID Connect provider).

Contents
--------
Currently only configured with application.yaml

To configure this example, you need to replace the following:
1. src/main/resources/application.yaml - set security.properties.idcs-* to your tenant and application configuration
2. src/main/java/io/helidon/security/examples/idcs/IdcsBuilderMain.java - see OidcConfig creation - update all IDCS values (and maybe proxy host)

Running the Example
-------------------

Run the "IdcsMain" class for file configuration based example, or "IdcsBuilderMain" for 
an example built programmatically.


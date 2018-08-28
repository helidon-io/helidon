Web Server Integration and HTTP Signatures
===================

This example demonstrates Integration of RX Web Server
based application with Security component and HTTP Signatures.

Contents
--------
There are two examples with exactly the same behavior
1. builder - shows how to programmatically secure application
2. config - shows how to secure application with configuration
    1. see src/main/resources/service1.conf and src/main/resources/service2.conf for configuration
3. Each consists of two services
    1. "public" service protected by basic authentication (for simplicity)
    2. "internal" service protected by a combination of basic authentication (for user propagation) and http signature
    (for service authentication)

Running the Example
-------------------

1. Clone the repository
2. If you cloned a snapshot, please run `mvn clean install -Dmaven.test.skip=true` in project root
3. Go to the directory of example you want to run
    - And run `mvn exec:exec` to run the default example (config based)
    - or  run `mvn exec:exec -Dexample.main-class="io.helidon.security.examples.signatures.SignatureExampleBuilderMain"` 
    to run builder based example
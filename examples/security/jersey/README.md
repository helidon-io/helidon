Security integration with Jersey
===================

This example demonstrates integration with Jersey (JAX-RS implementation).

Contents
--------
There are three examples with exactly the same behavior
1. builder - shows how to secure application using security built by hand
2. config - shows how to secure application with configuration
    1. see src/main/resources/application.yaml
3. programmatic - shows how to secure application using manual invocation of authentication

Running the Example
-------------------

1. Clone the repository
2. If you cloned a snapshot, please run `mvn clean install -Dmaven.test.skip=true` in project root
3. Go to the directory of example you want to run
    - And run `mvn exec:exec` to run the default example (config based)
    - or  run `mvn exec:exec -Dexample.main-class="io.helidon.security.examples.jersey.JerseyBuilderMain"` 
    to run builder based example
    - or run `mvn exec:exec -Dexample.main-class="io.helidon.security.examples.jersey.JerseyProgrammaticMain"` 
    to run example with a resource with manual security check

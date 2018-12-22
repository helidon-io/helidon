Web Server Integration and Digest Authentication
===================

This example demonstrates Integration of RX Web Server
based application with Security component and Digest authentication (from HttpAuthProvider).

Contents
--------
There are two examples with exactly the same behavior:
1. DigestExampleMain - shows how to programmatically secure application
2. DigestExampleConfigMain - shows how to secure application with configuration
    1. see src/main/resources/application.conf for configuration

Running the Example
-------------------

1. Clone the repository
2. If you cloned a snapshot, please run `mvn clean install -Dmaven.test.skip=true` in project root
3. Go to the directory of example you want to run
    - And run `mvn exec:exec` to run the default example (config based)
    - or  run `mvn exec:exec -Dexample.main-class="io.helidon.security.examples.webserver.digest.DigestExampleBuilderMain"` 
    to run builder based example

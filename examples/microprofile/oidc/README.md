# Security integration with OIDC

This example demonstrates integration with OIDC (Open ID Connect) providers.

## Contents

MP example that integrates with an OIDC provider.

To configure this example, you need to replace the following:
1. src/main/resources/application.yaml - set security.properties.oidc-* to your tenant and application configuration

## Running the Example

Run the "OidcMain" class for file configuration based example.

## Local configuration

The example is already set up to read
`${user.home}/helidon/conf/examples.yaml` to override defaults configured
in `application.yaml`.

## Build and run

```bash
mvn package
java -jar target/helidon-examples-microprofile-security-oidc-login.jar
```

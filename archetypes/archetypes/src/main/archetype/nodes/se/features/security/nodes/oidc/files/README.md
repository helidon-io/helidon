## Security integration with IDCS

This example demonstrates integration with IDCS (Oracle identity service, integrated with Open ID Connect provider).

### Code Configuration

Edit application.yaml for IdcsMain.java or OidcConfig variable definition for IdcsBuilderMain.java sample

1. idcs-uri  : Base URL of your idcs instance, usually something like https://idcs-<longnumber>.identity.oraclecloud.com
2. idcs-client-id  : This is obtained from your IDCS application in the IDCS console
3. idcs-client-secret   : This is obtained from your IDCS application in the IDCS console
4. frontend-uri : This is the base URL of your application when run, e.g. `http://localhost:7987`
5. proxy-host   : Your proxy server if needed
6. scope-audience : This is the scope audience which MUST match the primary audience in the IDCS resource, recommendation is not to have a trailing slash (/)

## Try the application

Build and run the application and then try the endpoints:

1. Open http://localhost:7987/rest/profile in your browser. This should present
   you with a response highlighting your logged in role (null) correctly as you are not logged in
2. Open `http://localhost:7987/oidc/logout` in your browser. This will log you out from your IDCS and Helidon sessions

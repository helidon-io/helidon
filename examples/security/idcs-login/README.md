# Security integration with IDCS

This example demonstrates integration with IDCS (Oracle identity service, integrated with Open ID Connect provider).

## Contents

This project contains two samples, one (IdcsMain.java) which is configured via the application.yaml file and a second example (IdcsBuilderMain.java) which is configured in code.

When configured the example exposes two HTTP endpoints  `/jersey`, a rest endpoint protected by an IDCS application (with two scopes) and a second endpoint (/rest/profile) which is not protected.

### IDCS Configuration

Edit application.yaml for IdcsMain.java or OidcConfig variable definition for IdcsBuilderMain.java sample

1. Log in to the IDCS console and create a new application of type "confidential app"
2. Within  **Resources**
    1. Create two resources called `first_scope` and `second_scope`
    2. Primary Audience = `http://localhost:7987/"`   (ensure there is a trailing /)
3. Within **Client Configuration**
   1.  Register a client
   2.  Allowed Grant Types = Client Credentials,JWT Assertion, Refresh Token, Authorization Code
   3.  Check "Allow non-HTTPS URLs"
   4.  Set ReDirect URL to `http://localhost:7987/oidc/redirect`
   5.  Client Type = Confidential
   6.  Add all Scopes defined in the resources section

Ensure you save and *activate* the application

### Code Configuration 

Edit application.yaml for IdcsMain.java or OidcConfig variable definition for IdcsBuilderMain.java sample

 1. idcs-uri  : Base URL of your idcs instance, usually something like https://idcs-<longnumber>.identity.oraclecloud.com
 2. idcs-client-id  : This is obtained from your IDCS application in the IDCS console 
 3. idcs-client-secret   : This is obtained from your IDCS application in the IDCS console
 4. frontend-uri : This is the base URL of your application when run, e.g. `http://localhost:7987`
 5. proxy-host   : Your proxy server if needed
 6. scope-audience : This is the scope audience which MUST match the primary audience in the IDCS resource, recommendation is not to have a trailing slash (/)

## Build and run

```bash
mvn package
java -jar target/helidon-examples-security-oidc.jar
```

Try the endpoints:

3. Open http://localhost:7987/rest/profile in your browser. This should present
 you with a response highlighting your logged in role (null) correctly as you are not logged in
4. Navigate to `http://localhost:7987/jersey` this should
   1. Redirect you to the OIDCS login console to authenticate yourself, once done
   2. Redirects you back to the application and should display your users credentials/IDCS information
5. Executing `http://localhost:7987/jersey` displays the user details from IDCS

## Calling Sample from Postman

Now that everything is setup it is possible to call the sample from tools like postman

1. Create new  REST call for your service , e.g. `http://localhost:7987/jersey`
2. Set authentication to oauth 2.0
3. Press the "Get New Access Token" button
   1. Grant Type should be "Password Credentials"
   2. IDCS Access token URI should be `https://<IDCS tenancy>.identity.oraclecloud.com/oauth2/v1/token`
   3. ClientID and Client Secret should be obtained from IDCS and is the same that is configured in the app
   4. Scopes should be a "space" delimited list of scopes prefixed by audience , e.g. `http://localhost:7987/first_scope http://localhost:7987/second_scope`
   5. Client Authentication "Send as Basic Auth Header"
4. Request Token  (If you get an error check the postman developer console)
5. Once you have a token ensure you press the "Use token" button
6. Execute your rest call

## Troubleshooting

#### Upon redirect to IDCS login page you receive a message indicating the scope isn't found

- Check that *both* scope names in JerseyResource (first_scope and second_scope) exist in your IDCS application
- Check that the `primary audience` config value in IDCS contains a trailing / and the `front-end-url` in the config file does not

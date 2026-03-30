# Helidon SE OIDC Security Provider Guide

This guide describes how to set up Keycloak and Helidon to secure your application with OIDC security provider.

## What You Need

For this 20 minute tutorial, you will need the following:

|  |  |
|----|----|
| [Java SE 21](https://www.oracle.com/technetwork/java/javase/downloads) ([Open JDK 21](http://jdk.java.net)) | Helidon requires Java 21+ (25+ recommended). |
| [Maven 3.8+](https://maven.apache.org/download.cgi) | Helidon requires Maven 3.8+. |
| [Docker 18.09+](https://docs.docker.com/install/) | If you want to build and run Docker containers. |
| [Kubectl 1.16.5+](https://kubernetes.io/docs/tasks/tools/install-kubectl/) | If you want to deploy to Kubernetes, you need `kubectl` and a Kubernetes cluster (you can [install one on your desktop](../../about/kubernetes.md)). |

Prerequisite product versions for Helidon 4.4.0-SNAPSHOT

*Verify Prerequisites*

``` bash
java -version
mvn --version
docker --version
kubectl version
```

*Setting JAVA_HOME*

``` bash
# On Mac
export JAVA_HOME=`/usr/libexec/java_home -v 21`

# On Linux
# Use the appropriate path to your JDK
export JAVA_HOME=/usr/lib/jvm/jdk-21
```

## Introduction

This guide describes the steps required to protect your whole application or a specific area with Open ID Connect (OIDC) security. OIDC is a secure mechanism for an application to contact an identity service. It’s built on top of OAuth 2.0 and provides full-fledged authentication and authorization protocols.

## Keycloak Installation

### On Docker

To install Keycloak with Docker, open a terminal and make sure the port 8080 is free.

*Enter the following command*

``` bash
docker run -p 8080:8080 -e KEYCLOAK_ADMIN=admin -e KEYCLOAK_ADMIN_PASSWORD=admin quay.io/keycloak/keycloak:24.0.5 start-dev
```

This will start Keycloak on local port 8080. It will create the admin user with username `admin` and password `admin` Feel free to modify 24.0.5 by another keycloak version. If you are running docker behind a proxy server, make sure it is either configured into docker or disabled. Otherwise, you might face a connection timeout because docker cannot download the required data.

To verify that Keycloak is running correctly, go to the admin console : <http://localhost:8080/admin> Log in using the username and password mentioned above: `admin`.

You should be logged in successfully, and it prompts the admin console.

### On JDK

Download the last version of Keycloak from Keycloak website : <https://www.keycloak.org/downloads> In the table Server choose Standalone server distribution. ZIP or Tar format are available, click on either to download Keycloak.

After extracting the archive file, you should have a directory named keycloak followed by the version. For example, if you chose version 24.0.5, the folder must be named keycloak-24.0.5.

Open keycloak folder to make it your current directory.

*Run this command from command prompt to open the directory:*

``` bash
cd keycloak-24.0.5
```

#### Start Keycloak

To start keycloak and have it ready for further steps, run the following command.

*On Linux run:*

``` bash
bin/kc.sh start-dev
```

*On Windows run:*

``` bash
bin\kc.bat start-dev
```

Keycloak runs on localhost:8080 by default.

### Create an Admin User

You need to create an admin user because it does not come by default when installing Keycloak. To do this, open <http://localhost:8080> in your favorite browser.

A window `Create an administrative user` should be prompted. If not, check if any error appear in the terminal.

Fill the form by adding Username and Password. Click on `Create user` to create the admin user. A confirmation message is displayed and an administrative user is created. Press `Open Administration Console` and use the same credentials to log in.

After successfully logged in, the admin console is prompted.

## Setup Keycloak

To set up Keycloak properly, go to the admin console: <http://localhost:8080/admin>

If you are using Docker, use Username `admin` and password `admin` as it is the default admin user. Otherwise, use the username and password you used to create the admin user.

### Create a Realm

A realm is the place where groups of applications, and their environment, can be created. It gathers :

- One or several applications
- One or several users
- Sessions
- Events
- Clients and their scopes

By default, there is a realm called `Master`. It is used to manage Keycloak. It is not recommended to associate your application with this realm as it could disturb Keycloak functioning.

To create a new realm to manage your application:

1.  Open Keycloak admin console <http://localhost:8080/admin>.
2.  Hover the mouse over the dropdown in the top-left corner where it says `Keycloack`, and press `Create realm`.
3.  Fill the form by adding the realm name, `myRealm` for example.
4.  Click on `Create` to create the new realm.

To verify that your realm is created, you should see your realm name (or `myRealm` if you followed the example) in the top-left corner where it said `Keycloack` previously.

To switch from a realm to another, hover the realm name, and the other realm created appear in the dropdown. Click on any realm name to change the current realm. Make sure all configuration or modification are saved before changing the current realm or be subject to lose your configuration.

### Create a User

Initially there are no users in a new realm. An unlimited number of user can be created per realm. A realm contains resources such as client which can be accessed by users.

To create a new user:

1.  Open the Keycloak admin console: <http://localhost:8080/admin>
2.  Click on `Users` in the left menu
3.  Press `Create new user`
4.  Fill the form (Username is the only mandatory field) with this value Username: `myUser`
5.  Click `Create`

A new user is just created, but it needs a password to be able to log in. To initialize it, do this:

1.  Click on `Credentials` at the top of the page, next to `Details`.
2.  Press on `Set Password`.
3.  Fill `Password` and `Password confirmation` with the user password of your choice.
4.  If the `Temporary` field is set to `ON`, the user has to update password on next login. Click `ON` to make it `OFF` and prevent it.
5.  Press `Save`.
6.  A pop-up window is popping off. Click on `Set Password` to confirm the new password.

To verify that the new user is created correctly:

1.  Open the Keycloak account console: `http://localhost:8080/realms/myRealm/account`.
2.  Login with `myUser` and password chosen earlier.
3.  Fill the form with required data.

You should now be logged-in to the account console where users can manage their accounts.

### Create a Client

To create your first client:

1.  Open the Keycloak admin console: <http://localhost:8080/admin>.
2.  Make sure the current realm is `myRealm` and not `Master`.
3.  Navigate to the left menu, into configure section, click on `Clients`. This window displays a table with every client from the realm.
4.  Click on `Create client`.
5.  Fill the following:
    1.  `Client ID` : `myClientID`
    2.  `Client Protocol` : `OpenID Connect`
6.  Press `Next`
7.  `Capability config` step
    1.  Enable `Client authentication`
    2.  Enable `Authorization`
8.  Press `Next`
    1.  Update `Valid Redirect URIs` : <http://localhost:7987/*>
9.  Click on `Save`.

A new tab named `Credentials` is created. Click on it to access this new tab.

- Select `Client Authenticator` : `Client ID and Secret`
- The client secret is displayed.

Keycloak is now configured and ready. Keep keycloak running on your terminal and open a new tab to set up Helidon.

## Setup Helidon

Use the Helidon SE Maven archetype to create a simple project. It will be used as an example to show how to set up Helidon. Replace `4.4.0-SNAPSHOT` by the latest helidon version. It will download the quickstart project into the current directory.

*Run the Maven archetype*

``` bash
mvn -U archetype:generate -DinteractiveMode=false \
    -DarchetypeGroupId=io.helidon.archetypes \
    -DarchetypeArtifactId=helidon-quickstart-se \
    -DarchetypeVersion=4.4.0-SNAPSHOT \
    -DgroupId=io.helidon.examples \
    -DartifactId=helidon-quickstart-se \
    -Dpackage=io.helidon.examples.quickstart.se
```

*The project will be built and run from the helidon-quickstart-se directory:*

``` bash
cd helidon-quickstart-se
```

### Update Project Dependencies

Update the pom.xml file and add the following Helidon dependency to the `<dependencies>` section.

*Add the following dependencies to `pom.xml`:*

``` xml
<dependency>
    <groupId>io.helidon.webserver</groupId>
    <artifactId>helidon-webserver-security</artifactId>
</dependency>
<dependency>
    <groupId>io.helidon.security.providers</groupId>
    <artifactId>helidon-security-providers-oidc</artifactId>
</dependency>
```

*Remove the `test` scope from `helidon-webclient` dependency*

``` xml
<dependency>
    <groupId>io.helidon.webclient</groupId>
    <artifactId>helidon-webclient</artifactId>
    <scope>test</scope> <!-- remove this line -->
</dependency>
```

### Add OIDC Security Properties

The OIDC security provider configuration can be joined to helidon configuration file. This file is located here: `src/main/resources/application.yaml`. It can be easily used to configure the web server without modifying application code.

*Add the following line to application.yaml*

``` yaml
server:
  port: 7987
  host: localhost
  features:
    security:
        # protected paths on the web server
        paths: 
          - path: "/greet"
            methods: ["get"]
            authenticate: true
security:
  providers:
  - abac:
      # Adds ABAC Provider - it does not require any configuration
  - oidc:
      client-id: "myClientID" 
      client-secret: "changeit" 
      identity-uri: "http://localhost:8080/realms/myRealm" 
      audience: "account"
      header-use: "true"
      # proxy-host should be defined if you operate behind a proxy, can be removed otherwise
      proxy-host: ""
      frontend-uri: "http://localhost:7987" 
      server-type: "oidc"
```

- `paths` section defines the protected application’s path.
- `client-id` must be the same as the one configure in keycloak.
- The client secret generate by Keycloak during `Create a client` section.
- `identity-uri` is used to redirect the user to keycloak.
- `frontend-uri` will direct you back to the application.

Make sure keycloak and the application are not running on the same port. The application port value can be changed into application.yaml.

If the port 7987 is already used, check what port is free on your machine.

*Replace the old port into application.yaml*

``` yaml
server:
  port: "{Your-new-port}"

frontend-uri: "http://localhost:{Your-new-port}"
```

### Configure Web Server

Once the properties are added, the web server must be setup. The `Main#routing` method gather all configuration properties.

*Add the following to the `Main#routing` method*

``` java
Config config = Config.global();
routing.addFeature(OidcFeature.create(config));   
```

- Create and register `OidcFeature`.

That code is extracting security properties from application.yaml into two steps. First the Security instance is used to bootstrap security, so the SecurityFeature instance can integrate security into Web Server. Then, OidcFeature instance registers the endpoint to which OIDC redirects browser after a successful login.

Helidon sample is now setup and ready.

## Build the Application

*Build the application, skipping unit tests, then run it:*

``` bash
mvn package -DskipTests
java -jar target/helidon-quickstart-se.jar
```

The tests must be skipped, otherwise it produces test failure. As the `/greet` endpoint for GET request is now protected, its access is limited, and the tests are not built to take oidc security in account.

1.  Open your favourite browser and try to access `http://localhost:7987/greet/Michael`.
2.  You should not be redirected and receive greeting from the application.
3.  Enter the following into URL : `http://localhost:7987/greet`.
4.  Keycloak redirect you to its login page.
5.  Enter the username and associated password:
    1.  `Username` : `myUser`
    2.  `Password`: `password`
6.  After successful log in, keycloak redirect you to the `http://localhost:7987/greet` endpoint and print Hello word.
7.  Press `Ctrl+C` to stop the application.

From the actual settings, the user needs to log in only once, then Keycloak saves all the connection data.

### Test Keycloak Process with Postman

Keycloak supports many authentication and authorization flows, but only two of them will be shown. This section describes another way you can get an access token or refresh a token or identity token. The identity token contains information about the user. The access token contains access information that the application can use to determine what resources the user is allowed to access. Once expired, the refresh token allows the application to obtain a new access token. As these tokens contain sensitive information, they are valid for a very short period. It is possible to make them last longer in order to let you manipulate them with Postman. To do so:

1.  Open the Keycloak Console.
2.  Click on the `Realm Settings` in the left menu.
3.  Navigate to the `Tokens` tab.

You can increase the access token lifespan.

#### Authorization Code Flow

The Authorization Code flow is suitable for browser-based applications. It is composed of three main steps:

1.  The browser visits the application. The user is not logged in, so it redirects the browser to Keycloak which requires username and password for authentication.
2.  Keycloak authenticates the user and returns a temporary authorization code as a query parameter in the URL.
3.  The authorization code is used to get access and refresh token from Keycloak token endpoint.

For the first step, paste the following URL into your browser: `http://localhost:8080/realms/myRealm/protocol/openid-connect/auth?client_id=myClientID&response_type=code`. Two query parameters are provided, the client id and the response type. Press enter and Keycloak responds with different URL containing a query parameter `code`. You successfully received the authorization code.

In order to achieve the third step, we can use Postman to exchange the authorization code for tokens. In Postman, select the Http POST method. Keycloak endpoint to get token is the following: `http://localhost:8080/realms/myRealm/protocol/openid-connect/token`. In the body of the request, select `x-www-form-urlencoded` type. Add the following data:

*Enter the key:value*

``` json
[
  {"key":"grant_type","value":"authorization_code"},
  {"key":"client_id","value":"myClientID"},
  {"key":"client_secret","value":"client secret"},
  {"key":"code","value":"authorization code"}
]
```

Do not forget to replace the `client secret` by its value (generated during Create a Client), and `authorization code` by the code value in the query parameter. Send the request by pressing `Send`. Keycloak returns an access token and a refresh token.

#### Resource Owner Password Credentials Grant (Direct Access Grants)

The Direct Access Grants flow is used by REST clients that want to request tokens on behalf of a user. To use Postman to make this request on behalf of `myuser`, select the GET method and enter this URL: `http://localhost:7987/greet/`. Under `Authorization` tab, select authorization type\`OAuth 2.0\`. Under it, complete the sentence `Add authorization data to` with `Request Headers`, and complete the required fields.

Note: Make sure your Helidon application is running. If it is not, please start it.

*Enter the following information:*

``` json
[
  {"key":"Header Prefix","value":"bearer"},
  {"key":"Grant type","value":"Password  Credentials"},
  {"key":"Access Token URL","value":"http://localhost:8080/realms/myRealm/protocol/openid-connect/token"},
  {"key":"Client ID","value":"myClientID"},
  {"key":"Client Secret","value":"client secret"},
  {"key":"Username","value":"myuser"},
  {"key":"Password","value":"password"},
  {"key":"Scope","value":"openid"},
  {"key":"Client Authentication","value":"Send as Basic Auth Header"}
]
```

Again, make sure to replace `client secret` by the actual client secret. Click on `Get New Access Token`. A popup window appears with Authentication complete, click on proceed to display access, refresh and identity token. Copy and paste the access token to `Access Token` field and press `Send`. Helidon greeting application sends back `Hello World !`.

#### Update Tests to the Secure Environment

At this stage of the application, tests cannot pass because of OIDC security. The only way to authenticate a user is through the front end of that server which can be accessed with the browser for example.

In order to keep security and test the application locally, a new security provider must be setup. By adding specific configuration to the tests, it is possible to override the application configuration.

The following explains how to set a basic authentication instead of oidc security provider only for the tests. Which means, at the end of this guide, the application will be secured by oidc security provider, and the tests will use basic authentication.

*Add the following dependency to `pom.xml`:*

``` xml
<dependency>
    <groupId>io.helidon.security.providers</groupId>
    <artifactId>helidon-security-providers-http-auth</artifactId>
    <scope>test</scope>
</dependency>
```

In the test folder open the application.yaml file: `helidon-quickstart-se/src/test/resources/application.yaml`

*Copy these properties into application.yaml*

``` yaml
app:
  greeting: "Hello"

server:
  port: 7987
  host: localhost

security:
  providers:
    - abac:
      # Adds ABAC Provider - it does not require any configuration
    - http-basic-auth:
        users:
          - login: "jack"
            password: "jackIsGreat"
    - oidc:
        client-id: "myClientID" 
        client-secret: "Your client secret" 
        identity-uri: "http://localhost:8080/realms/myRealm"
        audience: "account"
        frontend-uri: "http://localhost:7987"
        server-type: "oidc"
  web-server:
    # protected paths on the web server - do not include paths served by Jersey, as those are protected directly
    paths:
      - path: "/greet"
        methods: ["get"]
        authenticate: true
```

- Replace this field by your Keycloak client ID.
- Replace this field by your Keycloak client Password.

Add the `http-basic-auth` properties in the security → providers property section. This configuration will be used by the tests instead of the `java/resources/application.yaml`.

In the `AbstractMainTest.java` file, tests need to be modified to check the application security when accessing `/greet` path with a `GET` method.

*Replace the first webclient call by this one into testRootRoute method:*

``` java
try (HttpClientResponse response = client.get()
        .path("/greet")
        .request()) {
    assertThat(response.status(), is(Status.UNAUTHORIZED_401));
}
```

This piece of code uses the webclient to access the application on `/greet` path with a `GET` method. The http basic authentication security protects this path, so the client should receive an HTTP 401 code for unauthorized.

Only `jack` user has access to this part of the application.

*Add new check to the testRootRoute method:*

``` java
String auth = "Basic " + Base64.getEncoder().encodeToString("jack:changeit".getBytes());
JsonObject jsonObject = client.get()
        .path("/greet")
        .header(HeaderNames.AUTHORIZATION, auth)
        .requestEntity(JsonObject.class);

assertThat(jsonObject.getString("message"), is("Hello World!"));
```

The username and password are encoded and placed inside the header in order to authenticate as jack to access the application. If the authentication is successful, the application send the `Hello World` back as a `JsonObject`.

Now, the project can be built without skipping test.

*Build the project*

``` bash
mvn clean install
```

#### Restrict Access to a Specific Role

To give less access to an endpoint, it is possible to configure user role. So the application will only grant access to the user with the required role.

Add a user and roles to the `helidon-quickstart-se/src/test/resources/application.yaml`.

*Add jack role and create a new user named john:*

``` yaml
- http-basic-auth:
    users:
      - login: "jack"
        password: "changeit"
        roles: [ "admin", "user" ]
      - login: "john"
        password: "changeit"
        roles: [ "user" ]
```

Into the `web-server` section, the `roles-allowed` parameter defines which roles have access to the protected path and method.

*Add `admin` role*

``` yaml
web-server:
    # protected paths on the web server
    # do not include paths served by Jersey
    # as those are protected directly
    paths:
      - path: "/greet"
        methods: ["get"]
        roles-allowed: "admin"
        authenticate: true
```

Now, only Jack has access to secure endpoint as he has an admin role. John, as a simple user, can not access it. Once it is done, go to the tests to check the application behavior. The test from previous section is still passing as jack has access.

The user `john` has only the `user` role so when accessing protected endpoint, a 403 (Forbidden) http code is returned.

*Check that john does not have access*

``` java
String auth = "Basic " + Base64.getEncoder().encodeToString("john:changeit".getBytes());
try (HttpClientResponse response = client.get()
        .path("/greet")
        .header(HeaderNames.AUTHORIZATION, auth)
        .request()) {
    assertThat(response.status(), is(Status.FORBIDDEN_403));
}
```

*Build the project*

``` bash
mvn clean install
```

The tests pass, and your application is secured with specific roles in addition to user IDs.

# Helidon SE DB Client Guide

This guide describes the features of Helidon’s DB Client and how to create a sample Helidon SE project that can be used to run some basic examples using the Helidon DB Client.

## What You Need

For this 15 minute tutorial, you will need the following:

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

The Helidon DB Client provides a unified API for working with databases.

### Main Features

The main features of Helidon DB Client are:

- **Unified API for data access and query**: The API was implemented as a layer above JDBC or MongoDB Java Driver, so any relational databases with JDBC driver or MongoDB are supported.
- **Observability**: Support for health checks, metrics and tracing.
- **Portability between relational database drivers**: Works with native database statements that can be used inline in the code or defined as named statements in database configuration. By moving the native query code to configuration files, the Helidon DB Client allows you to switch to another database by changing the configuration files, not the code.

## Getting Started with Helidon DB Client

This section describes how to configure and use the key features of the Helidon DB Client.

### Set Up the H2 Database

#### From Docker

Create a new file in `helidon-quickstart-se` named `Dockerfile.h2`. It will be used to create the H2 docker image to run H2 in a container.

*Write the following content into the new file created*

``` dockerfile
FROM openjdk:11-jre-slim

ENV H2_VERSION "1.4.199"

ADD "https://repo1.maven.org/maven2/com/h2database/h2/${H2_VERSION}/h2-${H2_VERSION}.jar" /opt/h2.jar

COPY h2.server.properties /root/.h2.server.properties

EXPOSE 8082
EXPOSE 9092

CMD java \
       -cp /opt/h2.jar \
       org.h2.tools.Server \
       -web -webDaemon -webAllowOthers -webPort 8082 \
       -tcp -tcpAllowOthers -tcpPort 9092 \
       -ifNotExists
```

Create a new file `h2.server.properties` in the current directory.

*Copy the properties into the properties file.*

``` properties
webSSL=false
webAllowOthers=true
webPort=8082
0=Generic H2 (Server)|org.h2.Driver|jdbc\:h2\:tcp\://localhost\:9092/~/test|sa
```

*Build the H2 docker image*

``` bash
docker build -f Dockerfile.h2 . -t h2db
```

*Run the H2 docker image*

``` bash
docker run --rm -p 8082:8082 -p 9092:9092 --name=h2 -it h2db
```

#### From the Command Line

A database stores the books from the library. H2 is a java SQL database that is easy to use and lightweight. If H2 is not installed on your machine, here are few steps to quickly download and set it up:

1.  Download the latest H2 version from the official website: <https://www.h2database.com/html/main.html>
    - Note: Windows operating system users can download the Windows Installer.
2.  Unzip the downloaded file into your directory.
    - Only the h2-{latest-version}.jar, located in the h2/bin folder, will be needed.
3.  Open a terminal window and run the following command to start H2:.

*Replace `{latest-version}` with your current H2 version:*

``` bash
java -cp h2-{latest-version}.jar org.h2.tools.Shell -url dbc:h2:~/test -user sa -password "" -sql "" 
java -jar h2-{latest-version}.jar -webAllowOthers -tcpAllowOthers -web -tcp 
```

- Pre-create the database (optional if the file `~/test` already exists)
- Start the database

### Connect to the Database

Open the console at <http://127.0.0.1:8082> in your favorite browser. It displays a login window. Select `Generic H2` from `Saved Settings`. The following settings should be set by default:

- Driver Class: org.h2.Driver
- JDBC URL: jdbc:h2:tcp://localhost:9092/~/test
- User Name: sa
- Password:

Password must stay empty. Click **Connect**, the browser displays a web page. The database is correctly set and running.

### Create a Sample SE Project Using Maven Archetype

Generate the project sources using the Helidon SE Maven archetype. The result is a simple project that can be used for the examples in this guide.

*Run the Maven archetype:*

``` bash
mvn -U archetype:generate -DinteractiveMode=false \
    -DarchetypeGroupId=io.helidon.archetypes \
    -DarchetypeArtifactId=helidon-quickstart-se \
    -DarchetypeVersion=4.4.0-SNAPSHOT \
    -DgroupId=io.helidon.examples \
    -DartifactId=helidon-quickstart-se \
    -Dpackage=io.helidon.examples.quickstart.se
```

A new directory named `helidon-quickstart-se` is created.

*Enter into this directory:*

``` bash
cd helidon-quickstart-se
```

### Add Dependencies

Navigate to the `helidon-quickstart-se` directory and open the `pom.xml` file to add the following Helidon dependencies required to use the DB Client:

*Copy these dependencies to pom.xml:*

``` xml
<dependencies>
    <!-- ... -->
    <dependency>
        <groupId>io.helidon.dbclient</groupId>
        <artifactId>helidon-dbclient</artifactId> 
    </dependency>
    <dependency>
        <groupId>io.helidon.dbclient</groupId>
        <artifactId>helidon-dbclient-jdbc</artifactId> 
    </dependency>
    <dependency>
        <groupId>io.helidon.dbclient</groupId>
        <artifactId>helidon-dbclient-hikari</artifactId> 
    </dependency>
    <dependency>
        <groupId>io.helidon.integrations.db</groupId>
        <artifactId>h2</artifactId> 
    </dependency>
    <dependency>
        <groupId>org.slf4j</groupId>
        <artifactId>slf4j-jdk14</artifactId>
    </dependency>
    <dependency>
        <groupId>io.helidon.dbclient</groupId>
        <artifactId>helidon-dbclient-health</artifactId> 
    </dependency>
    <dependency>
        <groupId>io.helidon.dbclient</groupId>
        <artifactId>helidon-dbclient-metrics</artifactId> 
    </dependency>
    <dependency>
        <groupId>io.helidon.dbclient</groupId>
        <artifactId>helidon-dbclient-metrics-hikari</artifactId>
    </dependency>
    <dependency>
        <groupId>io.helidon.dbclient</groupId>
        <artifactId>helidon-dbclient-jsonp</artifactId> 
    </dependency>
    <!-- ... -->
</dependencies>
```

- DB Client API dependency.
- Using JDBC driver for this example.
- Using HikariCP as a connection pool.
- H2 driver dependency.
- Support for health check.
- Support for metrics.
- Support for Jsonp.

### Configure the DB Client

To configure the application, Helidon uses the `application.yaml`. The DB Client configuration can be joined in the same file and is located here: `src/main/resources`.

*Copy these properties into application.yaml*

``` yaml
db:
  source: jdbc 
  connection: 
    url: "jdbc:h2:tcp://localhost:9092/~/test"
    username: "sa"
    password:
  statements: 
    health-check: "SELECT 0"
    create-table: "CREATE TABLE IF NOT EXISTS LIBRARY (NAME VARCHAR NOT NULL, INFO VARCHAR NOT NULL)"
    insert-book: "INSERT INTO LIBRARY (NAME, INFO) VALUES (:name, :info)"
    select-book: "SELECT INFO FROM LIBRARY WHERE NAME = ?"
    delete-book: "DELETE FROM LIBRARY WHERE NAME = ?"
  health-check:
    type: "query"
    statementName: "health-check"
  services:
    metrics:
      - type: COUNTER 
        statement-names: [ "select-book" ]
```

- Source property support two values: jdbc and mongo.
- Connection detail we used to set up H2.
- SQL statements to manage the database.
- Add a counter for metrics only for the `select-book` statement.

*Copy these properties into application-test.yaml*

``` yaml
db:
  connection:
    url: "jdbc:h2:mem:test" 
```

- Override the JDBC URL to use an in-memory database for the tests

### Set Up Helidon DB Client

*Update `Main#main`:*

``` java
public static void main(String[] args) {

    // load logging configuration
    LogConfig.configureRuntime();

    Config config = Config.global();

    DbClient dbClient = DbClient.create(config.get("db")); 
    Contexts.globalContext().register(dbClient); 

    HealthObserver healthObserver = HealthObserver.builder()
            .useSystemServices(false)
            .details(true)
            .addCheck(DbClientHealthCheck.create(dbClient, config.get("db.health-check"))) 
            .build();

    ObserveFeature observe = ObserveFeature.builder()
            .config(config.get("server.features.observe"))
            .addObserver(healthObserver) 
            .build();

    WebServer server = WebServer.builder()
            .config(config.get("server"))
            .addFeature(observe) 
            .routing(Main::routing)
            .build()
            .start();

    System.out.println("WEB server is up! http://localhost:" + server.port() + "/simple-greet");
}
```

- Create the DbClient instance
- Register it in the global context
- Create an instance of HealthObserver to register a DbClientHealthCheck
- Add the `HealthObserver` to the `ObserveFeature`
- Register the ObserveFeature on the server

### Create the Library service

Create LibraryService class into `io.helidon.examples.quickstart.se` package.

*LibraryService class looks like this:*

``` java
public class LibraryService implements HttpService {

    private final DbClient dbClient;    

    LibraryService() {
        dbClient = Contexts.globalContext()
                .get(DbClient.class)
                .orElseGet(this::newDbClient); 
        dbClient.execute()
                .namedDml("create-table"); 
    }

    private DbClient newDbClient() {
        return DbClient.create(Config.global().get("db"));
    }

    @Override
    public void routing(HttpRules rules) {
        // TODO
    }
}
```

- Declare the DB Client instance
- Initialize the DB Client instance using global config
- Initialize the database schema

As the LibraryService implements `io.helidon.webserver.HttpService`, the `routing(HttpRules)` method has to be implemented. It defines application endpoints and Http request which can be reached by clients.

*Add update method to LibraryService*

``` java
@Override
public void routing(HttpRules rules) {
    rules
            .get("/{name}", this::getBook)      
            .put("/{name}", this::addBook)      
            .delete("/{name}", this::deleteBook)   
            .get("/json/{name}", this::getJsonBook); 
}
```

- Return information about the required book from the database.
- Add a book to the library.
- Remove a book from the library.
- Return the book information in Json format.

To summarize, there is one endpoint that can manipulate books. The number of endpoints and application features can be changed from these rules by creating or modifying methods. `{name}` is a path parameter for the book name. The architecture of the application is defined, so the next step is to create these features.

*Add getBook to the LibraryService:*

``` java
private void getBook(ServerRequest request,
                     ServerResponse response) {

    String bookName = request.path()
            .pathParameters()
            .get("name"); 

    String bookInfo = dbClient.execute()
            .namedGet("select-book", bookName)   
            .map(row -> row.column("INFO").asString().get())
            .orElseThrow(() -> new NotFoundException(
                    "Book not found: " + bookName)); 
    response.send(bookInfo); 
}
```

- Get the book name from the path in the URL.
- Helidon DB Client executes the `select-book` SQL script from application.yaml.
- Sends 404 HTTP status if no book was found for the given name.
- Sends book information to the client.

The `getBook` method reach the book from the database and send the information to the client. The name of the book is located into the url path. If the book is not present in the database, an HTTP 404 is sent back. The `execute()` method is called on the dbClient instance to execute one statement. Nevertheless, it is possible to execute a set of tasks into a single execution unit by using the `transaction()` method.

DbExecute class provides many builders to create statements such as, DML, insert, update, delete, query and get statements. For each statement there are two builders which can be regrouped in 2 categories. Builders with methods containing `Named` keyword, they use a statement defined in the configuration file.

And builders without `Named` keyword, they use a statement passed as an argument. More information on the Helidon DB Client [here](../dbclient.md).

*Add getJsonBook to the LibraryService:*

``` java
private void getJsonBook(ServerRequest request,
                         ServerResponse response) {

    String bookName = request.path()
            .pathParameters()
            .get("name");

    JsonObject bookJson = dbClient.execute()
            .namedGet("select-book", bookName)
            .map(row -> row.as(JsonObject.class))
            .orElseThrow(() -> new NotFoundException(
                    "Book not found: " + bookName));
    response.send(bookJson);
}
```

Instead of sending the `INFO` content of the targeted book, the `getJsonBook` method send the whole row of the database as a `JsonObject`.

*Add addBook to the LibraryService:*

``` java
private void addBook(ServerRequest request,
                     ServerResponse response) {

    String bookName = request.path()
            .pathParameters()
            .get("name");

    String newValue = request.content().as(String.class);
    dbClient.execute()
            .createNamedInsert("insert-book")
            .addParam("name", bookName) 
            .addParam("info", newValue)
            .execute();
    response.status(Status.CREATED_201).send(); 
}
```

- The SQL statement requires the book name and its information. They are provided with `addParam` method.
- A new book was added to library, so an HTTP 201 code is returned.

When a user adds a new book, it uses HTTP PUT method where the book name is in the URL and the information in the request content. To catch this content, the information is retrieved as a string and then the DB Client execute the `insert-book` script to add the book to the library. It requires two parameters, the book name and information which are passed to the dbClient thanks to `addParam` method. An HTTP 201 is sent back as a confirmation.

*Add deleteBook to LibraryService:*

``` java
private void deleteBook(ServerRequest request,
                        ServerResponse response) {

    String bookName = request.path()
            .pathParameters()
            .get("name");

    dbClient.execute().namedDelete("delete-book", bookName); 
    response.status(Status.NO_CONTENT_204).send(); 
}
```

- Execute SQL script from application.yaml to remove a book from the library by its name.
- The required book was removed, so an HTTP 204 is sent.

To remove a book from the library, use the "delete-book" script in the way than previously. If the book is removed successfully, an HTTP 204 is sent back.

### Set Up Routing

*Modify the `routing` method in `Main.java`:*

``` java
static void routing(HttpRouting.Builder routing) {
    routing
            .register("/greet", new GreetService())
            .register("/library", new LibraryService()) 
            .get("/simple-greet", (req, res) -> res.send("Hello World!"));
}
```

- Register the LibraryService to the Routing.

The library service does not yet exist, but you’ll create it in the next step of the guide.

## Build and Run the Library Application

The application is ready to be built and run.

*Run the following to build the application:*

``` bash
mvn package
```

Note that the tests are passing as the `GreetFeature` process was not modified. For the purposes of this demonstration, we only added independent new content to the existing application. Make sure H2 is running and start the Helidon quickstart with this command:

*Run the application*

``` bash
java -jar target/helidon-quickstart-se.jar
```

Once the application starts, check the table LIBRARY is created in the H2 database. To do so, go to the H2 Server console and LIBRARY table should be present in the left column under `jdbc:h2:tcp://localhost:9092/~/test`. If it is not, try to refresh the page, and it should appear.

Use `curl` to send request to the application:

*Get a book from the library*

``` bash
curl -i http://localhost:8080/library/SomeBook
```

*HTTP response*

``` text
HTTP/1.1 404 Not Found
Date: Tue, 12 Jan 2021 14:00:48 +0100
transfer-encoding: chunked
connection: keep-alive
```

There is currently no book inside the library, so the application returns a 404. Yet the application created an empty library table. Try to add a new book.

*Add a book from the library*

``` bash
curl -i -X PUT -d "Fantasy" http://localhost:8080/library/HarryPotter
```

*HTTP response*

``` text
HTTP/1.1 201 Created
Date: Tue, 12 Jan 2021 14:01:08 +0100
transfer-encoding: chunked
connection: keep-alive
```

This command creates an HTTP PUT request with the genre `Fantasy` content at the address [http://localhost:8080/library/{book-name}](http://localhost:8080/library/{book-name}). The 201 code means that Harry Potter book was successfully added to the library. You can now try to get it !

*Get Harry Potter from the library*

``` bash
curl -i http://localhost:8080/library/HarryPotter
```

*HTTP response*

``` text
HTTP/1.1 200 OK
Content-Type: text/plain
Date: Tue, 12 Jan 2021 14:01:14 +0100
connection: keep-alive
content-length: 6

Fantasy
```

The application accepted the request and returned an HTTP 200 OK with the book genre that was added earlier.

*Get Harry Potter from the library in Json*

``` bash
curl -i http://localhost:8080/library/json/HarryPotter
```

*HTTP response*

``` text
HTTP/1.1 200 OK
Content-Type: text/plain
Date: Tue, 12 Jan 2021 14:01:14 +0100
connection: keep-alive
content-length: 6

{"INFO":"Fantasy"}
```

It returns the database row in a Json format for the Harry Potter book. Harry Potter can be removed from the library with the following:

*Remove Harry Potter from the library*

``` bash
curl -i -X DELETE http://localhost:8080/library/HarryPotter
```

*HTTP response*

``` text
HTTP/1.1 204 No Content
Date: Tue, 12 Jan 2021 14:01:22 +0100
connection: keep-alive
```

The book had been removed from the library and confirmed by the 204 HTTP status. To check that the book was correctly deleted, try to get it again.

*Get Harry Potter from the library*

``` bash
curl -i http://localhost:8080/library/HarryPotter
```

*HTTP response*

``` text
HTTP/1.1 404 Not Found
Date: Tue, 12 Jan 2021 14:00:48 +0100
transfer-encoding: chunked
connection: keep-alive
```

The book is not found. We quickly checked, thanks to this suite of command, the application behavior.

*Check the health of your application:*

``` bash
curl http://localhost:8080/observe/health
```

*Response body*

``` json
{
  "status": "UP",
  "checks": [
    {
      "name": "jdbc:h2",
      "status": "UP"
    }
  ]
}
```

It confirms that the database is UP.

*Check the metrics of your application:*

``` bash
curl -H "Accept: application/json" http://localhost:8080/observe/metrics/application
```

*Response body*

``` json
{
  "db.counter.select-book" : 4
}
```

The select-book statement was invoked four times.

### Summary

This guide provided an introduction to the Helidon DB Client’s key features. If you want to learn more, see the Helidon DB Client samples in [GitHub](https://github.com/helidon-io/helidon-examples/tree/helidon-4.x/examples/dbclient).

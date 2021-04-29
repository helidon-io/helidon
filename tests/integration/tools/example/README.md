# Integration Tests with Native Image Application

This project contains an example of integration tests with web service which can
run in Java VM and Native Image mode.

## Building and running the Tests

Maven configuration contains 3 profiles:
* **mysql** to select MySQL database.
* **pgsql** to select PostgreSQL database. Profiles for additional databases can be added.
* **native-image** (optional) to trigger build and execution of web server in Native Image mode.

To build and run the tests in *Java VM* mode with running MySQL database, execute:

     mvn verify -Pdocker -Pmysql -Dapp.config=mysql.yaml \
         -Ddb.user=<database_user> -Ddb.password=<database_password> \
         -Ddb.url=<database_url>

To build and run the tests in *Native Image* mode, execute:

    mvn clean verify -Pdocker -Pmysql -Pnative-image \
        -Dapp.config=mysql.yaml -Ddb.user=<database_user> \
        -Ddb.password=<database_password> -Ddb.url=<database_url>

Project contains script `test.sh` to simplify tests execution:

    Usage: test.sh [-hcjn] -d <database>

        -h print this help and exit
        -c start and stop Docker containers
        -j execute remote application tests in Java VM mode (default)
        -n execute remote application tests in native image mode
        -d <database> select database
           <database> :: mysql | pgsql

## Code structure

Project consists of 2 parts: **web server** and **jUnit tests**.

### Web Server

Web server application contains just two type of classes:
* **ServerMain**: Web server entry point with web server and DB client initialization code.
* **Services**: Web server services.
 * *LifeCycleService*: Handles web server startup, setup and exit.
 * *Tests services*: Services for jUnit tests (e.g. `HelloWorldService`).

### jUnit Tests

jUnit tests consist of two parts: jUnit life cycle extension and jUnit tests.

`ServerLifeCycleExtension` class starts web server and implements setup and exit code. This class also calls web server life cycle services to perform those tasks on server side.

jUnit test classes contain jUnit tests to call web server test services and evaluate returned data.

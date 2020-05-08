# H2 Integration Example

## Overview

This example shows a trivial Helidon MicroProfile application that
uses the Hikari connection pool CDI integration and an H2 in-memory
database.

## Build and run

```bash
mvn package
java -jar target/helidon-integrations-examples-datasource-hikaricp-h2.jar
```

Try the endpoint:
```bash
curl http://localhost:8080/tables
```

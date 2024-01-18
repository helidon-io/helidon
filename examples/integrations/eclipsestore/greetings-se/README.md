# EclipseStore integration example

This example uses EclipseStore to persist a log entry for every greeting

## Build and run

```shell
mvn package
java -jar target/helidon-examples-integrations-eclipsestore-greetings-se.jar
```

## Endpoints

Get default greeting message:
```shell
curl -X GET http://localhost:8080/greet
```

Get greeting message for Joe:
```shell  
curl -X GET http://localhost:8080/greet/Joe
```

Change greeting:
```shell  
curl -X PUT -H "Content-Type: application/json" -d '{"greeting" : "Howdy"}' http://localhost:8080/greet/greeting
```

Get the logs:
```shell  
curl -X GET http://localhost:8080/greet/logs
```

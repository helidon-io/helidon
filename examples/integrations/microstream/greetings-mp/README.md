# Microstream integration example

This example uses Microstream to persist the greetings supplied

## Build and run

```shell
mvn package
java -jar target/helidon-examples-integrations-microstream-greetings-mp.jar
```

## Endpoints

Get default greeting message:
```shell
curl -X GET http://localhost:7001/greet
```

Get greeting message for Joe:  
```shell
curl -X GET http://localhost:7001/greet/Joe
```

Add a greeting:  
```shell
curl -X PUT -H "Content-Type: application/json" -d '{"message" : "Howdy"}' http://localhost:7001/greet/greeting
```
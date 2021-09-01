# Microstream integration example

This example uses Microstream to persist the greetings supplied

## Build and run

```
mvn package
java -jar target/helidon-examples-integrations-microstream-greetings-mp.jar
```

## Endpoints

Get default greeting message:  
curl -X GET http://localhost:7001/greet

Get greeting message for Joe:  
curl -X GET http://localhost:7001/greet/Joe

Add a greeting:  
curl -X PUT -H "Content-Type: application/json" -d '{"greeting" : "Howdy"}' http://localhost:7001/greet/greeting

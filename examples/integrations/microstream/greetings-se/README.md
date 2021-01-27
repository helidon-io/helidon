# Microstream integration example 
This example uses Microstream to persist a log entry for every greeting


## Build and run
```
mvn package
java -jar target/helidon-examples-integrations-microstream-greetings-se.jar
```

##Endpoints
Get default greeting message:  
curl -X GET http://localhost:8080/greet
 
Get greeting message for Joe:  
curl -X GET http://localhost:8080/greet/Joe
 
Change greeting:  
curl -X PUT -H "Content-Type: application/json" -d '{"greeting" : "Howdy"}' http://localhost:8080/greet/greeting
 
Get the logs:  
curl -X GET http://localhost:8080/greet/logs
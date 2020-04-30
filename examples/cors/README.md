
# Helidon SE CORS Example

This example shows a simple greeting application, similar to the one from the 
Helidon SE QuickStart, enhanced with CORS support.

Look at the `resources/application.yaml` file and notice the `restrictive-cors` and `open-cors`
sections. The application loads these to set up CORS for the application's endpoints; two
lines near the end of `Main#createRouting` do the work.

Near the end of the `resources/logging.properties` file, a commented line would turn on `FINE
` logging that would reveal how the Helidon CORS support makes it decisions. To see that logging
uncomment that line and then package and run the application.
  
## Build and run

With JDK11+
```bash
mvn package
java -jar helidon-examples-cors.jar
```

These normal greeting app endpoints work without change:

```bash
curl -X GET http://localhost:8080/greet
{"message":"Hello World!"}

curl -X GET http://localhost:8080/greet/Joe
{"message":"Hello Joe!"}

curl -X PUT -H "Content-Type: application/json" -d '{"greeting" : "Hola"}' http://localhost:8080/greet/greeting

curl -X GET http://localhost:8080/greet/Jose
{"message":"Hola Jose!"}
```

The following requests illustrate the CORS protocol.

By setting the `Origin` and `Host` headers so they do not match we trigger CORS processing in the
 server. Note the returned headers:

```bash
# Follow the CORS protocol for GET
curl -i -X GET -H "Origin: http://foo.com" -H "Host: bar.com" http://localhost:8080/greet

HTTP/1.1 200 OK
Access-Control-Allow-Origin: *
Content-Type: application/json
Date: Thu, 30 Apr 2020 17:25:51 -0500
Vary: Origin
connection: keep-alive
content-length: 27

{"greeting":"Hello World!"}
```

The same happens for a `GET` requesting a personalized greeting:
```bash
#
# Similarly for a personalized greeting
curl -i -X GET -H "Origin: http://foo.com" -H "Host: bar.com" http://localhost:8080/greet/Joe
```
Send a pre-flight request to set the stage for a `PUT` request to change the greeting:
```bash
curl -i -X OPTIONS \
    -H "Access-Control-Request-Method: PUT" \
    -H "Origin: http://foo.com" \
    -H "Host: bar.com" \
    http://localhost:8080/greet/greeting

HTTP/1.1 200 OK
Access-Control-Allow-Methods: PUT
Access-Control-Allow-Origin: http://foo.com
Date: Thu, 30 Apr 2020 17:30:59 -0500
transfer-encoding: chunked
connection: keep-alive
```
Note the Access-Control-Allow-xxx headers returned, indicating that the server accepted the 
pre-flight request.
 
Now send the actual greeting change.
```bash
curl -i -X PUT \
    -H "Origin: http://foo.com" \
    -H "Host: bar.com" \
    -H "Access-Control-Allow-Methods: PUT" \
    -H "Access-Control-Allow-Origin: http://foo.com" \
    -H "Content-Type: application/json" \
    -d "{ \"greeting\" : \"Hola\" }" \
    http://localhost:8080/greet/greeting

HTTP/1.1 204 No Content
Access-Control-Allow-Origin: http://foo.com
Date: Thu, 30 Apr 2020 17:32:55 -0500
Vary: Origin
connection: keep-alive
```

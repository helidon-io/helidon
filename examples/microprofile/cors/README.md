# Helidon MP CORS Example

This example shows a simple greeting application, similar to the one from the 
Helidon MP QuickStart, enhanced with CORS support.

Near the end of the `resources/logging.properties` file, a commented line would turn on `FINE`
logging that would reveal how the Helidon CORS support makes it decisions. To see that logging,
uncomment that line and then package and run the application.

## Build and run

```bash
mvn package
java -jar target/helidon-examples-microprofile-cors.jar
```

## Using the app endpoints as with the "classic" greeting app

These normal greeting app endpoints work just as in the original greeting app:

```bash
curl -X GET http://localhost:8080/greet
{"message":"Hello World!"}

curl -X GET http://localhost:8080/greet/Joe
{"message":"Hello Joe!"}

curl -X PUT -H "Content-Type: application/json" -d '{"greeting" : "Hola"}' http://localhost:8080/greet/greeting

curl -X GET http://localhost:8080/greet/Jose
{"message":"Hola Jose!"}
```

## Using CORS

### Sending "simple" CORS requests

The following requests illustrate the CORS protocol with the example app.

By setting `Origin` and `Host` headers that do not indicate the same system we trigger CORS processing in the
 server:

```bash
# Follow the CORS protocol for GET
curl -i -X GET -H "Origin: http://foo.com" -H "Host: here.com" http://localhost:8080/greet

HTTP/1.1 200 OK
Access-Control-Allow-Origin: *
Content-Type: application/json
Date: Thu, 30 Apr 2020 17:25:51 -0500
Vary: Origin
connection: keep-alive
content-length: 27

{"greeting":"Hola World!"}
```
Note the new headers `Access-Control-Allow-Origin` and `Vary` in the response.

The same happens for a `GET` requesting a personalized greeting (by passing the name of the
 person to be greeted):
```bash
curl -i -X GET -H "Origin: http://foo.com" -H "Host: here.com" http://localhost:8080/greet/Joe
{"greeting":"Hola Joe!"}
```
Take a look at `GreetResource` and in particular the methods named `optionsForXXX` near the end of the class.
There is one for each different subpath that the resource's endpoints handle: no subpath, `/{name}`, and `/greeting`. The 
`@CrossOrigin` annotation on each defines the CORS behavior for the corresponding path. 
The `optionsForUpdatingGreeting` gives specific origins and the HTTP method (`PUT`) constraints for sharing that
resource. The other two `optionsForRetrievingXXXGreeting` methods use default parameters for the `@CrossOrigin` 
annotation: allowing all origins, all methods, etc.

With this in mind, we can see why the two earlier `GET` `curl` requests work.

These are what CORS calls "simple" requests; the CORS protocol for these adds headers to the request and response that
would be exchanged between the client and server even without CORS. 

### "Non-simple" CORS requests

The CORS protocol requires the client to send a _pre-flight_ request before sending a request
that changes state on the server, such as `PUT` or `DELETE` and to check the returned status
and headers to make sure the server is willing to accept the actual request. CORS refers to such `PUT` and `DELETE`
requests as "non-simple" ones.
   
This command sends a pre-flight `OPTIONS` request to see if the server will accept a subsequent `PUT` request from the
specified origin to change the greeting:
```bash
curl -i -X OPTIONS \
    -H "Access-Control-Request-Method: PUT" \
    -H "Origin: http://foo.com" \
    -H "Host: here.com" \
    http://localhost:8080/greet/greeting

HTTP/1.1 200 OK
Access-Control-Allow-Methods: PUT
Access-Control-Allow-Origin: http://foo.com
Date: Thu, 30 Apr 2020 17:30:59 -0500
transfer-encoding: chunked
connection: keep-alive
```
The successful status and the returned `Access-Control-Allow-xxx` headers indicate that the
 server accepted the pre-flight request. That means it is OK for us to send `PUT` request to perform the actual change 
 of greeting. (See below for how the server rejects a pre-flight request.)
```bash
curl -i -X PUT \
    -H "Origin: http://foo.com" \
    -H "Host: here.com" \
    -H "Access-Control-Allow-Methods: PUT" \
    -H "Access-Control-Allow-Origin: http://foo.com" \
    -H "Content-Type: application/json" \
    -d "{ \"greeting\" : \"Cheers\" }" \
    http://localhost:8080/greet/greeting

HTTP/1.1 204 No Content
Access-Control-Allow-Origin: http://foo.com
Date: Thu, 30 Apr 2020 17:32:55 -0500
Vary: Origin
connection: keep-alive
```
And we run one more `GET` to observe the change in the greeting:
```bash
curl -i -X GET -H "Origin: http://foo.com" -H "Host: here.com" http://localhost:8080/greet/Joe
{"greeting":"Cheers Joe!"}
```
Note that the tests in the example `TestCORS` class follow these same steps.



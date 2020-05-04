
# Helidon SE CORS Example

This example shows a simple greeting application, similar to the one from the 
Helidon SE QuickStart, enhanced with CORS support.

Look at the `resources/application.yaml` file and notice the `restrictive-cors` and `open-cors`
sections. The application loads these to set up CORS for the application's endpoints; two
lines near the end of `Main#createRouting` do the work. If you set the system property `useOverride` to true, the
 application will use the `cors` section of the configuration to override the CORS settings otherwise set up in the 
 app. 

Near the end of the `resources/logging.properties` file, a commented line would turn on `FINE
` logging that would reveal how the Helidon CORS support makes it decisions. To see that logging,
uncomment that line and then package and run the application.
  
## Build and run

With JDK11+
```bash
mvn package
java -jar helidon-examples-cors.jar
```

These normal greeting app endpoints work without change in the requests or the responses:

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

By setting `Origin` and `Host` headers that do not match we trigger CORS processing in the
 server. Note the new returned headers `Access-Control-Allow-Origin` and `Vary`:

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

The same happens for a `GET` requesting a personalized greeting (by passing the name of the
 person to be greeted):
```bash
curl -i -X GET -H "Origin: http://foo.com" -H "Host: here.com" http://localhost:8080/greet/Joe
{"greeting":"Hola Joe!"}
```
The CORS protocol requires the client to send a _pre-flight_ request before sending a request
 that changes state on the server, such as `PUT` or `DELETE`, and checking the returned status
  and headers to make sure the server is willing to accept the actual request.
   
Here we send a pre-flight request to get approval for the subsequent `PUT` request to change the
 greeting:
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
Note the successful status and the `Access-Control-Allow-xxx` headers returned, indicating that the
 server accepted and responded to the pre-flight request.
 
Because of the successful pre-flight, it's OK for us to send the actual greeting change.
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
Note that the example `MainTest` class follows these same steps.

## Using overrides
The app accepts CORS override settings if you set the system property `useOverride` to true. 

With the same server running, tepeat the `OPTIONS` request from above, but change the `Origin` to `elsewhere.com`:
```bash
curl -i -X OPTIONS \
    -H "Access-Control-Request-Method: PUT" \
    -H "Origin: http://other.com" \
    -H "Host: here.com" \
    http://localhost:8080/greet/greeting
HTTP/1.1 403 Forbidden
Date: Mon, 4 May 2020 10:49:41 -0500
transfer-encoding: chunked
connection: keep-alive
```
This fails because the app set up CORS using the configuration in `application.yaml` which allows sharing only with 
`foo.com` and `bar.com`. 

Stop and rerun the app, this time telling it to allow overriding:
 
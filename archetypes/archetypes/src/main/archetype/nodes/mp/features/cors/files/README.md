## Using CORS

### Sending "simple" CORS requests

The following requests illustrate the CORS protocol with the example app.

By setting `Origin` and `Host` headers that do not indicate the same system we trigger CORS processing in the
server:

```bash
# Follow the CORS protocol for GET
curl -i -X GET -H "Origin: http://foo.com" -H "Host: here.com" http://localhost:8080/cors

HTTP/1.1 200 OK
Access-Control-Allow-Origin: *
Content-Type: application/json
Date: Thu, 30 Apr 2020 17:25:51 -0500
Vary: Origin
connection: keep-alive
content-length: 27

{"greeting":"Hello World!"}
```
Note the new headers `Access-Control-Allow-Origin` and `Vary` in the response.

These are what CORS calls "simple" requests; the CORS protocol for these adds headers to the request and response which
the client and server exchange anyway.

### "Non-simple" CORS requests

The CORS protocol requires the client to send a _pre-flight_ request before sending a request
that changes state on the server, such as `PUT` or `DELETE`, and to check the returned status
and headers to make sure the server is willing to accept the actual request. CORS refers to such `PUT` and `DELETE`
requests as "non-simple" ones.

This command sends a pre-flight `OPTIONS` request to see if the server will accept a subsequent `PUT` request from the
specified origin to change the greeting:
```bash
curl -i -X OPTIONS \
    -H "Access-Control-Request-Method: PUT" \
    -H "Origin: http://foo.com" \
    -H "Host: here.com" \
    http://localhost:8080/cors/greeting

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
    http://localhost:8080/greet/Hola

HTTP/1.1 200 OK
Access-Control-Allow-Origin: http://foo.com
Date: Thu, 30 Apr 2020 17:32:55 -0500
Vary: Origin
connection: keep-alive

Hola World!
```

Note that the tests in the example `MainTest` class follow these same steps.

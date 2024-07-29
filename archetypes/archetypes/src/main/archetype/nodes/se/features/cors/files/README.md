## Using CORS

The following requests illustrate the CORS protocol with the example app.

By setting `Origin` and `Host` headers that do not indicate the same system we trigger CORS processing in the
server:

```bash
# Follow the CORS protocol for GET
curl -i -X GET -H "Origin: http://foo.com" -H "Host: here.com" http://localhost:8080/cors-greet

HTTP/1.1 200 OK
Access-Control-Allow-Origin: *
Content-Type: application/json
Date: Thu, 30 Apr 2020 17:25:51 -0500
Vary: Origin
connection: keep-alive
content-length: 27

Hello World
```

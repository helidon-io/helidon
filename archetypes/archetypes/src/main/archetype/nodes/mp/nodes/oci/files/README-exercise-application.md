1. Use curl to access the client application
   ```
   curl -X GET http://localhost:8080/greet
   {"message":"Hello World!","date":[2022,4,1]}

   curl -X GET http://localhost:8080/greet/Joe
   {"message":"Hello Joe!","date":[2022,4,1]}

   curl -X PUT -H "Content-Type: application/json" -d '{"greeting" : "Ola"}' http://localhost:8080/greet/greeting
   curl -X GET http://localhost:8080/greet
   {"message":"Ola World!","date":[2022,4,8]}
   ```
2. Use curl to access the health checks:
   ```
   $ curl -X GET  http://localhost:8080/health/live
   {"outcome":"UP","status":"UP","checks":[{"name":"CustomLivenessCheck","state":"UP","status":"UP","data":{"time":1646361485815}}]}
   $ curl -X GET  http://localhost:8080/health/ready
   {"outcome":"UP","status":"UP","checks":[{"name":"CustomReadinessCheck","state":"UP","status":"UP","data":{"time":1646361474774}}]}
   ```

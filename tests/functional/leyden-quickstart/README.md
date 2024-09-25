```bash
mvn clean package
docker build -t leyden-helloworld -f Dockerfile.leyden .
docker run --network host --rm --name leyden-helloworld leyden-helloworld
```


```
---- Vanilla 
1667 milliseconds (since JVM startup)

Measuring ...Running 10s test @ http://localhost:7001
  16 threads and 16 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency   622.78us    1.37ms  27.81ms   92.19%
    Req/Sec     4.09k     2.06k    8.96k    61.38%
  651144 requests in 10.02s, 85.07MB read
Requests/sec:  64978.74
Transfer/sec:      8.49MB


---- Leyden
 578 milliseconds (since JVM startup)

 Measuring ...Running 10s test @ http://localhost:7001
  16 threads and 16 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency   446.13us    0.86ms  22.79ms   92.21%
    Req/Sec     4.57k     1.30k    8.82k    70.10%
  729398 requests in 10.10s, 95.30MB read
Requests/sec:  72211.76
Transfer/sec:      9.43MB
```
Synchronous API for Helidon SE
---

These APIs reap benefits of project Loom (https://jdk.java.net/loom/).

If you run these on the loom build of the JDK, the application
will be synchronous in code, yet non-blocking.


Results of a Jmeter test:
Sample: 10 000 000 requests

Reactive
- using reactive service implementation on Java 16 (with loom)

Throughput: 120 310 transactions/second 

Loom
- using unbounded virtual executor service on Java 16 (with loom)
Throughput: 112 146 transaction/second
Throughput: 117 307 transaction/second when not renaming threads

Blocking
- using Helidon executor service on Java 15

Throughput: 104289 transactions/second 
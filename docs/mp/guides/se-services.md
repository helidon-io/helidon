# Reusing Helidon SE services

This guide shows how reuse Helidon SE Service in your Helidon MP application.

## What You Need

For this 10 minute tutorial, you will need the following:

|  |  |
|----|----|
| [Java SE 21](https://www.oracle.com/technetwork/java/javase/downloads) ([Open JDK 21](http://jdk.java.net)) | Helidon requires Java 21+ (25+ recommended). |
| [Maven 3.8+](https://maven.apache.org/download.cgi) | Helidon requires Maven 3.8+. |
| [Docker 18.09+](https://docs.docker.com/install/) | If you want to build and run Docker containers. |
| [Kubectl 1.16.5+](https://kubernetes.io/docs/tasks/tools/install-kubectl/) | If you want to deploy to Kubernetes, you need `kubectl` and a Kubernetes cluster (you can [install one on your desktop](../../about/kubernetes.md)). |

Prerequisite product versions for Helidon 4.4.0-SNAPSHOT

*Verify Prerequisites*

```bash
java -version
mvn --version
docker --version
kubectl version
```

*Setting JAVA_HOME*

```bash
# On Mac
export JAVA_HOME=`/usr/libexec/java_home -v 21`

# On Linux
# Use the appropriate path to your JDK
export JAVA_HOME=/usr/lib/jvm/jdk-21
```

Helidon MP supports [WebServer routing](../server.md) which brings possibility for reusing `io.helidon.webserver.HttpService` implementations in Helidon MP. Such feature can be quite useful for common solutions for filtering, auditing, logging or augmenting REST endpoints in hybrid Helidon SE/MP environment.

Let’s define simple Helidon SE Service for adding special header to every REST response:

```java
public class CoolingService implements HttpService, Handler {

    public static final HeaderName COOL_HEADER_NAME = HeaderNames.create("Cool-Header");
    public static final String COOLING_VALUE = "This is way cooler response than ";

    @Override
    public void routing(HttpRules rules) {
        rules.any(this);
    }

    @Override
    public void handle(ServerRequest req, ServerResponse res) {
        res.headers().add(COOL_HEADER_NAME, COOLING_VALUE);
        res.next();
    }
}
```

It’s easy to use it with Helidon SE:

```java
WebServer.builder()
        .routing(it -> it
                .register("/cool", new CoolingService())) 
        .config(config)
        .mediaContext(it -> it
                .addMediaSupport(JsonpSupport.create()))
        .build()
        .start();
```

- register service with routing path

And not much harder to use it with Helidon MP:

```java
@ApplicationScoped
public class MyBean {

    @Produces
    @ApplicationScoped
    @RoutingPath("/cool")
    public HttpService coolService() {
        return new CoolingService();
    }

}
```

You can leverage annotations:

- @RoutingPath - path of the WebServer service
- @RoutingName - select routing when [serving requests on multiple ports](../server.md)

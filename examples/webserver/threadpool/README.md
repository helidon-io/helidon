# Helidon WebServer Thread Pool Example

This example shows how to use an application specific threadpool.

With the Helidon WebServer you do not want to block the Netty thread that is executing
your Handler. So you either need to use WebServer's reactive APIs:

```
 request.content().as(JsonObject.class)
            .thenAccept(jo -> doSomething(jo, response))
```

Or pass the request off to a thread pool dedicated to your business logic. This
example shows how to do this in `getMessageSlowlyHandler`.

Helidon's `ThreadPoolSupplier` provides thread pools that automatically propagate
request `Context` so that tracing and authentication information is preserved across
threads.

You can use the `Context` registry to propagate your own data across threads. You can
 also do this by passing the information directly to your `Runnable`. The
example shows both techniques.

## Configuration

See `application.yaml` for an example of how you can configure the number of Netty
worker threads, as well as provide a configuration for the dedicated application
threadpool.

## Build and run

With JDK11+
```bash
mvn package
java -jar target/helidon-examples-webserver-threadpool.jar
```

When the server starts up you will see it display some information about the application
thread pool. This should reflect what is specified in `application.yaml`. For example:

```
Application thread pool: ThreadPool 'helidon-thread-pool-2' {corePoolSize=5, maxPoolSize=50,
 queueCapacity=10000, growthThreshold=1000, growthRate=0%, averageQueueSize=0.00,
 peakQueueSize=0, averageActiveThreads=0.00, peakPoolSize=5, currentPoolSize=5,
 completedTasks=0, failedTasks=0, rejectedTasks=0}
```

## Exercise the application

Each request will return the name of the thread the created the response. For example:

```
$ curl -X GET http://localhost:8080/greet/hello
{"message":"Hello hello!","thread":"Thread[nioEventLoopGroup-3-2,10,main]"}
```

`nioEventLoopGroup-` indicates that this is a Netty worker thread. To exercise
the application thread pool do this:

```
curl -X GET http://localhost:8080/greet/slowly/Joe
{"message":"Hello Joe!","thread":"Thread[my-thread-1,5,helidon-thread-pool-2]"}
```

You'll notice that the response takes ~3 seconds to return -- that's an artificial delay
we have in our handler. Also note that the thread name starts with `my-thread-`. That indicates
this is a tread from the dedicated application thread pool.

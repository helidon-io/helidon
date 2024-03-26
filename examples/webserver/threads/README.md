# Helidon SE Threading Example

Helidon's adoption of virtual threads has eliminated a lot of the headaches
of thread pools and thread pool tuning. But there are still cases where using
application specific executors is desirable. This example illustrates two
such cases:

1. You want to execute multiple tasks in parallel.
2. You want to execute a CPU intensive, long running operation.

Let's look at these two use cases in a bit more detail.

## Use Case 1: Executing Tasks in Parallel

In this case you have an endpoint that wants to execute multiple tasks in parallel
that perform some blocking operations.
Examples of this might be making multiple calls to a database or to some other service.
Virtual threads are a good fit for this because they are lightweight and do not consume
platform threads when performing blocking operations (like network I/O).

The `fanout` endpoint in this example demonstrates this use case. You pass the endpoint
the number of parallel tasks to execute and it simulates remote client calls by using
the Helidon WebClient to call the `sleep` endpoint on the server.

## Use Case 2: Executing a CPU Intensive Task

In this case you have an endpoint that performs an in-memory, CPU intensive task.
This is not a good fit for virtual threads because the virtual thread would be pinned to
a platform thread -- potentially causing unbounded consumption of platform threads. Instead,
the example uses a small, bounded pool of platform threads. Bounded meaning that the number
of threads and the size of the work queue are both limited and will reject work when they fill up.
This lets you have tight control over the resources you allocate to these CPU
intensive tasks.

The `compute` endpoint in this example demonstrates this use case. You pass the endpoint
the number of times you want to make the computation, and it uses a small bounded pool
of platform threads to execute the task. 

## Use of Helidon's ThreadPoolSupplier and Configuration

The example uses `io.helidon.common.configurable.ThreadPoolSupplier` to create the 
two executors used in the example. This provides a couple of benefits:

1. ThreadPoolSupplier supports a number of tuning parameters that enable us to configure a small, bounded threadpool.
2. You can drive the thread pool configuration via Helidon config -- see this example's `application.yaml`
3. You get propagation of Helidon's Context which supports Helidon's features as well as direct use by the application.

## Logging

In `logging.properties` we increase the log level for `io.helidon.common.configurable.ThreadPool`
so that you can see the values used to configure the platform thread pool.

## Build and run

```bash
mvn package
java -jar target/helidon-examples-webserver-threads.jar
```

You will see a line like the following:
```
ThreadPool 'application-platform-executor-thread-pool-1' {corePoolSize=1, maxPoolSize=2,
 queueCapacity=10, growthThreshold=1000, growthRate=0%, averageQueueSize=0.00, peakQueueSize=0, averageActiveThreads=0.00, peakPoolSize=0, currentPoolSize=0, completedTasks=0, failedTasks=0, rejectedTasks=0}
```
This reflects the configuration of the platform thread pool created by the application
and used by the `compute` endpoint. At most the thread pool will consume two platform
threads for computations. The work queue is limited to 10 entries to allow for small
bursts of requests. 

## Exercise the application

__Compute:__
```
curl -X GET http://localhost:8080/thread/compute/5
```
Depending on the speed of your machine this should take a few seconds to complete. You
can increase the number to force the computation to take longer.

The request returns the results of the computation (not important!).

__Fanout:__
```
curl -X GET http://localhost:8080/thread/fanout/5
```
This will simulate a fanout of five remote calls. Each call will sleep anywhere from
0 to 4 seconds. Since the requests are executed in parallel the request should not
take longer than 4 seconds total. 

The request returns a list of numbers showing the sleep value of each remote client call.

__Sleep:__
```
curl -X GET http://localhost:8080/thread/sleep/4
```
This is a simple endpoint that just sleeps for the specified number of seconds. It is
used by the `fanout` endpoint.

The request returns the number of seconds requested to sleep.

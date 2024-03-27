# Helidon SE Threading Example

Helidon's adoption of virtual threads has eliminated a lot of the headaches
of thread pools and thread pool tuning. But there are still cases where using
application specific executors is desirable. This example illustrates two
such cases:

1. Using a virtual thread executor to execute multiple tasks in parallel.
2. Using a platform thread executor to execute long-running CPU intensive operations.

## Build and run

```bash
mvn package
java -jar target/helidon-examples-webserver-threads.jar
```

## Exercise the application

__Compute:__
```
curl -X GET http://localhost:8080/thread/compute/5
```
The `compute` endpoint runs a costly floating point computation using a platform thread.
Increase the number to make the computation more costly (and take longer).

The request returns the results of the computation (not important!).

__Fanout:__
```
curl -X GET http://localhost:8080/thread/fanout/5
```
The `fanout` endpoint simulates a fanout of remote calls that are run in parallel using
virtual threads. Each remote call invokes the server's `sleep` endpoint sleeping anywhere from
0 to 4 seconds. Since the remote requests are executed in parallel the curl request should not
take longer than 4 seconds to return. Increase the number to have more remote calls made
in parallel.

The request returns a list of numbers showing the sleep value of each remote client call.

__Sleep:__
```
curl -X GET http://localhost:8080/thread/sleep/4
```
This is a simple endpoint that just sleeps for the specified number of seconds. It is
used by the `fanout` endpoint.

The request returns the number of seconds requested to sleep.

## Further Discussion

### Use Case 1: Virtual Threads: Executing Tasks in Parallel

Sometimes an endpoint needs to perform multiple blocking operations in parallel:
querying a database, calling another service, etc. Virtual threads are a
good fit for this because they are lightweight and do not consume platform
threads when performing blocking operations (like network I/O).

The `fanout` endpoint in this example demonstrates this use case. You pass the endpoint
the number of parallel tasks to execute and it simulates remote client calls by using
the Helidon WebClient to call the `sleep` endpoint on the server.

### Use Case 2: Platform Threads: Executing a CPU Intensive Task

If you have an endpoint that performs an in-memory, CPU intensive task, then
platform threads might be a better match. This is because a virtual thread would be pinned to
a platform thread throughout the computation -- potentially causing unbounded consumption
of platform threads. Instead, the example uses a small, bounded pool of platform
threads to perform computations. Bounded meaning that the number of threads and the
size of the work queue are both limited and will reject work when they fill up.
This gives the application tight control over the resources allocated to these CPU intensive tasks.

The `compute` endpoint in this example demonstrates this use case. You pass the endpoint
the number of times you want to make the computation, and it uses a small bounded pool
of platform threads to execute the task. 

### Use of Helidon's ThreadPoolSupplier and Configuration

This example uses `io.helidon.common.configurable.ThreadPoolSupplier` to create the 
two executors used in the example. This provides a few benefits:

1. ThreadPoolSupplier supports a number of tuning parameters that enable us to configure a small, bounded threadpool.
2. You can drive the thread pool configuration via Helidon config -- see this example's `application.yaml`
3. You get propagation of Helidon's Context which supports Helidon's features as well as direct use by the application.

### Logging

In `logging.properties` the log level for `io.helidon.common.configurable.ThreadPool`
is increased so that you can see the values used to configure the platform thread pool.
When you start the application you will see a line like
```
ThreadPool 'application-platform-executor-thread-pool-1' {corePoolSize=1, maxPoolSize=2,
 queueCapacity=10, growthThreshold=1000, growthRate=0%, averageQueueSize=0.00, peakQueueSize=0, averageActiveThreads=0.00, peakPoolSize=0, currentPoolSize=0, completedTasks=0, failedTasks=0, rejectedTasks=0}
```
This reflects the configuration of the platform thread pool created by the application
and used by the `compute` endpoint. At most the thread pool will consume two platform
threads for computations. The work queue is limited to 10 entries to allow for small
bursts of requests.

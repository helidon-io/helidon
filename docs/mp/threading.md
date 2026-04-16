# Threading

## Overview

Helidon 4 has been written from the ground up to take full advantage of Java 21’s virtual threads. With this new architecture, threads are no longer a scarce resource that need to be pooled and managed, instead they are an abundant resource that can be created as needed to satisfy your application needs.

In most cases, users do not need to worry about thread management and are able to run any type of task on virtual threads provided by the Helidon Webserver. However, there are certain use cases where tasks may need to be executed on platform threads: these include cpu-intensive tasks as well as tasks that may pin a virtual thread to a platform thread due to the use of synchronized blocks. Many libraries that are typically used in Helidon applications have been updated to take full advantage of virtual threads by avoiding unwanted synchronization blocks, yet this process is still underway and some legacy libraries may never be fully converted.

Helidon MP supports a new `@ExecuteOn` annotation to give developers full control on how to run tasks. This annotation can be applied to any CDI bean method to control the type of thread in which invocations of that method shall execute on. If such a method returns `CompletionStage` or `CompletableFuture`, it is assumed to be asynchronous and shall execute in a new thread but without blocking the caller’s thread.

## Maven Coordinates

To enable ExecuteOn, add the following dependency to your project’s `pom.xml` (see [Managing Dependencies](../about/managing-dependencies.md)).

``` xml
<dependency>
    <groupId>io.helidon.microprofile</groupId>
    <artifactId>helidon-microprofile-cdi</artifactId>
</dependency>
```

## API

The API consists of a single `@ExecuteOn` annotation (with a few parameters) that can be applied to any CDI bean method.

> [!NOTE]
> This feature is based on CDI interceptors, so using it on a non-CDI bean method will have no effect.

|  |  |  |
|----|----|----|
| Name | Value | Description |
| value | ThreadType.PLATFORM, ThreadType.VIRTUAL, ThreadType.EXECUTOR | Type of thread used to execute a method invocation |
| timeout | A long value | Maximum wait time for the method to return a value before triggering a timeout exception |
| unit | A `TimeUnit` value | Unit for `timeout` parameter |
| executorName | The name of an executor from which to obtain a thread | CDI producer with a `@Named` qualifier to access the executor |

## Configuration

The implementation of the `@ExecuteOn` annotation takes advantage of Helidon’s `ThreadPoolSupplier` to (lazily) create a pool of platform threads. The default configuration for this thread pool can be overridden using the (root) config key `execute-on.platform` as shown in the example below.

``` yaml
execute-on:
  platform:
    thread-name-prefix: "my-platform-thread"
    core-pool-size: 1
    max-pool-size: 2
    queue-capacity: 10
```

For more information see the Javadoc for [io.helidon.common.configurable.ThreadPoolSupplier](/apidocs/io.helidon.common.configurable/io/helidon/common/configurable/ThreadPoolSupplier.html). For virtual threads, only the thread name prefix can be overridden as follows:

``` yaml
execute-on:
  virtual:
    thread-name-prefix: "my-virtual-thread"
```

## Examples

1.  The following example creates a new platform thread from a (configurable) default pool to execute a cpu-intensive task. Platform threads are a scarce resource, creating threads of type `PLATFORM` should be done responsibly!

    ``` java
    public class MyPlaformBean {

        @ExecuteOn(ThreadType.PLATFORM)
        int cpuIntensive(int n) {
            return PiDigitCalculator.nthDigitOfPi(n);
        }
    }
    ```

2.  The next example also uses a platform thread, but this time the developer is also responsible for providing an executor; this is done by creating a CDI provider with the same executor name (using the `@Named` annotation) as the one in the annotation parameter. Note that for simplicity the producer in this example is also part of the same bean, but that is not a requirement in CDI.

    ``` java
    public class MyExecutorBean {

        @ExecuteOn(value = ThreadType.EXECUTOR, executorName = "my-executor")
        int cpuIntensive(int n) {
            return PiDigitCalculator.nthDigitOfPi(n);
        }

        @Produces
        @Named("my-executor")
        ExecutorService myExecutor() {
            return Executors.newFixedThreadPool(2);
        }
    }
    ```

3.  It is also possible to explicitly execute a method in a virtual thread, blocking the caller’s thread until the method execution is complete.

    ``` java
    public class MyVirtualBean {

        @ExecuteOn(ThreadType.VIRTUAL)
        void someTask() {
            // run task on virtual thread
        }
    }
    ```

4.  Finally, a method can be executed in another thread but without blocking the caller’s thread. This behavior is triggered automatically when the bean method returns `CompletionStage` or `CompletableFuture`.

    ``` java
    public class MyVirtualBeanAsync {

        @ExecuteOn(ThreadType.VIRTUAL)
        CompletionStage<String> someTask() {
            // run task on virtual thread without blocking caller
            return CompletableFuture.completedFuture("DONE");
        }
    }
    ```

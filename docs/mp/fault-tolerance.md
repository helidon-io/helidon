# Fault Tolerance

## Overview

Fault Tolerance is part of the MicroProfile set of specifications. This API
defines mostly annotations that improve application robustness by providing
support to conveniently handle error conditions (faults) that may occur in
real-world applications. Examples include service restarts, network delays,
temporal infrastructure instabilities, etc.

## Maven Coordinates

To enable MicroProfile Fault Tolerance, either add a dependency on the
[helidon-microprofile bundle](introduction.md) or add the following dependency
to your project’s `pom.xml` (see [Managing
Dependencies](../dependency-management.md)).

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.microprofile</groupId>
  <artifactId>helidon-microprofile-fault-tolerance</artifactId>
</dependency>
```

## API

The MicroProfile Fault Tolerance specification defines a set of annotations to
decorate classes and methods in your application for the purpose of improving
its robustness. Many of these annotations can be applied at the class or method
level: if applied at the class level, they will impact all methods in the class;
if applied both at the class and method level, the latter will take precedence
over the former.

The following table provides a brief description of each of these annotations,
including its parameters and default values.

<table>
<thead>
<tr>
<th>Annotation</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td>
<!-- @code java --><pre><code>@Retry(
    maxRetries=3,
    delay=0,
    delayUnit=ChronoUnit.MILLIS,
    maxDuration=180000,
    durationUnit=ChronoUnit.MILLIS,
    jitter=200,
    jitterDelayUnit=ChronoUnit.MILLIS,
    retryOn={Exception.class},
    abortOn={}
)</code></pre>
</td>
<td style="vertical-align: middle">
Retries the execution of a method if a failure is encountered.
Annotation attributes can be used to control the number of retries, delay between retries and which exceptions to retry or abort on.
</td>
</tr>
<tr>
<td>
<!-- @code java --><pre><code>@Timeout(
    value=1000,
    unit=ChronoUnit.MILLIS
)</code></pre>
</td>
<td style="vertical-align: middle">
Defines an upper bound on a method’s execution time. Default value is 1 second.
</td>
</tr>
<tr>
<td>
<!-- @code java --><pre><code>@CircuitBreaker(
    failOn={Throwable.class},
    skipOn={},
    delay=5000,
    delayUnit=ChronoUnit.MILLIS,
    requestVolumeThreshold=20,
    failureRation=.50,
    successThreshold=1
)</code></pre>
</td>
<td style="vertical-align: middle">
Defines a policy to avoid repeated execution of logic that is likely to fail.
A circuit breaker can be <em>closed</em>, <em>open</em> or <em>half-open</em>.
<ul>
<li>In <em>closed</em> state a circuit breaker will execute logic normally.</li>
<li>In <em>open</em> state a circuit breaker will prevent execution of logic that has been seen to fail.</li>
<li>In <em>half-open</em> state a circuit breaker will allow <em>trial</em> executions in an attempt to switch its internal
state to <em>closed</em>.</li>
</ul>
The other annotation parameters are used to control how these state transitions are triggered.
</td>
</tr>
<tr>
<td><!-- @code java --><pre><code>@Bulkhead(
    value=10,
    waitingTaskQueue=10
)</code></pre></td>
<td style="vertical-align: middle">
Defines a policy to limit the number of concurrent executions allowed over some application logic.
A queue is used to park tasks awaiting execution after the limit has been reached.
A queue is only active when invocations are <code>@Asynchronous</code>.</td>
</tr>
<tr>
<td>
<!-- @code java --><pre><code>@Fallback(
    value=DEFAULT.class,
    fallbackMethod=&quot;&quot;,
    applyOn={Throwable.class},
    skipOn={}
)</code></pre>
</td>
<td style="vertical-align: middle">
Establishes a handler to be executed upon encountering an invocation failure.
A handler is either a class that implements <code>FallbackHandler&lt;T&gt;</code> or just a simple method in the same class.
Additional properties are used to control the conditions under which these handlers are called.
</td>
</tr>
<tr>
<td>
<!-- @code java --><pre><code>@Asynchronous</code></pre>
</td>
<td style="vertical-align: middle">
Executes an invocation asynchronously without blocking the calling thread.
Annotated method must return <code>Future</code> or <code>CompletionStage</code>.
Typically used to avoid blocking the calling thread on I/O or on a long-running computation.
</td>
</tr>
</tbody>
</table>

## Configuration

Helidon’s implementation uses two types of thread pools: normal and scheduled.
The default core size of these executors is 20; however, that can be configured
using an `application.yaml` file as follows:

```yaml
executor:
  core-pool-size: 32

scheduled-executor:
  core-pool-size: 32
```

> [!NOTE]
> There is currently *no support* to configure these executor properties via a
> `microprofile-config.properties` file.

For a complete set of properties available to configure these executors, see
[ThreadPoolConfig][threadpoolconfig].
[ScheduledThreadPoolConfig][scheduledthreadp].

## Examples

1.  The method `retryWithFallback` shall be called at most 3 times, first call
    plus 2 retries, with a delay of 400 milliseconds between calls. If none of
    the calls is successful, the `onFailure` method shall be called as a
    fallback mechanism.

    ```java
    @Retry(maxRetries = 2, delay = 400L)
    @Fallback(fallbackMethod = "onFailure")
    String retryWithFallback() {
        return getMyValue();
    }
    ```

2.  The method `timedCircuitBreaker` defines a *rolling window* of size 10 and a
    policy to open the circuit breaker after 4 or more failures occur in that
    window, and to transition back to half-open state after 3 consecutive and
    successful runs. Additionally, it sets an overall timeout for the invocation
    of 1.5 seconds.

    ```java
    @Timeout(1500)
    @CircuitBreaker(requestVolumeThreshold = 10,
                failureRatio = .4,
                successThreshold = 3)
    void timedCircuitBreaker() throws InterruptedException {
        //...
    }
    ```

3.  The method `executeWithQueueAndFallback` defines a bulkhead that will limit
    the number of concurrent calls to a maximum of 2; any additional tasks shall
    be queued up to a maximum of 10. Finally, if an error occurs the `onFailure`
    method shall be called as a fallback mechanism. The `@Asynchronous`
    annotation is needed to enable queueing of bulkhead tasks.

    ```java
    @Asynchronous
    @Fallback(fallbackMethod = "onFailure")
    @Bulkhead(value = 2, waitingTaskQueue = 10)
    CompletableFuture<String> executeWithQueueAndFallback() {
        return getMyValueAsync();
    }
    ```

## Additional Information

For additional information about this API, see the [MicroProfile Fault Tolerance
Javadocs][microprofile-fau].

## Reference

- [MicroProfile Fault Tolerance][microprofile-fau-2]

[threadpoolconfig]: https://helidon.io/docs/v4/apidocs/io.helidon.common.configurable/io/helidon/common/configurable/ThreadPoolConfig.html
[scheduledthreadp]: https://helidon.io/docs/v4/apidocs/io.helidon.common.configurable/io/helidon/common/configurable/ScheduledThreadPoolConfig.html
[microprofile-fau]: https://download.eclipse.org/microprofile/microprofile-fault-tolerance-4.0.2/apidocs
[microprofile-fau-2]: https://download.eclipse.org/microprofile/microprofile-fault-tolerance-4.0.2/microprofile-fault-tolerance-spec-4.0.2.html

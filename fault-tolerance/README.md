Fault Tolerance
----

Weight of interceptors for Fault tolerance (when using annotations) - defined in `FaultTolerance`:

| Weight | FT Handler      |
|--------|-----------------|
| 10     | Retry           |
| 20     | Bulkhead        |
| 30     | Circuit Breaker |
| 40     | Timeout         |
| 50     | Async           |
| 60     | Fallback        |

This means that if a method has a fallback and a retry, the fallback interceptor is called first, retry second.
In case the target method fails, it would first be retried, and then a fallback would be called if all retries fail.

# Retry

| Option             | Value                   |
|--------------------|-------------------------|
| Interface          | `Retry`                 |
| Can be injected    | yes                     |
| Named              | yes                     |
| Config interface   | `RetryConfig`           |
| Config driven      | yes                     |
| Implementation     | `RetryImpl`             |
| Interceptor        | `RetryInterceptor`      |
| Generated code     | yes                     |
| Generated contract | `RetryMethod`           |
| Throwables         | `RetryTimeoutException` |
| Annotation(s)      | `FaultTolerance.Retry`  |

Retry can have named instances (either from configuration of from custom `Provider`). Each such instance can have a different
configuration and can be references from annotations.
When used with `@FtRetry`, a named instance is located, and if not present, a custom instance is created based on the annotation
setup.

# Bulkhead

| Option             | Value                     |
|--------------------|---------------------------|
| Interface          | `Bulkhead`                |
| Can be injected    | yes                       |
| Named              | yes                       |
| Config interface   | `BulkheadConfig`          |
| Config driven      | yes                       |
| Implementation     | `BulkheadImpl`            |
| Interceptor        | `BulkheadInterceptor`     |
| Generated code     | no                        |
| Generated contract | N/A                       |
| Throwables         | `BulkheadException`       |
| Annotation(s)      | `FaultTolerance.Bulkhead` |

Bulkhead can have named instances (either from configuration of from custom `Provider`). Each such instance can have a different
configuration and can be references from annotations.
When used with `@FtBulkhead`, a named instance is located, and if not present, a custom instance is created based on the
annotation
setup.

# CircuitBreaker

| Option             | Value                           |
|--------------------|---------------------------------|
| Interface          | `CircuitBreaker`                |
| Can be injected    | yes                             |
| Named              | yes                             |
| Config interface   | `CircuitBreakerConfig`          |
| Config driven      | yes                             |
| Implementation     | `CircuitBreakerImpl`            |
| Interceptor        | `CircuitBreakerInterceptor`     |
| Generated code     | yes                             |
| Generated contract | `CircuitBreakerMethod`          |
| Throwables         | `CircuitBreakerOpenException`   |
| Annotation(s)      | `FaultTolerance.CircuitBreaker` |

CircuitBreaker can have named instances (either from configuration of from custom `Provider`). Each such instance can have a
different
configuration and can be references from annotations.
When used with `@FtCircuitBreaker`, a named instance is located, and if not present, a custom instance is created based on the
annotation
setup.

# Timeout

| Option             | Value                    |
|--------------------|--------------------------|
| Interface          | `Timeout`                |
| Can be injected    | yes                      |
| Named              | yes                      |
| Config interface   | `TimeoutConfig`          |
| Config driven      | yes                      |
| Implementation     | `TimeoutImpl`            |
| Interceptor        | `TimeoutInterceptor`     |
| Generated code     | no                       |
| Generated contract | N/A                      |
| Throwables         | `TimeoutException`       |
| Annotation(s)      | `FaultTolerance.Timeout` |

# Async

| Option             | Value                  |
|--------------------|------------------------|
| Interface          | `Async`                |
| Can be injected    | yes                    |
| Named              | yes                    |
| Config interface   | `AsyncConfig`          |
| Config driven      | yes                    |
| Implementation     | `AsyncImpl`            |
| Interceptor        | `AsyncInterceptor`     |
| Generated code     | no                     |
| Generated contract | N/A                    |
| Throwables         | N/A                    |
| Annotation(s)      | `FaultTolerance.Async` |

`@FtAsync` will run the method in an executor service, and wait for its completion. The executor can be named (contract
of `ExecutorService`). By default uses a thread per task
executor service with virtual threads.

# Fallback

| Option             | Value                     |
|--------------------|---------------------------|
| Interface          | `Fallback`                |
| Can be injected    | no                        |
| Named              | N/A                       |
| Config interface   | N/A                       |
| Config driven      | N/A                       |
| Implementation     | `FallbackImpl`            |
| Interceptor        | `FallbackInterceptor`     |
| Generated code     | yes                       |
| Generated contract | `FallbackMethod`          |
| Throwables         | N/A                       |
| Annotation(s)      | `FaultTolerance.Fallback` |

As fallback is a method reference within the same type, it cannot be injected or shared.
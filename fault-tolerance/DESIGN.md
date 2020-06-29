Fault Tolerance
---

Fault tolerance (FT) covers a wide area of features. The following document
describes each FT feature and its API for Helidon SE (or approach to use to achieve such a feature using
existing APIs).

# Common API
The FT requires executor service (or more) to handle some of the features provided. To be able to
configure FT for the whole application, a set of static methods exists on class `FaultTolerance`.

- `FaultTolerance.config(Config)` - use a Helidon config instance to configure defaults  

# Asynchronous
Provides an asynchronous execution for a blocking operation. As Helidon SE network stack is using a 
non blocking reactive API, applications cannot block the threads. You can use this API to execute a blocking 
operation in a separate executor service and obtain its result as a Helidon reactive `Single`.

Configuration:
- executor service

# Bulkhead
Limits the number of parallel calls to a single resource.

Configuration:
- parallel execution limit
- executor service
- queue
- number of queued records

# Circuit Breaker
Defines a circuit breaker policy to an individual method or a class.
A circuit breaker aims to prevent further damage by not executing functionality that is doomed to fail. After a failure situation has been detected, circuit breakers prevent methods from being executed and instead throw exceptions immediately. After a certain delay or wait time, the functionality is attempted to be executed again.
A circuit breaker can be in one of the following states:
Closed: In normal operation, the circuit is closed. If a failure occurs, the Circuit Breaker records the event. In closed state the requestVolumeThreshold and failureRatio parameters may be configured in order to specify the conditions under which the breaker will transition the circuit to open. If the failure conditions are met, the circuit will be opened.
Open: When the circuit is open, calls to the service operating under the circuit breaker will fail immediately. A delay may be configured for the circuit breaker. After the specified delay, the circuit transitions to half-open state.
Half-open: In half-open state, trial executions of the service are allowed. By default one trial call to the service is permitted. If the call fails, the circuit will return to open state. The successThreshold parameter allows the configuration of the number of trial executions that must succeed before the circuit can be closed. After the specified number of successful executions, the circuit will be closed. If a failure occurs before the successThreshold is reached the circuit will transition to open.
Circuit state transitions will reset the circuit breaker's records.

Configuration:
- fail on `<? extends Throwable>` - these are failures
- skip on `<? extends Throwable>` - these are not failures
- delay (duration) - how long before transitioning from open to half-open
- volume threshold - rolling window size
- ratio (percentage) - how many failures will trigger this to open
- success threshold - how many successful calls will close a half-open breaker 

# Fallback
What to call when this fails.
Could be replaced with `onException` of `CompletionStage`

May provide a context of execution with information such as 
 - `Class`
 - some description of the method called (not reflection - we want to avoid reflection at all costs)
 - parameter values posted to original method

Configuration:
- fallback method/handler
- applyOn `<? extends Throwable>` - these are failures
- skip on `<? extends Throwable>` - these are not failures

# Retry
Retry execution.

Configuration:
- maximal number of retries
- delay between retries (duration)
- overall maximal duration 
- jitter (randomize delays a bit - duration) - a jitter of 200 ms will randomly add between -200 and 200 milliseconds to each retry delay.
- retry on `<? extends Throwable>` - these are failures
- abort on `<? extends Throwable>` - these will immediately abort retries 

# Timeout 
Can be simply replaced with `Single.timeout`

Configuration:
- overall timeout (duration)

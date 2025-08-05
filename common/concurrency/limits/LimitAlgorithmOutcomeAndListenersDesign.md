# Limit Algorithm Listeners Processing

Developers--and Helidon itself--can create listeners to which the limit algorithms call back at key moments in the processing of an item of work (such as an incoming HTTP request).

Helidon allows developers to associate `LimitAlgorithmListener` instances with the `FixedLimit` and `AimdLimit` limit algorithms. Helidon also automatically discovers implementations of the `LimitAlgorithmListenerProvider` SPI that are in the service registry and uses them to create `LimitAlgorithmListener` instances. The fixed and AIMD limit implementations then call back to each known listener when an item of work is accepted or rejected and when each accepted item is completed (succeeds, fails, or is ignored).

To decouple the limit algorithms themselves from the details of these listeners, each listener returns an opaque listener context when a work item is accepted or rejected. Because the limits code and therefore the listeners run in a performance-sensitive code path, it is generally best for the listener itself to _avoid_ doing much work at all. Instead, the code which uses the limit algorithm--for example, HTTP request processing--would normally store these listener context objects somewhere accessible to downstream logic--for example, an HTTP request filter--so that downstream logic can retrieve the specific listener contexts it cares about and use them. Each listener instance indicates if it should be propagated in this way via its `shouldBePropagated()` method so the caller can propagate those instances that need it.

For example, to create a tracing span representing the time that a work item spent waiting due to concurrency limits, the trace span limit listener responds to the deferred accept or deferred reject notifications by storing the outcome (which includes the wait start and wait end times) in its listener context. The HTTP processing adds this listener context--and any others from other listeners--to the request context. 

Later during request processing the tracing filter looks for the listener context in the request context, and if it finds it creates a "wait" span using the saved wait start and end times. 

The following diagram illustrates the general approach.
![Limit Algorithm Listener processing](LimitAlgorithmOutcomeAndListeners.drawio.svg)


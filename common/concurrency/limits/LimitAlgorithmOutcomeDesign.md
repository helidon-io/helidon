# Limit Algorithm Outcome Processing

Callers of the limit algorithms already receive a `Token` if algorithm accepts the work for processing. 

In some cases, the caller needs to know the decision the algorithm makes regardless of whether it was rejected or accepted.

To obtain the outcome, callers can optionally pass a `Consumer<LimitOutcome>` to the limit algorithm `tryAcquire` or `invoke` methods. The algorithm invokes the consumer, passing the outcome, whether or not the algorithm returns a `Token`. 

For example, to create a tracing span representing the time that an incoming request spent waiting due to concurrency limits, the webserver saves the outcome created by the algorithm and saves it in the request it creates.  

When the request context is created on first access, that same init code now adds the `LimitOutcome` to the request context.

As a result, later during request processing the tracing filter looks for the `LimitOutcome` in the request context, and if it finds it creates a "wait" span using the saved wait start and end times, properly parented using the headers on the request. 

The following diagram illustrates the general approach.
![limit outcome processing](LimitAlgorithmOutcome.drawio.svg)


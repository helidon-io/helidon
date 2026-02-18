Concurrency Limits
-----

This module provides concurrency limits, so we can limit the number of concurrent, in-progress operations (for example in WebServer).

The implemented concurrency limits are:

| Key          | Weight | Description                                                  |
|--------------|--------|--------------------------------------------------------------|
| `fixed`      | `90`   | Semaphore based concurrency limit, supports queueing         |
| `aimd`       | `80`   | AIMD based limit (additive-increase/multiplicative-decrease) |
| `throughput` | `85`   | Throughput based limit, supports queueing                    |

Current usage: `helidon-webserver`

The weight is not significant (unless you want to override an implementation using your own Limit with a higher weight), as the usages in Helidon use a single (optional) implementation that must be correctly typed in 
configuration.

# Fixed concurrency limit

The fixed concurrency limit is based on a semaphore behavior. 
You can define the number of available permits, then each time a token is requested, a permit (if available) is returned.
When the token is finished (through one of its lifecycle operations), the permit is returned.

When the limit is set to 0, an unlimited implementation is used.

The fixed limit also provides support for defining a queue. If set to a value above `0`, queuing is enabled. In such a case we enqueue a certain number of requests (with a configurable timeout). 

Defaults are:
- `permits: 0` - unlimited permits (no limit)
- `queue-length: 0` - no queuing
- `queue-timeout: PT1S` - 1 second timeout in queue, if queuing is enabled

# AIMD concurrency limit

The additive-increase/multiplicative-decrease (AIMD) algorithm is a feedback control algorithm best known for its use in TCP congestion control. AIMD combines linear growth of the congestion window when there is no congestion with an exponential reduction when congestion is detected.

This implementation provides variable concurrency limit with fixed minimal/maximal number of permits.

# Throughput limit

The throughput limit throttles requests to a configured amount of requests over a duration. 
This limit is also based on a semaphore behavior with requests requiring an available token represented by a permit; 
however, permits are never returned but only generated as time passes based on the configured rate limiting algorithm.

When the amount is set to 0, an unlimited implementation is used.

The throughput limit also provides support for defining a queue. If set to a value above `0`, queuing is enabled. In such a case we enqueue a certain number of requests (with a configurable timeout).

Defaults are:
- `amount: 0` - unlimited throughput (no limit)
- `duration: PT1S` - 1 second duration over which to calculate throughput
- `rate-limiting-algorithm: TOKEN_BUCKET` - tokens (permits) refill to a maximum value of the amount over the duration
- `queue-length: 0` - no queuing
- `queue-timeout: PT1S` - 1 second timeout in queue, if queuing is enabled

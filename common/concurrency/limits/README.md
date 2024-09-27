Concurrency Limits
-----

This module provides concurrency limits, so we can limit the number of concurrent, in-progress operations (for example in WebServer).

The implemented concurrency limits are:

| Key         | Weight | Description                                                                                                          |
|-------------|--------|----------------------------------------------------------------------------------------------------------------------|
| `semaphore` | `90`   | Semaphore based concurrency limit (highest weight, so the default), if max set to `0`, we have unlimited concurrency |
| `aimd`      | `80`   | AIMD based limit (additive-increase/multiplicative-decrease)                                                         |
| `bulkhead`  | `70`   | Uses a bulkhead with a queue, implementation provided by `helidon-fault-tolerance`                                   |

Current usage: `helidon-webserver`
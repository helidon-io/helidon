HTTP Exception Handling
---

# WebServer

The request may fail in three locations:
1. In Helidon server processing code (internal, unexpected error caused by a bug). Such a case will be logged into the server log in 
    Severe/Error log level and the connection will be terminated.
2. In request processing code, before ServerRequest and ServerResponse are created. Such errors (such as when request cannot be parsed)
    are handled by `DirectHandler`, and the server provides as much information as possible to the handler.
3. Exceptions thrown from user routing code, and most server exceptions thrown after `ServerRequest` and `ServerResponse` are created, are handled using error handlers of routing. The exception is `BadRequestException` which is always handled by `DirectHandler` (we may discover lazily that a header does not adhere to specification and thrown this exception)

The exception class hierarchy that is used (these exceptions should not be caught by user code, unless you want to handle everything on your own):
- `RequestException` - used for case 2 - this is the only exception (final class) used when we need to explicitly throw an exception
    to be handled by Helidon server code in a `DirectHandler`. 
- `HttpException` - used for case 3 - this is the top level exception class for exceptions that provide a status code and message, and
    that can be handled by error handlers. If Error handles do not handle them, they will create a response with the expected error
    code, and will send the exception message as entity
- `CloseConnectionException` - this exception can be thrown to terminate current connection
- `UncheckedIOException` - this exception is NOT handled and will cause the connection to be closed (we expect this to be problems such as socket closed etc.)


# WebClient

! we need to discuss this first!
    Web client will use `HttpException` and its subclasses (such as `NotFoundException`) in case the entity is read directly
(e.g. the user skips `WebClientResponse`, and uses `as(Class)` to read entity) and the status code returned does not provide
an expected entity (such as any error)

# HttpException reference
The following HTTP status codes have a specific exception class:

- 403 - `ForbiddenException`
- 404 - `NotFoundException`
- 400 - `BadRequestException` - ALWAYS handled by `DirectHandler`
- 500 - `InternalServerException` - the error handler will be first attempted for the cause, then for this exception
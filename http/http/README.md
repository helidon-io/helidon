HTTP
----

# HTTP Header name and value

Abstraction of a single header name (`Connection`), or a header value (`Connection: keep-alive`).


## Types

- `Http.HeaderName` - abstraction of a name of a header (such as `Content-Length`)
- `Http.HeaderNames` - constants with "known" header names (such as `HeaderNames.CONTENT_LENGTH`)
- `Http.Header` - abstraction of a header with a value (such as `Content-Length: 0`)
- `Http.Headers` - constants with commonly used headers with values (such as `Headers.CONTENT_LENGTH_ZERO`)

Internal types:
- `Http.HeaderNameEnum` - "known" headers, optimized for performance in header containers
- `Http.HeaderNameImpl` - custom headers 

## Factory methods

Factory methods to create header names and values are located on their relevant type that contains constants (aligned
   with how we treat `MediaTypes`:

Header names:
1. `Http.HeaderNames.create(String)` - create a header name from the provided name (uses known header if possible)
2. `Http.HeaderNames.create(String, String)` - create a header name (optimized) with lower case and name
3. `Http.HeaderNames.createFromLowercase(String)` - create a header name when we have a guaranteed lowercase name (such as in HTTP/2)

Headers (name and value):
1. `Http.Headers.create(....)` - create a header with the provided name and value
2. `Http.Headers.createCached(...)` - create a header that caches its HTTP/1.1 bytes (optimization for heavily used headers)

Methods with `changing` and `sensitive` - these options are used for HTTP/2 (and HTTP/3) to correctly cache names/values when talking over the network

# HTTP Header containers

Abstraction of a collection of headers, as used in server and client requests and responses.
The containers are optimized for read and write speed by using an array for "known" headers (Headers that are part of our 
`HeaderNameEnum`). Other headers are stored in a map, keyed by `HeaderName`.

## Types

- `Headers` - a collection of HTTP headers that is read-only
- `WritableHeader` - a collection of HTTP headers that is mutable
- `ClientRequestHeaders` - writable headers to create client request
- `ClientResponseHeaders` - read-only headers with response from the server
- `ServerRequestHeaders` - read-only headers with request from client
- `ServerResponseHeaders` - writable header to create server response 

HTTP
----

Proposed changes:

HTTP Header:
1. Rename `Http.Header` to `Http.HeaderNames`
2. Rename all methods that create header names to `create` (no `createName` method)
3. Move methods that create values to `Http.HeaderValues`
4. Rename all methods that create header values to `create` (no `createValue` method)
5. Consider adding a builder for values (to support `changing`, `sensitive` etc.)
6. Create all variants for creating values for (with `String`, `String...`, `int`, `long`, `Iterable<String>`)
   1. `HeaderValues.createCached(String name, ...)`
   2. `HeaderValues.createCached(HeaderName name, ...)`
   3. `HeaderValues.create(String name, ...)`
   4. `HeaderValues.create(HeaderName name, ...)`
   5. `HeaderValues.create(HeaderName name, boolean, boolean, ...)`
7. Rename `HeaderValue` to `HttpHeader` (or `Header`, but that would be confusing for backward compatibility)

HTTP Headers:
1. `WritableHeaders` - there is a factory method to create `WritableHeaders` from another instance, nevertheless we sometimes need to add/set all headers from another instance on an existing instance. We should add `from(Headers)` or `update(Headers)` or `set(HttpHeaders)
2. Consider which "shortcut" methods are needed on `WritableHeaders` for set/add (`set(HeaderValue)`, `set(HeaderName, String value)`...)
3. Consider which "shortcut" methods are needed on `ClientRequest` and `ServerResponse` (if any) - either remove existing and require update through header consumer, or add consistent

Media types:
1. Remove methods on headers that accept `HttpMediaType`, unless they behave differently (they should, as `HttpMediaType` can add parameters)
2. Constants on `HttpMediaTypes` should only contain constants with additional parameters and be named accordingly, never duplicate `MediaTypes` constants (to make the distinction clear - `HttpMediaType` is a `MediaType` with parameters)

# HTTP Headers

The abstraction for Headers used in Helidon has the following components:

A single header:
- `Http.Header` - contains all known HTTP `HeaderName`s as constants
- `Http.HeaderName` - interface representing a name of a header (access to its `lowerCase()` for HTTP/2, `index()` for performance optimization)
- `Http.HeaderValue` - interface representing a full header object (name and value(s)), also provides additional metadata (`changing()`, `sensitive()`) for handling in HTTP/2
- `Http.HeaderValues` - contains a few known `HeaderValue`s as constants

The following factory methods exist for `HeaderName`
- `Header.create(String name)` - create a header name
- `Header.createFromLowercase(String name)` - create a header name (no validation whether really lower case, for performance reasons)
- `Header.createName(String lowerCase, String defaultCase)` - create a header name (no validation whether really lower case, for performance reasons)

The following variants should exist for `HeaderValue` factory method:
- `(HeaderName name, String value)` - always used in Helidon itself, recommended for use
- `(HeaderName name, String... values)`
- `(HeaderName name, int value)`
- `(HeaderName name, long value)`
- `(HeaderName name, Iterable<String> values)`
- `(HeaderName name, boolean changing, boolean sensitive, String value)` - for changing or sensitive headers (HTTP/2 and HTTP/3 utilize these options)
- same variants as without the booleans
- `(String name, String value)` - for cases where we copy headers from somewhere else
- same variants as with `HeaderName`
- `Header.createCached()` - for headers that are re-used often in HTTP/1, this caches the actual bytes to be used (only for single value headers)

Implementation detail for `Http.HeaderName`:
`HeaderName` is a sealed interface, permitting only `HeaderEnum` (Helidon known header names), and `HeaderImpl` (custom header names)
    this is used by our implementation to optimize storage of headers (known headers are stored in an array, custom in a map)

HTTP Headers:
- `Headers` - interface representing a set of HTTP headers received or sent over the network
- `WritableHeaders` - allows modification of headers
- `ClientRequestHeaders` - (writable) client request headers (what we send to the server)
- `ClientResponseHeaders` - (read-only) client response headers (what we got back from the server)
- `ServerRequestHeaders` - (read-only) server request headers (what we got from the client)
- `ServerResponseHeaders` - (writable) server response headers (what we send to the client)
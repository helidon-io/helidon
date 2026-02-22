CORS
----

IMPORTANT:
The below description is the target solution, after we remove all deprecated types and fix (uncomment) code and tests for it.
Currently, we cannot have the "fallback" CORS configuration that prohibits everything, as we would never reach the currently allowed
custom CORS handling done in routing. 
The steps are exactly as they happen in the code, only the "Do not allow any origins for any other requests" part of default configuration is currently 
not there


CORS: Cross-Origin Resource Sharing

CORS is a protocol designed for browsers, to create a trusted client. The browser ensures that if there is an invocation of an endpoint from a different domain than the script invoking it came from, the request is allowed by the endpoint for that domain.

# CORS configuration

In Helidon, CORS is configured either through config, or by setting up a `CorsFeature` (a `ServerFeature`),
or by creating a `ServiceRegistry` service(s) that provides `CorsPathConfig` instance.

Configuration is treated as a sequence of protected paths. The first path configuration that matches the provided path pattern and allowed method will be used to process the CORS requests, and all other path configurations will be ignored.

Each `CorsPathConfig` can configure (see `io.helidon.webserver.cors.CorsPathConfigBlueprint`):

- `path-pattern`: web server path pattern to handle with this config
- `enabled`: whether this path should be used
- `allow-origins`: a set of origins, or origin regular expressions (if it contains `\`, `*`, or `{` we consider this to be a regular expression)
- `allow-headers`: a set of header names (case-insensitive) - which headers will be sent from the script with a different origin 
- `allow-methods`: a set of method names (case-sensitive) - which methods are allowed for this path (only if both path-pattern and method match will this be used)
- `expose-headers`: a set of header names (case-insensitive) - which headers may be exposed from the response to the script with a different origin
- `allow-credentials`: boolean - whether to add credentials (such as cookies) to requests from a script from a different origin
- `max-age`: duration - how long is this response valid


## Defaults

By default, Helidon will add the following configuration (on path pattern `/*` - i.e. matches all requests):

- Allow any `GET`, `HEAD`, and `POST` request with any origin, any headers, with no exposed headers, and allow credentials set to `false`
- Do not allow any origins for any other requests (IMPORTANT: this is future behavior, cannot be enabled until we remove deprecated types and behavior)

The first default (allow `GET`, `HEAD`, and `POST`) can be disabled.

## Additional configuration

Consider the following config:

```yaml
cors:
  add-defaults: false
  paths:
    path-pattern: "/metrics/*" # all paths under /metric including /metric
    allow-origins: ["https://my.server"]
```

This will create a single allowed path (anything under `/metric`) for the origin `https://my.server`, and 
any other CORS request will be denied (as we disable defaults).


Cors can also be disabled:
```yaml
cors:
  enabled: false
```

# CORS enforcement

Cors can be either a "Pre-Flight" check (`OPTIONS` method invoked by the browser when it needs to check a CORS request, defined by the fetch spec), or a "Flight" check (the target HTTP method with `Origin` header),
or it can be a non-cors request (no `Origin` header, or the request is same origin).

Decision whether to invoke "Pre-Flight" or "Flight" check:

1. Check if this is `OPTIONS` method, if not -> may be a "Flight" check, if options, may be both
2. Check if this is a `CORS` request (must contain `Origin` header, and origin must differ from host), if not -> non CORS
3. For `OPTIONS` methods: check if this is preflight (must contain `Access-Control-Request-Method` header), if not -> "Flight" check
4. "Pre-Flight" check

# Non-Cors requests
If the request is not a CORS request, we ignore it and do not send any headers back.

# "Pre Flight" Check
Pre-flight check is done in `CorsHttpFilter`, and finalized in the `CorsFeature.CorsOptionsHttpFeature` where we ensure that a `200` is returned for any options method, even if such a routing does not exist in the application.

Pre flight validation sequence:

1. Find first validator that matches the path, if not found, allow (we will not configure any headers, so if no route catches this, browser will not allow the request)
2. If method is not in `Allow-Methods`, return `403`
3. If origin is not in `Allow-Origins`, return `403`
4. If requested header (`Access-Control-Request-Headers`) is not in `Allow-Headers`, return `403`

The pre-flight response setup sequence:

1. If we allow credentials, send `Access-Control-Allow-Credentials: true` and (fetch spec, section 3.2.5):
   - send `Access-Control-Allow-Origin` with the requested origin
   - send `Access-Control-Allow-Methods` with the requested method
   - send `Vary: Origin`
2. If we do not allow credentials: 
   - if we allow all origins, send `Access-Control-Allow-Origin: *`, otherwise send the requested origin
   - if we do not allow all origins, send `Vary: Origin`
   - if we allow all methods, send `Access-Control-Allow-Methods: *`, otherwise send the allowed methods
3. If there are `Access-Control-Request-Headers` defined, send `Access-Control-Allow-Headers` with the same set that was requested (not the `Allow-Header` configured set)
4. If configured max age is higher than 0 seconds, send `Access-Control-Max-Age` with the configured value

# "Flight" Check
Flight check is done in `CorsHttpFilter` only.

The flight validation sequence:

1. Find first validator that matches the path
2. If method is not in `Allow-Methods`, return `403`
3. If origin is not in `Allow-Origins`, return `403`

The flight response setup sequence:

1. If we allow credentials
   - send `Access-Control-Allow-Credentials: true` header
   - send `Access-Control-Allow-Origin` header configured to the origin from request
   - send `Vary: Origin` header
   - if `Expose-Headers` is configured and not to `*`, send `Access-Control-Expose-Headers` with the configured values
2. If we do not allow credentials:
   - if we allow all origins, send `Access-Control-Allow-Origin: *`, otherwise send the requested origin
   - if we do nota allow all origins, send `Vary: Origin`
   - if we expose all headers, send `Access-Control-Expose-Headers: *`, otherwise send the set of exposed headers (this is treated as literal `*` when allow credentials is configured)

# 2.x Upgrade

In Helidon 2 we have made some changes to APIs and runtime behavior. This guide
will help you migrate a Helidon SE 1.x application to 2.x.

## Java 11 Runtime

Java 8 is no longer supported in Helidon 2. Java 11 or newer is required.

## Tracing

We have upgraded to OpenTracing version 0.33.0 that is not backward compatible.
OpenTracing introduced the following breaking changes:

<table>
<thead>
<th>Removed</th>
<th>Replacement</th>
</thead>
<tr>
<td><code>Scope<wbr>Manager.<wbr>active()</code></td>
<td><code>Tracer.<wbr>active<wbr>Span()</code></td>
</tr>
<tr>
<td><code>Scope<wbr>Manager.<wbr>activate(<wbr>Span,<wbr>boolean)</code></td>
<td><code>Scope<wbr>Manager.<wbr>activate(<wbr>Span)</code> ; second parameter is now always false</td>
</tr>
<tr>
<td><code>Span<wbr>Builder.<wbr>start<wbr>Active()</code></td>
<td><code>Tracer.<wbr>activate<wbr>Span(<wbr>Span)</code></td>
</tr>
<tr>
<td><code>Text<wbr>Map<wbr>Extract<wbr>Adapter</code></td>
<td rowspan="2" style="vertical-align: middle"><code>Text<wbr>Map<wbr>Adapter</code></td>
</tr>
<tr>
<td><code>Text<wbr>MapInject<wbr>Adapter</code></td>
</tr>
<tr>
<td>Module name changed: <code>opentracing.<wbr>api</code></td>
<td><code>io.<wbr>opentracing.<wbr>api</code> (same for <code>noop</code> and <code>util</code>)</td>
</tr>
</table>

If you use the `TracerBuilder` abstraction in Helidon and have no custom Spans,
there is no change required

## Security: OIDC

When the OIDC provider is configured to use cookie (default configuration) to
carry authentication information, the cookie `Same-Site` is now set to `Lax`
(used to be `Strict`). This is to prevent infinite redirects, as browsers would
refuse to set the cookie on redirected requests (due to this setting). Only in
the case of the frontend host and identity host match, we leave `Strict` as the
default

## Getters

Some methods that act as getters of type `T` have been modified to return
`Optional<T>`. You will need to change your code to handle the `Optional` return
type. For example `ServerRequest.spanContext()` in 1.x had a return type of
`SpanContext`. In 2.x it has a return type of `Optional<SpanContext>`. So if you
had code like:

Helidon 1.x Code:

```java
Span myNewSpan = GlobalTracer.get()
    .buildSpan("my-operation")
    .asChildOf(serverRequest.spanContext())
    .start();
```

you will need to change it to something like:

Helidon 2.x Code:

```java
Tracer.SpanBuilder spanBuilder = serverRequest.tracer()
    .buildSpan("my-operation");
serverRequest.spanContext().ifPresent(spanBuilder::asChildOf);
Span myNewSpan = spanBuilder.start();
```

Note the use of `ifPresent()` on the returned `Optional<SpanContext>`.

## Configuration

1.  File watching is now done through a `ChangeWatcher` - use of
    `PollingStrategies.watch()` needs to be refactored to
    `FileSystemWatcher.create()` and the method to configure it on config source
    builder has changed to `changeWatcher(ChangeWatcher)`.
2.  Methods on `ConfigSources` now return specific builders (they used to return
    `AbstractParsableConfigSource.Builder` with a complex type declaration). If
    you store such a builder in a variable, either change it to the correct
    type, or use `var`
3.  Some APIs were cleaned up to be aligned with the development guidelines of
    Helidon. When using Git config source, or etcd config source, the factory
    methods moved to the config source itself, and the builder now accepts all
    configuration options through methods
4.  The API of config source builders has been cleaned, so now only methods that
    are relevant to a specific config source type can be invoked on such a
    builder. Previously you could configure a polling strategy on a source that
    did not support polling
5.  There is a small change in behavior of Helidon Config vs. MicroProfile
    Config: The MP TCK require that system properties are fully mutable (e.g. as
    soon as the property is changed, it must be used), so MP Config methods work
    in this manner (with a certain performance overhead). Helidon Config treats
    System properties as a mutable config source, with an (optional) time based
    polling strategy. So the change is reflected as well, though not immediately
    (this is only relevant if you use change notifications).
6.  `CompositeConfigSource` has been removed from `Config`. If you need to
    configure `MerginStrategy`, you can do it now on `Config` `Builder`

Example of advanced configuration of config:

```java
Config.builder()
    // system properties with a polling strategy of 10 seconds
    .addSource(ConfigSources.systemProperties()
        .pollingStrategy(PollingStrategies.regular(Duration.ofSeconds(10))))
    // environment variables
    .addSource(ConfigSources.environmentVariables())
    // optional file config source with change watcher
    .addSource(ConfigSources.file(Paths.get("/conf/app.yaml"))
        .optional()
        .changeWatcher(FileSystemWatcher.create()))
    // classpath config source
    .addSource(ConfigSources.classpath("application.yaml"))
    // map config source (also supports polling strategy)
    .addSource(ConfigSources.create(Map.of("key", "value")))
    .build();
```

## Resource Class When Loaded from Config

The configuration approach to `Resource` class was using prefixes which was not
aligned with our approach to configuration. All usages were refactored as
follows:

1.  The `Resource` class expects a config node `resource` that will be used to
    read it
2.  The feature set remains unchanged - we support path, classpath, url, content
    as plain text, and content as base64
3.  Classes using resources are changed as well, such as `KeyConfig` - see
    details below

## Media Support

In Helidon 1.x support for JSON and other media types was configured when
constructing `webserver.Routing` using the `register` method. In Helidon 2 Media
Support has been refactored so that it can be shared between the Helidon
`WebServer` and `WebClient`. You now specify media support as part of the
WebServer build:

```java
WebServer.builder()
    .addMediaSupport(JsonpSupport.create()) //registers reader and writer for Json-P
    .build();
```

This replaces `Routing.builder().register(JsonSupport.create())...`

The new JSON MediaSupport classes are:

- `io.helidon.http.media.jsonp.JsonpSupport` in module
  `io.helidon.http.media:helidon-media-jsonp`
- `io.helidon.http.media.jsonb.JsonbSupport` in module
  `io.helidon.http.media:helidon-media-jsonb`
- `io.helidon.http.media.jackson.JacksonSupport` in module
  `io.helidon.http.media:helidon-media-jackson`

## Reactive

<table>
<thead>
<th>Removed</th>
<th>Replacement</th>
</thead>
<tr>
<td><code>io.<wbr>helidon.<wbr>common.<wbr>reactive.<wbr>Reactive<wbr>Streams<wbr>Adapter</code></td>
<td><code>org.<wbr>reactivestreams.<wbr>Flow<wbr>Adapters</code></td>
</tr>
</table>

## Security: OidcConfig

Configuration has been updated to use the new `Resource` approach:

1.  `oidc-metadata.resource` is the new key for loading `oidc-metadata` from
    local resource
2.  `sign-jwk.resource` is the new key for loading signing JWK resource

## Security: JwtProvider and JwtAuthProvider

Configuration has been updated to use the new `Resource` approach:

1.  `jwk.resource` is the new key for loading JWK for verifying signatures
2.  `jwt.resource` is also used for outbound as key for loading JWK for signing
    tokens

## PKI Key Configuration

The configuration has been updated to have a nicer tree structure:

Example of a public key from keystore:

```yaml
keystore:
   cert.alias: "service_cert"
   resource.path: "/conf/keystore.p12"
   type: "PKCS12"
   passphrase: "password"
```

Example of a private key from keystore:

```yaml
keystore:
  key:
    alias: "myPrivateKey"
    passphrase: "password"
  resource.resource-path: "keystore/keystore.p12"
  passphrase: "password"
```

Example of a pem resource with private key and certificate chain:

```yaml
pem:
  key:
    passphrase: "password"
    resource.resource-path: "keystore/id_rsa.p8"
  cert-chain:
    resource.resource-path: "keystore/public_key_cert.pem"
```

## GrpcTlsDescriptor

Configuration has been updated to use the new `Resource` approach:

1.  `tls-cert.resource` is the new key for certificate
2.  `tls-key.resource` is the new key for private key
3.  `tl-ca-cert` is the new key for certificate

## WebServer Configuration

### SSL/TLS

There is a new class `io.helidon.webserver.WebServerTls` that can be used to
configure TLS for a WebServer socket. Class
`io.helidon.webserver.SSLContextBuilder` has been deprecated and will be
removed.

The class uses a `Builder` pattern:

```java
WebServerTls.builder()
    .privateKey(KeyConfig.keystoreBuilder()
        .keystore(Resource.create("certificate.p12"))
        .keystorePassphrase("helidon"));
```

The builder or built instance can be registered with a socket configuration
builder including the `WebServer.Builder` itself:

```java
WebServer.builder(routing())
    .tls(webServerTls)
    .build();
```

### Additional Sockets

Additional socket configuration has changed both in config and in API.

The configuration now accepts following structure:

```yaml
server:
   port: 8000
   sockets:
     - name: "admin"
       port: 8001
     - name: "static"
       port: 8002
       enabled: false
```

Socket name is now a value of a property, allowing more freedom in naming. The
default socket name is implicit (and set to `@default`).

We have added the `enabled` flag to support disabling sockets through
configuration.

To add socket using a builder, you can use:

```java
WebServer.builder()
    .addSocket(SocketConfigurationBuilder.builder()
       .port(8001)
       .name("admin"));
```

There is also a specialized method to add a socket and routing together, to
remove mapping through a name.

### ServerConfiguration

`io.helidon.webserver.ServerConfiguration.Builder` is no longer used to
configure `WebServer`.

Most methods from this class have been moved to `WebServer.Builder` or
deprecated.

Example of a simple WebServer setup:

```java
WebServer.builder()
    .port(8001)
    .host("localhost")
    .routing(createRouting())
    .build();
```

### Other Deprecations

<table>
<thead>
<th>Symbol</th>
<td>Replacement</td>
</thead>
<tr>
<td><code>io.<wbr>helidon.<wbr>webserver.<wbr>Web<wbr>Server.<wbr>Builder</code></td>
<td>
All methods that accept <code>Server<wbr>Configuration</code> or its builder are deprecated,
use methods on <code>Web<wbr>Server.<wbr>Builder</code>
</td>
</tr>
<tr>
<td><code>io.<wbr>helidon.<wbr>webserver.<wbr>Web<wbr>Server.<wbr>Builder</code></td>
<td>
All methods for socket configuration that accept a name and socket are deprecated,
socket name is now part of socket configuration itself
</td>
</tr>
<tr>
<td><code>io.<wbr>helidon.<wbr>webserver.<wbr>Response<wbr>Headers.<wbr>when<wbr>Send()</code></td>
<td><code>when<wbr>Sent()</code></td>
</tr>
<tr>
<td><code>io.<wbr>helidon.<wbr>webserver.<wbr>Routing.<wbr>create<wbr>Server(<wbr>Server<wbr>Configuration)</code></td>
<td><code>WebServer.builder()</code></td>
</tr>
<tr>
<td><code>io.<wbr>helidon.<wbr>webserver.<wbr>Socket<wbr>Configuration.<wbr>DEFAULT</code></td>
<td>Use a builder to create a named configuration</td>
</tr>
<tr>
<td><code>io.<wbr>helidon.<wbr>webserver.<wbr>Socket<wbr>Configuration.<wbr>Builder.<wbr>ssl(<wbr>SSL<wbr>Context)</code></td>
<td><code>WebServerTls</code></td>
</tr>
<tr>
<td><code>io.<wbr>helidon.<wbr>webserver.<wbr>Socket<wbr>Configuration.<wbr>Builder.<wbr>enabled<wbr>SSl<wbr>Protocols(<wbr>String...)</code></td>
<td><code>Web<wbr>Server<wbr>Tls</code></td>
</tr>
</table>

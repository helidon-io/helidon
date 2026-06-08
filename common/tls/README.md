# TLS

This module is used to get an instance of `SSLContext` and `SSLParameters` from configuration, or built using a builder.

In addition, TLS supports reloading of TLS material, either explicitly "form outside", or internally using a
`TlsManager`.

## 4.x Design

The current design introduces a circular behavior, where we have `Tls`, that owns a `TlsManager`, but to reload existing `Tls`,
we must provide a new instance of `Tls`, with its own, new `TlsManager`.

## 27.x Design

To separate the concerns of each component:

- `TlsConfig` is the _initial_ information needed to construct a `Tls` instance
- `TlsMaterial` is the _changeable_ information needed to update a `Tls` instance
- `Tls` still owns a `TlsManager`
- `TlsManager` knows how to create `SSLContext`, `K509KeyManager`, and `X509TrustManager`, it may also support an explicit method `reload(TlsMaterial)` that may be delegated from a listener method

Intention (target solution):

There is only one `Tls` instance per use (i.e. listener).

There is only one `TlsManager` instance per instance of `Tls`.

The method `reload(TlsMaterial)` on listener(s) will delegate the call to the currently configured `TlsManager` - this may not be supported by the chosen manager (i.e. OCI based manager may refuse or ignore calls, as it has a scheduler to read rotated information).

`TlsManager` is responsible for reading configuration, and obtaining the initial information to setup an SSLContext - this may be from config, or from external sources (i.e. a cloud service, environment).

Example of `TlsManager`s we provide/expect:

- `ExplicitTlsManager` - used when we have an explicit instance of `SSLContext` configure, reload throws an exception
- `ConfiguredTlsManager` - uses configuration as the main source of truth
- `OciCertificatesTlsManager` - uses information that may be rotated by OCI
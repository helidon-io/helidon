HSTS
----

HTTP Strict Transport Security support for Helidon WebServer.

The feature adds the `Strict-Transport-Security` response header on responses whose resolved request URI scheme is `https`.

This is intentionally based on secure-origin detection rather than HTTP version checks:

- direct TLS connections on secure sockets are covered
- trusted proxy / forwarded HTTPS discovery is covered when requested-URI discovery is configured
- future secure transports such as HTTP/3 inherit the same behavior automatically

# Configuration

The feature can be configured through `server.features.hsts`.

Supported options:

- `enabled`
- `max-age`
- `include-sub-domains`
- `preload` - non-standard token used by browser preload lists, not part of RFC 6797 itself
- `sockets`
- `weight`
- `name`

Example:

```yaml
server:
  features:
    hsts:
      max-age: 365d
      include-sub-domains: true
      preload: false
```

# Behavior Notes

- The header is only added when the resolved request URI scheme is `https`.
- When TLS is terminated by a trusted proxy, this relies on requested-URI discovery being correctly configured so Helidon resolves the external scheme as `https`.
- Existing `Strict-Transport-Security` headers set by application code are preserved.
- The feature applies to normal responses, redirects, and framework-generated error responses because the header is added through `beforeSend(...)`.

# io.helidon.common.context.http.ContextRecordConfig

## Description

Configuration of a single propagation record, a mapping of a header name to its context classifier, with optional default value(s), and definition whether it is a single value, or an array.

## Usages

- [`server.features.context.records`](../config/io_helidon_webserver_context_ContextFeature.md#aa10e9-records)

## Configuration options

| Key | Kind | Type | Description |
|----|----|----|----|
| <span id="a908d5-array"></span> `array` | `VALUE` | `Boolean` | Whether to treat the option as an array of strings |
| <span id="a89308-classifier"></span> `classifier` | `VALUE` | `String` | String classifier of the value that will be used with `io.helidon.common.context.Context#get(Object, Class)` |
| <span id="af720e-default-value"></span> `default-value` | `VALUE` | `String` | Default value to send if not configured in context |
| <span id="aaa55f-default-values"></span> `default-values` | `LIST` | `String` | Default values to send if not configured in context |
| <span id="aa9da1-header"></span> `header` | `VALUE` | `i.h.c.c.h.C.RecordCustomMethods` | Name of the header to use when sending the context value over the network |

See the [manifest](../config/manifest.md) for all available types.

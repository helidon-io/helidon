# io.helidon.telemetry.otelconfig.ZipkinExporterConfig

## Description

N/A

.

## Usages

## Configuration options

| Key | Kind | Type | Description |
|----|----|----|----|
| <span id="a1e003-compression"></span> [`compression`](../config/io_helidon_telemetry_otelconfig_CompressionType.md) | `VALUE` | `i.h.t.o.CompressionType` | Compression type |
| <span id="aa7e39-encoder"></span> [`encoder`](../config/zipkin2_codec_SpanBytesEncoder.md) | `VALUE` | `z.c.SpanBytesEncoder` | Encoder type |
| <span id="ad1fa8-endpoint"></span> `endpoint` | `VALUE` | `URI` | Collector endpoint to which this exporter should transmit |
| <span id="a971c1-timeout"></span> `timeout` | `VALUE` | `Duration` | Exporter timeout |

See the [manifest](../config/manifest.md) for all available types.

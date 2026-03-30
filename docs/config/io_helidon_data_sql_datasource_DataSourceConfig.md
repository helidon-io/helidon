# io.helidon.data.sql.datasource.DataSourceConfig

## Description

javax.sql.DataSource

configuration.

## Usages

- [`data.sources.sql`](../config/config_reference.md#ac15c2-data-sources-sql)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="aa9099-name"></span> `name` | `VALUE` | `String` | `@default` | `javax.sql.DataSource` name |
| <span id="ae8f73-provider"></span> [`provider`](../config/io_helidon_data_sql_datasource_ProviderConfig.md) | `VALUE` | `i.h.d.s.d.ProviderConfig` |   | Configuration of the used provider, such as UCP |
| <span id="a75975-provider-discover-services"></span> `provider-discover-services` | `VALUE` | `Boolean` | `true` | Whether to enable automatic service discovery for `provider` |

See the [manifest](../config/manifest.md) for all available types.

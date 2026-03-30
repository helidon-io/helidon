# io.helidon.data.sql.datasource.jdbc.JdbcDataSourceConfig

## Description

JDBC Data source configuration.

## Usages

- [`data.sources.sql.provider.jdbc`](../config/io_helidon_data_sql_datasource_ProviderConfig.md#ad38ee-jdbc)

## Configuration options

| Key | Kind | Type | Description |
|----|----|----|----|
| <span id="a2ea60-auto-commit"></span> `auto-commit` | `VALUE` | `Boolean` | Set the default auto-commit behavior of create connections |
| <span id="ada475-catalog"></span> `catalog` | `VALUE` | `String` | Set the default catalog name to be set on connections |
| <span id="aa3d90-properties"></span> `properties` | `MAP` | `String` | Add properties (name/value pair) that will be used to configure the DataSource/Driver |
| <span id="afd7f4-read-only"></span> `read-only` | `VALUE` | `Boolean` | Whether the connection should be read only |
| <span id="ab7b1f-schema"></span> `schema` | `VALUE` | `String` | Set the default schema name to be set on connections |
| <span id="aa1bc5-transaction-isolation"></span> [`transaction-isolation`](../config/io_helidon_data_sql_datasource_TransactionIsolation.md) | `VALUE` | `i.h.d.s.d.TransactionIsolation` | Set the default transaction isolation level |

See the [manifest](../config/manifest.md) for all available types.

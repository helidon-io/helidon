# io.helidon.data.sql.datasource.ProviderConfig

## Description

This type is a provider contract.

## Usages

- [`data.sources.sql.provider`](io_helidon_data_sql_datasource_DataSourceConfig.md#ae8f73-provider)

## Implementations

| Key | Type | Description |
|----|----|----|
| <span id="a671b7-hikari"></span> [`hikari`](io_helidon_data_sql_datasource_hikari_HikariDataSourceConfig.md) | `i.h.d.s.d.h.HikariDataSourceConfig` | Hikari connection pool specific configuration for `javax.sql.DataSource` |
| <span id="ad38ee-jdbc"></span> [`jdbc`](io_helidon_data_sql_datasource_jdbc_JdbcDataSourceConfig.md) | `i.h.d.s.d.j.JdbcDataSourceConfig` | JDBC Data source configuration |
| <span id="a41404-ucp"></span> [`ucp`](io_helidon_data_sql_datasource_ucp_UcpDataSourceConfig.md) | `i.h.d.s.d.u.UcpDataSourceConfig` | UCP specific configuration for `javax.sql.DataSource` |

See the [manifest](../config/manifest.md) for all available types.

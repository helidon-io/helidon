package io.helidon.data.sql.datasource.jdbc;

import io.helidon.common.config.Config;
import io.helidon.data.sql.datasource.ProviderConfig;
import io.helidon.data.sql.datasource.spi.DataSourceConfigProvider;

/**
 * A {@link java.util.ServiceLoader} provider implementation of
 * {@link io.helidon.data.sql.datasource.spi.DataSourceConfigProvider} for a data source based on direct JDBC connections.
 */
public class JdbcDataSourceConfigProvider implements DataSourceConfigProvider {
    static final String PROVIDER_TYPE = "jdbc";

    /**
     * Required default constructor for {@link java.util.ServiceLoader}.
     */
    public JdbcDataSourceConfigProvider() {
    }

    @Override
    public String configKey() {
        return PROVIDER_TYPE;
    }

    @Override
    public ProviderConfig create(Config config, String name) {
        return JdbcDataSourceConfig.builder()
                .config(config)
                .name(name)
                .build();
    }
}

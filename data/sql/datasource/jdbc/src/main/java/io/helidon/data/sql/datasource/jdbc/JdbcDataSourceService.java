package io.helidon.data.sql.datasource.jdbc;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import javax.sql.DataSource;

import io.helidon.data.sql.common.SqlDriver;
import io.helidon.data.sql.datasource.DataSourceConfig;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;

@Service.Singleton
class JdbcDataSourceService implements Service.ServicesFactory<DataSource> {
    private final Supplier<List<DataSourceConfig>> dsConfigs;

    @Service.Inject
    JdbcDataSourceService(Supplier<List<DataSourceConfig>> dsConfigs) {
        this.dsConfigs = dsConfigs;
    }

    @Override
    public List<Service.QualifiedInstance<DataSource>> services() {
        List<Service.QualifiedInstance<DataSource>> instances = new ArrayList<>();

        for (DataSourceConfig dsConfig : dsConfigs.get()) {
            if (dsConfig.provider() instanceof JdbcDataSourceConfig jdbcConfig) {
                // this is a JDBC Data source
                String name = dsConfig.name();

                instances.add(createNamedDataSource(name, jdbcConfig));
            }
        }

        return instances;
    }

    private Service.QualifiedInstance<DataSource> createNamedDataSource(String name, JdbcDataSourceConfig providerConfig) {
        return Service.QualifiedInstance.create(createDataSource(providerConfig),
                                                Qualifier.createNamed(name));
    }

    private DataSource createDataSource(JdbcDataSourceConfig providerConfig) {
        SqlDriver driver = SqlDriver.create(providerConfig);

        if (driver instanceof DataSource ds) {
            return ds;
        }

        return new JdbcDataSource(providerConfig, driver);
    }
}

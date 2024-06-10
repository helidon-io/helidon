/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.helidon.integrations.datasource.ucp.cdi;

import java.util.Properties;
import java.util.regex.Matcher;

import io.helidon.integrations.cdi.configurable.AbstractConfigurableExtension;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.CreationException;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.enterprise.inject.spi.configurator.BeanConfigurator;
import jakarta.enterprise.util.TypeLiteral;
import jakarta.inject.Named;
import oracle.ucp.UniversalConnectionPool;
import oracle.ucp.UniversalConnectionPoolAdapter;
import oracle.ucp.UniversalConnectionPoolException;
import oracle.ucp.admin.UniversalConnectionPoolManager;
import oracle.ucp.admin.UniversalConnectionPoolManagerImpl;
import oracle.ucp.jdbc.PoolDataSource;

import static io.helidon.integrations.datasource.ucp.cdi.UCPBackedDataSourceExtension.DATASOURCE_NAME_PATTERN;

/**
 * An {@link AbstractConfigurableExtension} that provides injection support for {@link UniversalConnectionPoolManager}
 * and {@linkplain Named named} {@link UniversalConnectionPool} instances.
 *
 * <p>As with all portable extensions, to begin to make use of the features enabled by this class, ensure its containing
 * artifact (normally a jar file) is on the runtime classpath of your CDI-enabled application.</p>
 *
 * <p>To support injection of the {@link UniversalConnectionPoolManager}, use normal CDI injection idioms:</p>
 *
 * {@snippet :
 *   // Inject the UniversalConnectionPoolManager into a private field named ucpManager:
 *   @jakarta.inject.Inject // @link substring="jakarta.inject.Inject" target="jakarta.inject.Inject"
 *   private oracle.ucp.admin.UniversalConnectionPoolManager ucpManager;
 *   }
 *
 * <p>To support injection of a {@link UniversalConnectionPool} named {@code test}, first ensure that enough MicroProfile
 * Config configuration is present to create a valid {@link PoolDataSource}. For example, the following sample system
 * properties are sufficient for a {@link PoolDataSource} named {@code test} to be created:</p>
 *
 * {@snippet lang="properties" :
 *   # @link substring="oracle.ucp.jdbc.PoolDataSource" target="PoolDataSource" @link substring="oracle.jdbc.pool.OracleDataSource" target="oracle.jdbc.pool.OracleDataSource" :
 *   oracle.ucp.jdbc.PoolDataSource.test.connectionFactoryClassName=oracle.jdbc.pool.OracleDataSource
 *   oracle.ucp.jdbc.PoolDataSource.test.URL=jdbc:oracle:thin://@localhost:1521/XE
 *   oracle.ucp.jdbc.PoolDataSource.test.user=scott
 *   oracle.ucp.jdbc.PoolDataSource.test.password=tiger
 *   }
 *
 * <p>See the {@link UCPBackedDataSourceExtension} documentation for more information about how {@link PoolDataSource}
 * instances are made eligible for injection from configuration such as this.</p>
 *
 * <p>With configuration such as the above, you can now inject the implicit {@link UniversalConnectionPool} it also
 * defines:</p>
 *
 * {@snippet :
 *   // Inject a UniversalConnectionPool whose getName() method returns test into a private field named ucp:
 *   @jakarta.inject.Inject // @link substring="jakarta.inject.Inject" target="jakarta.inject.Inject"
 *   @jakarta.inject.Named("test") // @link substring="jakarta.inject.Named" target="jakarta.inject.Named"
 *   private oracle.ucp.UniversalConnectionPool ucp; // assert "test".equals(ucp.getName());
 *   }
 *
 * <p><strong>Note:</strong> Working directly with a {@link UniversalConnectionPool} is for advanced use cases
 * only. Injecting and working with {@link PoolDataSource} instances is much more common, and {@link PoolDataSource} is
 * the interface recommended by Oracle's documentation for users to interact with. See {@link
 * UCPBackedDataSourceExtension}'s documentation for more details.</p>
 *
 * @see UniversalConnectionPool
 *
 * @see UCPBackedDataSourceExtension
 */
public class UniversalConnectionPoolExtension extends AbstractConfigurableExtension<UniversalConnectionPool> {

    /**
     * Creates a new {@link UniversalConnectionPoolExtension}.
     *
     * @deprecated For use by CDI only.
     */
    @Deprecated // For use by CDI only
    public UniversalConnectionPoolExtension() {
        super();
    }

    @Override
    protected final Matcher matcher(String configPropertyName) {
        return configPropertyName == null ? null : DATASOURCE_NAME_PATTERN.matcher(configPropertyName);
    }

    @Override
    protected final String name(Matcher configPropertyNameMatcher) {
        return configPropertyNameMatcher == null ? null : configPropertyNameMatcher.group(2);
    }

    @Override
    protected final String propertyName(Matcher configPropertyNameMatcher) {
        return configPropertyNameMatcher == null ? null : configPropertyNameMatcher.group(3);
    }

    private void installManager(@Observes AfterBeanDiscovery event) {
        event.addBean()
            .addTransitiveTypeClosure(UniversalConnectionPoolManager.class)
            .scope(ApplicationScoped.class)
            .produceWith(instance -> {
                    UniversalConnectionPoolManager ucpm;
                    try {
                        ucpm = UniversalConnectionPoolManagerImpl.getUniversalConnectionPoolManager();
                    } catch (final UniversalConnectionPoolException e) {
                        throw new CreationException(e.getMessage(), e);
                    }
                    instance.select(new TypeLiteral<Event<UniversalConnectionPoolManager>>() {})
                        .get()
                        .fire(ucpm);
                    return ucpm;
                });
    }

    @Override
    protected void addBean(BeanConfigurator<UniversalConnectionPool> beanConfigurator,
                           Named name,
                           Properties properties) {
        beanConfigurator
            .addQualifier(name)
            .addTransitiveTypeClosure(UniversalConnectionPool.class)
            .scope(ApplicationScoped.class)
            .produceWith(instance -> {
                    PoolDataSource pds = instance.select(PoolDataSource.class, name).get();
                    UniversalConnectionPoolManager ucpm = instance.select(UniversalConnectionPoolManager.class).get();
                    UniversalConnectionPool ucp;
                    try {
                        ucpm.createConnectionPool((UniversalConnectionPoolAdapter) pds);
                        ucp = ucpm.getConnectionPool(pds.getConnectionPoolName());
                    } catch (UniversalConnectionPoolException e) {
                        throw new CreationException(e.getMessage(), e);
                    }
                    instance.select(new TypeLiteral<Event<UniversalConnectionPool>>() {})
                        .get()
                        .fire(ucp);
                    return ucp;
                })
            .disposeWith((ucp, instance) -> {
                    try {
                        ucp.stop();
                        UniversalConnectionPoolManager ucpm = instance.select(UniversalConnectionPoolManager.class).get();
                        ucpm.destroyConnectionPool(ucp.getName());
                    } catch (UniversalConnectionPoolException e) {
                        throw new CreationException(e.getMessage(), e);
                    }
                });
    }

}

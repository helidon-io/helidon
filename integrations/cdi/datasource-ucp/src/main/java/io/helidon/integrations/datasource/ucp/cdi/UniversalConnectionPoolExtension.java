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

/**
 * An {@link AbstractConfigurableExtension} that provides injection support for {@link UniversalConnectionPoolManager}
 * and {@link UniversalConnectionPool} instances.
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
    protected final Matcher getPropertyPatternMatcher(String configPropertyName) {
        return
            configPropertyName == null ? null : UCPBackedDataSourceExtension.DATASOURCE_NAME_PATTERN.matcher(configPropertyName);
    }

    @Override
    protected final String getName(Matcher propertyPatternMatcher) {
        return propertyPatternMatcher == null ? null : propertyPatternMatcher.group(2);
    }

    @Override
    protected final String getPropertyName(Matcher propertyPatternMatcher) {
        return propertyPatternMatcher == null ? null : propertyPatternMatcher.group(3);
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

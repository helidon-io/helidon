/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.integrations.datasource.hikaricp.cdi;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.literal.NamedLiteral;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.enterprise.inject.spi.ProcessInjectionPoint;
import javax.inject.Named;
import javax.sql.DataSource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigSource;

/**
 * An {@link Extension} that arranges for named {@link DataSource}
 * injection points to be satisfied.
 */
public class HikariCPBackedDataSourceExtension implements Extension {

    private final Set<String> dataSourceNames;

    /**
     * Creates a new {@link HikariCPBackedDataSourceExtension}.
     */
    public HikariCPBackedDataSourceExtension() {
        super();
        this.dataSourceNames = new HashSet<>();
    }

    private <T extends DataSource> void processInjectionPoint(@Observes final ProcessInjectionPoint<?, T> event) {
        if (event != null) {
            final InjectionPoint injectionPoint = event.getInjectionPoint();
            if (injectionPoint != null) {
                final Type type = injectionPoint.getType();
                if (type instanceof Class && DataSource.class.isAssignableFrom((Class<?>) type)) {
                    final Set<Annotation> qualifiers = injectionPoint.getQualifiers();
                    for (final Annotation qualifier : qualifiers) {
                        if (qualifier instanceof Named) {
                            final String dataSourceName = ((Named) qualifier).value();
                            if (dataSourceName != null && !dataSourceName.isEmpty()) {
                                this.dataSourceNames.add(dataSourceName);
                            }
                        }
                    }
                }
            }
        }
    }

    private void afterBeanDiscovery(@Observes final AfterBeanDiscovery event) {
        if (event != null) {
            for (final String dataSourceName : this.dataSourceNames) {
                event.<HikariDataSource>addBean()
                    .addQualifier(NamedLiteral.of(dataSourceName)) // ...and Default and Any?
                    .addTransitiveTypeClosure(HikariDataSource.class)
                    .beanClass(HikariDataSource.class)
                    .scope(ApplicationScoped.class)
                    .produceWith(beans -> new HikariDataSource(new HikariConfig(toProperties(dataSourceName,
                                                                                             beans.select(Config.class).get()))))
                    .disposeWith((dataSource, beans) -> dataSource.close());
            }
        }
    }

    private static Properties toProperties(final String dataSourceName, final Config config) {
        Objects.requireNonNull(dataSourceName);
        Objects.requireNonNull(config);
        final Properties returnValue = new Properties();

        // Look up a required one to bootstrap the whole thing if such
        // bootstrapping happens to be necessary.
        config.getValue("javax.sql.DataSource." + dataSourceName + ".dataSourceClassName", String.class);

        // The MicroProfile Config specification does not say whether
        // property names must be cached or must not be cached
        // (https://github.com/eclipse/microprofile-config/issues/370).
        // It is implied in the MicroProfile Google group
        // (https://groups.google.com/d/msg/microprofile/tvjgSR9qL2Q/M2TNUQrOAQAJ),
        // but not in the specification, that ConfigSources can be
        // mutable and dynamic.  Consequently one would expect their
        // property names to come and go.  Because of this we have to
        // make sure to get all property names from all ConfigSources
        // "by hand".
        //
        // (The MicroProfile Config specification also does not say
        // whether a ConfigSource is thread-safe
        // (https://github.com/eclipse/microprofile-config/issues/369),
        // so iteration over its coming-and-going dynamic property
        // names may be problematic, but there's nothing we can do.)
        //
        // As of this writing, the Helidon MicroProfile Config
        // implementation caches all property names up front, which
        // may not be correct, but is also not forbidden.

        final Iterable<? extends String> propertyNames = getPropertyNames(config);
        if (propertyNames != null) {
            final String prefix = "javax.sql.DataSource." + dataSourceName + ".";
            final int prefixLength = prefix.length();
            for (final String propertyName : propertyNames) {
                if (propertyName != null
                    && propertyName.length() > prefixLength
                    && propertyName.startsWith(prefix)) {
                    returnValue.setProperty(propertyName.substring(prefixLength), config.getValue(propertyName, String.class));
                }
            }
        }
        return returnValue;
    }

    private static Set<String> getPropertyNames(final Config config) {
        final Set<String> returnValue;
        if (config == null) {
            returnValue = Collections.emptySet();
        } else {
            final Set<String> propertyNames = getPropertyNames(config.getConfigSources());
            if (propertyNames == null || propertyNames.isEmpty()) {
                returnValue = Collections.emptySet();
            } else {
                returnValue = Collections.unmodifiableSet(propertyNames);
            }
        }
        return returnValue;
    }

    private static Set<String> getPropertyNames(final Iterable<? extends ConfigSource> configSources) {
        final Set<String> returnValue = new HashSet<>();
        if (configSources != null) {
            for (final ConfigSource configSource : configSources) {
                if (configSource != null) {
                    final Set<String> configSourcePropertyNames = configSource.getPropertyNames();
                    if (configSourcePropertyNames != null && !configSourcePropertyNames.isEmpty()) {
                        returnValue.addAll(configSourcePropertyNames);
                    }
                }
            }
        }
        return Collections.unmodifiableSet(returnValue);
    }

}

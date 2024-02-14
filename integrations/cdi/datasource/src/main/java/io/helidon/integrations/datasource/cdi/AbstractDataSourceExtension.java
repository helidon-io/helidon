/*
 * Copyright (c) 2019, 2024 Oracle and/or its affiliates.
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
package io.helidon.integrations.datasource.cdi;

import java.util.Collections;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;

import javax.sql.DataSource;

import io.helidon.integrations.cdi.configurable.AbstractConfigurableExtension;

import jakarta.enterprise.inject.spi.configurator.BeanConfigurator;
import jakarta.inject.Named;

/**
 * An {@link AbstractConfigurableExtension} whose subclasses arrange for {@link DataSource} instances to be added as CDI
 * beans.
 *
 * <h2>Thread Safety</h2>
 *
 * <p>As with all CDI portable extensions, this class' instances are not safe for concurrent use by multiple
 * threads.</p>
 */
public abstract class AbstractDataSourceExtension extends AbstractConfigurableExtension<DataSource> {

    /**
     * Creates a new {@link AbstractDataSourceExtension}.
     */
    protected AbstractDataSourceExtension() {
        super();
    }

    /**
     * Calls the {@link #getConfigPropertyNames()} method and returns its return value.
     *
     * <p>This method never returns {@code null}.</p>
     *
     * <p>This method exists for backwards compatibility only and the {@link #getConfigPropertyNames()} method is
     * preferred.</p>
     *
     * @return the return value of an invocation of the {@link #getConfigPropertyNames()} method
     *
     * @see #getConfigPropertyNames()
     *
     * @deprecated Please use the {@link #getConfigPropertyNames()} method instead.
     */
    @Deprecated
    protected final Set<String> getPropertyNames() {
        return this.getConfigPropertyNames();
    }

    @Override
    protected final Matcher getPropertyPatternMatcher(String configPropertyName) {
        return this.getDataSourcePropertyPatternMatcher(configPropertyName);
    }

    /**
     * Returns a {@link Matcher} for a property name.
     *
     * <p>Implementations of this method must not return {@code null}.</p>
     *
     * <p>Implementations of this method must not invoke the {@link #getPropertyPatternMatcher(String)} method or an
     * infinite loop may result.</p>
     *
     * <p>Given a {@link String} like
     * <code>javax.sql.DataSource.<em>dataSourceName</em>.<em>dataSourcePropertyName</em></code>, any implementation of
     * this method should return a non-{@code null} {@link Matcher} that is capable of being supplied to the {@link
     * #getDataSourceName(Matcher)} and {@link #getDataSourcePropertyName(Matcher)} methods.</p>
     *
     * @param configPropertyName the name of a configuration property that logically contains a <em>data source
     * name</em> and a <em>data source property name</em>; must not be {@code null}
     *
     * @return a non-{@code null} {@link Matcher}
     *
     * @see #getDataSourceName(Matcher)
     *
     * @see #getDataSourcePropertyName(Matcher)
     */
    protected abstract Matcher getDataSourcePropertyPatternMatcher(String configPropertyName);

    @Override
    protected final String getName(Matcher propertyPatternMatcher) {
        return this.getDataSourceName(propertyPatternMatcher);
    }

    /**
     * Given a {@link Matcher} that has been produced by the {@link #getDataSourcePropertyPatternMatcher(String)}
     * method, returns the relevant data source name.
     *
     * <p>Implementations of this method may return {@code null}.</p>
     *
     * <p>Implementations of this method must not invoke the {@link #getName(Matcher)} method or an infinite loop may
     * result.</p>
     *
     * @param dataSourcePropertyPatternMatcher a {@link Matcher} produced by the {@link
     * #getDataSourcePropertyPatternMatcher(String)} method; must not be {@code null}
     *
     * @return a data source name, or {@code null}
     *
     * @see #getDataSourcePropertyPatternMatcher(String)
     */
    protected abstract String getDataSourceName(Matcher dataSourcePropertyPatternMatcher);

    @Override
    protected final String getPropertyName(Matcher propertyPatternMatcher) {
        return this.getDataSourcePropertyName(propertyPatternMatcher);
    }

    /**
     * Given a {@link Matcher} that has been produced by the {@link #getDataSourcePropertyPatternMatcher(String)}
     * method, returns the relevant data source property name.
     *
     * <p>Implementations of this method may return {@code null}.</p>
     *
     * <p>Implementations of this method must not invoke the {@link #getPropertyName(Matcher)} method or an infinite
     * loop may result.</p>
     *
     * @param dataSourcePropertyPatternMatcher a {@link Matcher} produced by the {@link
     * #getDataSourcePropertyPatternMatcher(String)} method; must not be {@code null}
     *
     * @return a data source property name, or {@code null}
     *
     * @see #getDataSourcePropertyPatternMatcher(String)
     */
    protected abstract String getDataSourcePropertyName(Matcher dataSourcePropertyPatternMatcher);

    /**
     * Returns a {@link Set} of data source names known to this {@link AbstractDataSourceExtension} implementation.
     *
     * <p>This method never returns {@code null}.</p>
     *
     * <p>The {@link Set} returned by this method is {@linkplain Collections#unmodifiableSet(Set) unmodifiable}.</p>
     *
     * @return a non-{@code null}, {@linkplain Collections#unmodifiableSet(Set) unmodifiable <code>Set</code>} of known
     * data source names
     *
     * @deprecated This method exists for backwards compatibility only. Please use the {@link #getNames()} method
     * instead.
     */
    @Deprecated // for backwards compatibility only
    protected final Set<String> getDataSourceNames() {
        return this.getNames();
    }

    /**
     * Adds additional synthesized properties to an internal map of data source properties whose contents will be
     * processed eventually by the {@link #addBean(BeanConfigurator, Named, Properties)} method.
     *
     * <p>This method may return {@code null}.</p>
     *
     * @param dataSourceName the name of the data source under which the supplied {@link Properties} will be indexed;
     * may be {@code null}
     *
     * @param properties the {@link Properties} to put; must not be {@code null}
     *
     * @return the prior {@link Properties} indexed under the supplied {@code dataSourceName}, or {@code null}
     *
     * @exception NullPointerException if {@code properties} is {@code null}
     *
     * @deprecated Please use the {@link #putProperties(String, Properties)} method instead.
     */
    @Deprecated // for backwards compatibility only
    protected final Properties putDataSourceProperties(final String dataSourceName, final Properties properties) {
        return this.putProperties(dataSourceName, properties);
    }

    /**
     * Calls the {@link #initializeNamedProperties()} method.
     *
     * @deprecated This method exists for backwards compatibility only. Please use the {@link
     * #initializeNamedProperties()} method instead.
     */
    @Deprecated // for backwards compatibility only
    protected final void initializeMasterProperties() {
        this.initializeNamedProperties();
    }

}

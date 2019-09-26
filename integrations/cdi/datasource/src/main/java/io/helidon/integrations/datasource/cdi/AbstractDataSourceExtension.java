/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;

import javax.annotation.sql.DataSourceDefinition;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.literal.NamedLiteral;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.AfterDeploymentValidation;
import javax.enterprise.inject.spi.Annotated;
import javax.enterprise.inject.spi.DeploymentException;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessAnnotatedType;
import javax.enterprise.inject.spi.WithAnnotations;
import javax.enterprise.inject.spi.configurator.BeanConfigurator;
import javax.inject.Named;
import javax.sql.DataSource;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.spi.ConfigSource;

/**
 * An abstract {@link Extension} whose subclasses arrange for {@link
 * DataSource} instances to be added as CDI beans.
 *
 * <h2>Thread Safety</h2>
 *
 * <p>As with all CDI portable extensions, this class' instances are
 * not safe for concurrent use by multiple threads.</p>
 */
public abstract class AbstractDataSourceExtension implements Extension {

    private final Map<String, Properties> masterProperties;

    private final Map<String, Properties> explicitlySetProperties;

    private final Config config;

    /**
     * Creates a new {@link AbstractDataSourceExtension}.
     */
    protected AbstractDataSourceExtension() {
        super();
        this.masterProperties = new HashMap<>();
        this.explicitlySetProperties = new HashMap<>();
        this.config = ConfigProvider.getConfig();
    }

    /**
     * Returns a {@link Matcher} for a property name.
     *
     * <p>Implementations of this method must not return {@code null}.</p>
     *
     * <p>Given a {@link String} like
     * <code>javax.sql.DataSource.<em>dataSourceName</em>.<em>dataSourcePropertyName</em></code>,
     * any implementation of this method should return a non-{@code
     * null} {@link Matcher} that is capable of being supplied to the
     * {@link #getDataSourceName(Matcher)} and {@link
     * #getDataSourcePropertyName(Matcher)} methods.</p>
     *
     * @param configPropertyName the name of a configuration property
     * that logically contains a <em>data source name</em> and a
     * <em>data source property name</em>; must not be {@code null}
     *
     * @return a non-{@code null} {@link Matcher}
     *
     * @see #getDataSourceName(Matcher)
     *
     * @see #getDataSourcePropertyName(Matcher)
     */
    protected abstract Matcher getDataSourcePropertyPatternMatcher(String configPropertyName);

    /**
     * Given a {@link Matcher} that has been produced by the {@link
     * #getDataSourcePropertyPatternMatcher(String)} method, returns
     * the relevant data source name.
     *
     * <p>Implementations of this method may return {@code null}.</p>
     *
     * @param dataSourcePropertyPatternMatcher a {@link Matcher}
     * produced by the {@link
     * #getDataSourcePropertyPatternMatcher(String)} method; must not
     * be {@code null}
     *
     * @return a data source name, or {@code null}
     *
     * @see #getDataSourcePropertyPatternMatcher(String)
     */
    protected abstract String getDataSourceName(Matcher dataSourcePropertyPatternMatcher);

    /**
     * Given a {@link Matcher} that has been produced by the {@link
     * #getDataSourcePropertyPatternMatcher(String)} method, returns
     * the relevant data source property name.
     *
     * <p>Implementations of this method may return {@code null}.</p>
     *
     * @param dataSourcePropertyPatternMatcher a {@link Matcher}
     * produced by the {@link
     * #getDataSourcePropertyPatternMatcher(String)} method; must not
     * be {@code null}
     *
     * @return a data source property name, or {@code null}
     *
     * @see #getDataSourcePropertyPatternMatcher(String)
     */
    protected abstract String getDataSourcePropertyName(Matcher dataSourcePropertyPatternMatcher);

    /**
     * Called to permit subclasses to add a {@link DataSource}-typed
     * bean using the supplied {@link BeanConfigurator}.
     *
     * <p>Implementations of this method will be called from an
     * observer method that is observing the {@link
     * AfterBeanDiscovery} container lifecycle event.</p>
     *
     * @param beanConfigurator the {@link BeanConfigurator} to use to
     * actually add a new bean; must not be {@code null}
     *
     * @param name a {@link Named} instance qualifying the {@link
     * DataSource}-typed bean to be added; may be {@code null}
     *
     * @param properties a {@link Properties} instance containing
     * properties relevant to the data source; must not be {@code
     * null}
     */
    @Deprecated
    protected void addBean(BeanConfigurator<DataSource> beanConfigurator,
                           Named name,
                           Properties properties) {

    }

    /**
     * Called to permit subclasses to add a {@link DataSource}-typed
     * bean using the supplied {@link BeanConfigurator}.
     *
     * <p>Implementations of this method will be called from an
     * observer method that is observing the {@link
     * AfterBeanDiscovery} container lifecycle event.</p>
     *
     * @param beanConfigurator the {@link BeanConfigurator} to use to
     * actually add a new bean; must not be {@code null}
     *
     * @param name a {@link Named} instance qualifying the {@link
     * DataSource}-typed bean to be added; may be {@code null}
     *
     * @param propertiesFunction a {@link Function} that returns
     * {@link Properties} objects containing properties relevant to
     * the data source and whose sole {@code Boolean} parameter
     * indicates whether supporting configuration may be removed to
     * save memory; must not be {@code null}
     */
    protected void addBean(final BeanConfigurator<DataSource> beanConfigurator,
                           final Named name,
                           final Function<? super Boolean, ? extends Properties> propertiesFunction) {
        this.addBean(beanConfigurator, name, propertiesFunction.apply(false));
    }

    /**
     * Returns the {@link Config} instance used to acquire
     * configuration property values.
     *
     * <p>This method never returns {@code null}.</p>
     *
     * @return a non-{@code null} {@link Config} instance
     *
     * @see Config
     */
    protected final Config getConfig() {
        return this.config;
    }

    /**
     * Returns a {@link Set} of data source names known to this {@link
     * AbstractDataSourceExtension} implementation.
     *
     * <p>This method never returns {@code null}.</p>
     *
     * <p>The {@link Set} returned by this method is {@linkplain
     * Collections#unmodifiableSet(Set) unmodifiable}.</p>
     *
     * @return a non-{@code null}, {@linkplain
     * Collections#unmodifiableSet(Set) unmodifiable <code>Set</code>}
     * of known data source names
     */
    protected final Set<String> getDataSourceNames() {
        final Set<String> returnValue;
        if (this.masterProperties.isEmpty()) {
            if (this.explicitlySetProperties.isEmpty()) {
                returnValue = Collections.emptySet();
            } else {
                returnValue = Collections.unmodifiableSet(this.explicitlySetProperties.keySet());
            }
        } else {
            returnValue = Collections.unmodifiableSet(this.masterProperties.keySet());
        }
        return returnValue;
    }

    /**
     * Adds additional synthesized properties to an internal map of
     * data source properties whose contents will be processed
     * eventually by the {@link #addBean(BeanConfigurator, Named,
     * Properties)} method.
     *
     * <p>Invocations of this method will replace {@link Properties}
     * previously indexed under the supplied {@code dataSourceName},
     * if any.</p>
     *
     * <p>This method may return {@code null}.</p>
     *
     * <p>An invocation of this method after CDI has completed bean
     * discovery will result in undefined behavior.</p>
     *
     * @param dataSourceName the name of the data source under which
     * the supplied {@link Properties} will be indexed; may be {@code
     * null}
     *
     * @param properties the {@link Properties} to put; may be {@code null}
     *
     * @return the prior {@link Properties} indexed under the supplied
     * {@code dataSourceName}, or {@code null}
     */
    protected final Properties putDataSourceProperties(final String dataSourceName, final Properties properties) {
        return this.explicitlySetProperties.put(dataSourceName, properties);
    }

    public final void addDataSourceName(final String dataSourceName) {
        this.explicitlySetProperties.put(dataSourceName, null);
    }

    /**
     * <strong>{@linkplain Map#clear() Clears}</strong> and then
     * builds or rebuilds an internal map of data source properties
     * whose contents will be processed eventually by the {@link
     * #addBean(BeanConfigurator, Named, Properties)} method.
     *
     * <p>If no subclass explicitly calls this method, it will be
     * called by the {@link #addBean(BeanConfigurator, Named,
     * Properties)} method just prior to its other activities.</p>
     *
     * <p>Once the {@link #addBean(BeanConfigurator, Named,
     * Properties)} method has run to completion, while this method
     * may be called freely its use is discouraged and its effects
     * will no longer be used.</p>
     *
     * @see #addBean(BeanConfigurator, Named, Properties)
     */
    protected final void initializeMasterProperties() {
        this.masterProperties.clear();
        final Set<? extends String> allPropertyNames = this.getPropertyNames();
        if (allPropertyNames != null && !allPropertyNames.isEmpty()) {
            for (final String propertyName : allPropertyNames) {
                final Optional<String> propertyValue = this.config.getOptionalValue(propertyName, String.class);
                if (propertyValue != null && propertyValue.isPresent()) {
                    final Matcher matcher = this.getDataSourcePropertyPatternMatcher(propertyName);
                    if (matcher != null && matcher.matches()) {
                        final String dataSourceName = this.getDataSourceName(matcher);
                        Properties properties = this.masterProperties.get(dataSourceName);
                        if (properties == null) {
                            properties = new Properties();
                            this.masterProperties.put(dataSourceName, properties);
                        }
                        final String dataSourcePropertyName = this.getDataSourcePropertyName(matcher);
                        properties.setProperty(dataSourcePropertyName, propertyValue.get());
                    }
                }
            }
        }
        this.masterProperties.putAll(this.explicitlySetProperties);
    }

    /**
     * Returns a {@link Set} of all known configuration property
     * names.
     *
     * <p>This method never returns {@code null}.</p>
     *
     * <p>The {@link Set} returned by this method is {@linkplain
     * Collections#unmodifiableSet(Set) unmodifiable}.</p>
     *
     * <p>The {@link Set} returned by this method is not safe for
     * concurrent use by multiple threads.</p>
     *
     * <p>Any other semantics of the {@link Set} returned by this
     * method are governed by the <a
     * href="https://github.com/eclipse/microprofile-config"
     * target="_parent">MicroProfile Config</a> specification.</p>
     *
     * @return a non-{@code null}, {@linkplain
     * Collections#unmodifiableSet(Set) unmodifiable <code>Set</code>}
     * of all known configuration property names
     *
     * @see Config#getConfigSources()
     *
     * @see ConfigSource#getPropertyNames()
     */
    protected final Set<String> getPropertyNames() {
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
        // Additionally, the Helidon MicroProfile Config
        // implementation may add on some Helidon SE Config sources
        // that are not represented as MicroProfile Config sources.
        // Consequently we have to source property names from both the
        // MicroProfile Config ConfigSources and from the MicroProfile
        // Config object itself.  We do this by first iterating over
        // the MicroProfile Config object's ConfigSources and then
        // augmenting where necessary with any other (cached) property
        // names reported by the MicroProfile Config implementation.
        // This may be a violation of the MicroProfile Config
        // specification, but as there is no test for it and the
        // specification is unclear, we have to be prepared to handle
        // it.  See also:
        // https://github.com/eclipse/microprofile-config/issues/431#issuecomment-519858099
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
        final Set<String> returnValue;

        // Start by getting all the property names directly from our
        // MicroProfile Config ConfigSources.  They take precedence.
        Set<String> propertyNames = getPropertyNames(this.config.getConfigSources());
        assert propertyNames != null;

        // Add any property names that the Config itself might report
        // that aren't reflected, for whatever reason, in the
        // ConfigSources' property names.
        final Iterable<String> configPropertyNames = this.config.getPropertyNames();
        if (configPropertyNames != null) {
            for (final String configPropertyName : configPropertyNames) {
                propertyNames.add(configPropertyName);
            }
        }

        if (propertyNames.isEmpty()) {
            returnValue = Collections.emptySet();
        } else {
            returnValue = Collections.unmodifiableSet(propertyNames);
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
        return returnValue;
    }

    private void processAnnotatedType(@Observes
                                      @WithAnnotations(DataSourceDefinition.class)
                                      final ProcessAnnotatedType<?> event) {
        if (event != null) {
            final Annotated annotated = event.getAnnotatedType();
            if (annotated != null) {
                final Set<? extends DataSourceDefinition> dataSourceDefinitions =
                    annotated.getAnnotations(DataSourceDefinition.class);
                if (dataSourceDefinitions != null && !dataSourceDefinitions.isEmpty()) {
                    for (final DataSourceDefinition dsd : dataSourceDefinitions) {
                        assert dsd != null;
                        final Set<String> knownDataSourceNames = this.getDataSourceNames();
                        assert knownDataSourceNames != null;
                        final String dataSourceName = dsd.name();
                        if (!knownDataSourceNames.contains(dataSourceName)) {
                            this.putDataSourceProperties(dataSourceName, toProperties(dsd));
                        }
                    }
                }
            }
        }
    }

    private void afterBeanDiscovery(@Observes final AfterBeanDiscovery event) {
        if (event != null) {
            final Config config = this.getConfig();
            assert config != null;
            if (this.masterProperties.isEmpty()) {
                final boolean lateConfigurationBinding = config.getOptionalValue("jpa.lateConfigurationBinding", Boolean.class)
                    .orElse(false);
                if (!lateConfigurationBinding) {
                    this.initializeMasterProperties();
                }
            }
            final Set<? extends String> dataSourceNames = this.masterProperties.keySet();
            if (dataSourceNames != null && !dataSourceNames.isEmpty()) {
                for (final String dataSourceName : dataSourceNames) {
                    if (dataSourceName != null) {
                        this.addBean(event.addBean(),
                                     NamedLiteral.of(dataSourceName),
                                     remove -> remove
                                     ? this.masterProperties.remove(dataSourceName)
                                     : this.masterProperties.get(dataSourceName));
                    }
                }
            }
        }
    }

    private void afterDeploymentValidation(@Observes final AfterDeploymentValidation event) {
        Set<? extends String> dataSourceNames = this.getDataSourceNames();
        if (!dataSourceNames.isEmpty()) {
            if (this.masterProperties.isEmpty()) {
                // Master properties were never initialized, but we
                // had explicit data source names, so we're in a
                // delayed binding situation.
                this.initializeMasterProperties();
                dataSourceNames = this.getDataSourceNames();
                // There have to be some data source names, because
                // getDataSourceNames() will always return the
                // explicitly set ones, which is how we got into this
                // block in the first place.
                assert !dataSourceNames.isEmpty();
            }
            for (final String dataSourceName : dataSourceNames) {
                this.validateDataSourceProperties(event, dataSourceName, this.masterProperties.get(dataSourceName));
            }
        }
        this.explicitlySetProperties.clear();
    }

    /**
     * Validates the supplied {@link Properties} which are assumed to
     * be properties for a data source identified by the supplied
     * {@code dataSourceName}.
     *
     * @param event the {@link AfterDeploymentValidation} event in
     * effect; must not be {@code null}
     *
     * @param dataSourceName the name of the data source; may be
     * {@code null} in invalid scenarios
     *
     * @param dataSourceProperties the data source properties
     * corresponding to the supplied {@code dataSourceName}; may be
     * {@code null}
     *
     * @exception NullPointerException if {@code event} is {@code null}
     *
     * @see AfterDeploymentValidation#addDeploymentProblem(Throwable)
     */
    protected void validateDataSourceProperties(final AfterDeploymentValidation event,
                                                final String dataSourceName,
                                                final Properties dataSourceProperties) {
        Objects.requireNonNull(event);
        if (dataSourceName == null) {
            event.addDeploymentProblem(new DeploymentException("The datasource name was null"));
        }
        if (dataSourceProperties == null) {
            event.addDeploymentProblem(new DeploymentException("No datasource properties found for datasource \""
                                                               + dataSourceName + "\""));
        }
    }

    /**
     * Returns a {@link Properties} object representing the properties
     * defined by the supplied {@link DataSourceDefinition}.
     *
     * <p>This implementation returns {@code null} in all cases.
     * Overrides are encouraged to return more sensible values.</p>
     *
     * <p>Overrides are permitted to return {@code null}.</p>
     *
     * @param dsd the {@link DataSourceDefinition} in question; may be
     * {@code null}
     *
     * @return a {@link Properties} object representing the
     * properties, or {@code null} if no properties could be inferred
     */
    protected Properties toProperties(final DataSourceDefinition dsd) {
        return null;
    }

}

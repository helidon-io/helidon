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
package io.helidon.integrations.cdi.configurable;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;

import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.literal.NamedLiteral;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.configurator.BeanConfigurator;
import jakarta.inject.Named;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

/**
 * A convenient, abstract {@link Extension} whose subclasses arrange for instances of a particular type to be configured
 * and added as CDI beans.
 *
 * <p>There are four kinds of names defined by this extension:</p>
 *
 * <ol>
 *
 * <li><strong>Type name</strong>: the class name of the kind of object this extension manages. Example: {@code
 * javax.sql.DataSource}</li>
 *
 * <li><strong>Name</strong>: a name designating one of potentially many such objects. Example: {@code prod}</li>
 *
 * <li><strong>Property name</strong>: a name of a property of the kind of object this extension manages. Example:
 * {@code user}</li>
 *
 * <li><strong>Configuration property name</strong>: a name of a configuration property that logically contains at least
 * the other three kinds of names. Example: {@code javax.sql.DataSource.prod.user}</li>
 *
 * </ol>
 *
 * <h2>Thread Safety</h2>
 *
 * <p>As with all CDI portable extensions, this class' instances are not safe for concurrent use by multiple
 * threads.</p>
 *
 * @param <T> the type for which this {@link AbstractConfigurableExtension} implementation installs instances
 */
public abstract class AbstractConfigurableExtension<T> implements Extension {


    /*
     * Instance fields.
     */


    // clear()ed after bean discovery
    private final Map<String, Properties> namedProperties;

    // clear()ed after bean discovery
    private final Map<String, Properties> explicitlySetProperties;

    // Implementation detail.
    private final Config config;


    /*
     * Constructors.
     */


    /**
     * Creates a new {@link AbstractConfigurableExtension}.
     */
    protected AbstractConfigurableExtension() {
        super();
        this.namedProperties = new HashMap<>();
        this.explicitlySetProperties = new HashMap<>();
        this.config = ConfigProvider.getConfig();
    }


    /*
     * Instance methods.
     */


    /**
     * Returns a {@link Matcher} given a <em>configuration property name</em> that can logically identify and provide
     * access to at least its three component names.
     *
     * <p>Implementations of this method must not return {@code null}.</p>
     *
     * <p>Given a {@link String} that is a configuration property name, like
     * <code>com.foo.Bar.<em>name</em>.<em>propertyName</em></code>, any implementation of this method must return a
     * non-{@code null} {@link Matcher} that is capable of being supplied to the {@link #name(Matcher)} and {@link
     * #propertyName(Matcher)} methods.</p>
     *
     * @param configPropertyName a <em>configuration property name</em> that logically contains a <em>type name</em>, a
     * <em>name</em> and a <em>property name</em>; must not be {@code null}
     *
     * @return a non-{@code null} {@link Matcher}
     *
     * @exception NullPointerException if {@code configPropertyName} is {@code null}
     *
     * @see #name(Matcher)
     *
     * @see #propertyName(Matcher)
     */
    protected abstract Matcher matcher(String configPropertyName);

    /**
     * Given a {@link Matcher} that has been produced by the {@link #matcher(String)} method, returns the relevant name.
     *
     * <p>Implementations of this method may return {@code null}.</p>
     *
     * @param configPropertyNameMatcher a {@link Matcher} produced by the {@link #matcher(String)} method; must not be
     * {@code null}
     *
     * @return a name, or {@code null}
     *
     * @exception NullPointerException if {@code configPropertyNameMatcher} is {@code null}
     *
     * @see #matcher(String)
     */
    protected abstract String name(Matcher configPropertyNameMatcher);

    /**
     * Given a {@link Matcher} that has been produced by the {@link #matcher(String)} method, returns the relevant
     * <em>property name</em>, or {@code null} if there is no such property name.
     *
     * <p>Most implementations of this method will use the {@link Matcher#group(int)} method to produce the required
     * property name.</p>
     *
     * <p>Implementations of this method may return {@code null}.</p>
     *
     * @param configPropertyNameMatcher a {@link Matcher} produced by the {@link #matcher(String)} method; must not be
     * {@code null}
     *
     * @return a property name, or {@code null}
     *
     * @exception NullPointerException if {@code configPropertyNameMatcher} is {@code null}
     *
     * @see #matcher(String)
     */
    protected abstract String propertyName(Matcher configPropertyNameMatcher);

    /**
     * Called internally to permit subclasses to add a {@code T}-typed bean, qualified with at least the supplied {@link
     * Named}, using the supplied {@link BeanConfigurator}.
     *
     * <p>Implementations of this method will be called from an observer method that is observing the {@link
     * AfterBeanDiscovery} container lifecycle event.</p>
     *
     * @param beanConfigurator the {@link BeanConfigurator} to use to actually add a new bean; must not be {@code null}
     *
     * @param name a {@link Named} instance qualifying the {@code T}-typed bean to be added; may be {@code
     * null}
     *
     * @param properties a {@link Properties} instance containing properties relevant to the object; must not be {@code
     * null}
     *
     * @exception NullPointerException if {@code beanConfigurator} or {@code properties} is {@code null}
     *
     * @see AfterBeanDiscovery
     */
    protected abstract void addBean(BeanConfigurator<T> beanConfigurator, Named name, Properties properties);

    /**
     * Given a <em>configuration property name</em>, returns an {@link Optional Optional&lt;String&gt;} containing its
     * value, or an {@linkplain Optional#empty() empty <code>Optional</code>} if no such configuration property value
     * exists.
     *
     * <p>This method never returns {@code null}.</p>
     *
     * <p>Overrides of this method must not return {@code null}.</p>
     *
     * @param configPropertyName a configuration property name; must not be {@code null}
     *
     * @return a non-{@code null} {@link Optional}
     *
     * @exception NullPointerException if {@code configPropertyName} is {@code null}
     */
    protected Optional<String> configPropertyValue(String configPropertyName) {
        return this.config.getOptionalValue(configPropertyName, String.class);
    }

    /**
     * Returns a {@link Set} of <em>names</em> known to this {@link AbstractConfigurableExtension} implementation.
     *
     * <p>This method never returns {@code null}.</p>
     *
     * <p>The {@link Set} returned by this method is {@linkplain Collections#unmodifiableSet(Set) unmodifiable}.</p>
     *
     * @return a non-{@code null}, {@linkplain Collections#unmodifiableSet(Set) unmodifiable <code>Set</code>} of known
     * names
     */
    protected final Set<String> names() {
        if (this.namedProperties.isEmpty()) {
            if (this.explicitlySetProperties.isEmpty()) {
                return Set.of();
            }
            return Collections.unmodifiableSet(this.explicitlySetProperties.keySet());
        }
        return Collections.unmodifiableSet(this.namedProperties.keySet());
    }

    /**
     * Adds additional synthesized properties (<em>property names</em> and their values) to an internal map of such
     * properties whose contents will be processed eventually by the {@link #addBean(BeanConfigurator, Named,
     * Properties)} method.
     *
     * <p>This method may return {@code null}.</p>
     *
     * @param name the <em>name</em> of the object under which the supplied {@link Properties} will be indexed; may be
     * {@code null}
     *
     * @param properties the {@link Properties} to put consisting of property names and their values; must not be {@code
     * null}
     *
     * @return the prior {@link Properties} indexed under the supplied {@code name}, or {@code null}
     *
     * @exception NullPointerException if {@code properties} is {@code null}
     */
    protected final Properties put(String name, Properties properties) {
        return this.explicitlySetProperties.put(name, Objects.requireNonNull(properties, "properties"));
    }

    /**
     * <strong>{@linkplain Map#clear() Clears}</strong>, and then builds or rebuilds, an internal set of properties
     * whose contents will be processed eventually by the {@link #addBean(BeanConfigurator, Named, Properties)} method.
     *
     * <p>If no subclass explicitly calls this method, as is common, it will be called by the {@link
     * #addBean(BeanConfigurator, Named, Properties)} method just prior to its other activities.</p>
     *
     * <p>Once the {@link #addBean(BeanConfigurator, Named, Properties)} method has run to completion, while this method
     * may be called freely, its use is discouraged and its effects will no longer be used.</p>
     *
     * @see #addBean(BeanConfigurator, Named, Properties)
     */
    protected final void initializeNamedProperties() {
        this.namedProperties.clear();
        Set<? extends String> allConfigPropertyNames = this.configPropertyNames();
        if (!allConfigPropertyNames.isEmpty()) {
            for (String configPropertyName : allConfigPropertyNames) {
                Optional<String> propertyValue = this.configPropertyValue(configPropertyName);
                if (propertyValue.isPresent()) {
                    Matcher matcher = this.matcher(configPropertyName);
                    if (matcher != null && matcher.matches()) {
                        this.namedProperties.computeIfAbsent(this.name(matcher), n -> new Properties())
                            .setProperty(this.propertyName(matcher), propertyValue.get());
                    }
                }
            }
        }
        this.namedProperties.putAll(this.explicitlySetProperties);
    }

    /**
     * Returns a {@link Set} of all known <em>configuration property names</em>.
     *
     * <p>This method never returns {@code null}.</p>
     *
     * <p>Overrides of this method must not return {@code null}.</p>
     *
     * <p>The {@link Set} returned by this method is {@linkplain Collections#unmodifiableSet(Set) unmodifiable}.</p>
     *
     * <p>Overrides of this method must ensure that the returned {@link Set} is {@linkplain
     * Collections#unmodifiableSet(Set) unmodifiable}.</p>
     *
     * <p>The {@link Set} returned by this method is not safe for concurrent use by multiple threads.</p>
     *
     * <p>Overrides of this method may return {@link Set} instances that are not safe for concurrent use by multiple
     * threads.</p>
     *
     * <p>The {@link Set} returned by this method and its overrides may be {@linkplain Set#isEmpty() empty}.</p>
     *
     * <p>The ordering of the elements contained by the {@link Set} returned by this method and its overrides is
     * indeterminate.</p>
     *
     * @return a non-{@code null}, {@linkplain Collections#unmodifiableSet(Set) unmodifiable <code>Set</code>} of all
     * known configuration property names
     */
    protected Set<String> configPropertyNames() {
        Iterable<String> propertyNames = this.config.getPropertyNames();
        if (propertyNames == null) {
            return Set.of();
        }
        Set<String> set = new HashSet<>();
        propertyNames.iterator().forEachRemaining(pn -> set.add(pn));
        return Set.copyOf(set);
    }

    private void afterBeanDiscovery(@Observes AfterBeanDiscovery event) {
        if (event != null) {
            if (this.namedProperties.isEmpty()) {
                this.initializeNamedProperties();
            }
            for (Entry<? extends String, ? extends Properties> entry : this.namedProperties.entrySet()) {
                this.addBean(event.addBean(), NamedLiteral.of(entry.getKey()), entry.getValue());
            }
        }
        this.namedProperties.clear();
        this.explicitlySetProperties.clear();
    }

}

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
package io.helidon.service.configuration.ucp;

import java.util.Properties;
import java.util.Set;

/**
 * An abstract {@link
 * io.helidon.service.configuration.api.ServiceConfiguration}
 * implementation that provides configuration information for <a
 * href="https://docs.oracle.com/en/database/oracle/oracle-database/19/jjucp/index.html"
 * target="_parent">Oracle Universal Connection Pool</a> componentry.
 *
 * @see #UCPServiceConfiguration(Properties,
 * io.helidon.service.configuration.api.System, Properties)
 *
 * @see UCPServiceConfigurationProvider
 *
 * @deprecated This class is slated for removal.
 */
@Deprecated
public class UCPServiceConfiguration extends io.helidon.service.configuration.api.ServiceConfiguration {


    /*
     * Instance fields.
     */


    /**
     * A {@link Properties} instance supplied {@linkplain
     * #UCPServiceConfiguration(Properties,
     * io.helidon.service.configuration.api.System, Properties) at
     * construction time} containing the property values that will
     * ultimately be returned by the default implementation of the
     * {@link #getPropertyNames()} and {@link #getProperty(String,
     * String)} methods.
     *
     * <p>This field is never {@code null}.</p>
     *
     * @see #UCPServiceConfiguration(Properties,
     * io.helidon.service.configuration.api.System, Properties)
     */
    @SuppressWarnings("checkstyle:VisibilityModifier")
    protected final Properties properties;

    /**
     * The {@link io.helidon.service.configuration.api.System} that
     * was determined to be the authoritative {@link
     * io.helidon.service.configuration.api.System} at the time this
     * {@link UCPServiceConfiguration} was {@linkplain
     * #UCPServiceConfiguration(Properties,
     * io.helidon.service.configuration.api.System, Properties)
     * constructed}.
     *
     * <p>This field may be {@code null}.</p>
     *
     * @see #UCPServiceConfiguration(Properties,
     * io.helidon.service.configuration.api.System, Properties)
     *
     * @see
     * io.helidon.service.configuration.api.ServiceConfigurationProvider#buildFor(Set,
     * Properties)
     */
    @SuppressWarnings("checkstyle:VisibilityModifier")
    protected final io.helidon.service.configuration.api.System system;

    /**
     * A {@link Properties} instance representing the meta-properties
     * in effect at the time this {@link UCPServiceConfiguration} was
     * {@linkplain #UCPServiceConfiguration(Properties,
     * io.helidon.service.configuration.api.System, Properties)
     * constructed}.
     *
     * <p>This field may be {@code null}.</p>
     *
     * @see #UCPServiceConfiguration(Properties,
     * io.helidon.service.configuration.api.System, Properties)
     *
     * @see
     * io.helidon.service.configuration.api.ServiceConfigurationProvider#buildFor(Set,
     * Properties)
     */
    @SuppressWarnings("checkstyle:VisibilityModifier")
    protected final Properties coordinates;


    /*
     * Constructors.
     */


    /**
     * Creates a new {@link UCPServiceConfiguration}.
     *
     * @param properties a {@link Properties} instance containing the
     * property values that will ultimately be returned by the default
     * implementation of the {@link #getPropertyNames()} and {@link
     * #getProperty(String, String)} methods; may be {@code null}
     *
     * @param system the {@link
     * io.helidon.service.configuration.api.System} that was
     * determined to be the authoritative {@link
     * io.helidon.service.configuration.api.System}; may be {@code
     * null}
     *
     * @param coordinates a {@link Properties} instance representing
     * the meta-properties in effect; may be {@code null}
     *
     * @see
     * io.helidon.service.configuration.api.ServiceConfigurationProvider#buildFor(Set,
     * Properties)
     *
     * @see
     * io.helidon.service.configuration.api.ServiceConfigurationProvider#getAuthoritativeSystem(Set,
     * Properties)
     */
    protected UCPServiceConfiguration(final Properties properties,
                                      final io.helidon.service.configuration.api.System system,
                                      final Properties coordinates) {
        super("ucp");
        if (properties == null) {
            this.properties = new Properties();
        } else {
            this.properties = properties;
        }
        this.system = system;
        this.coordinates = coordinates;
    }


    /*
     * Instance methods.
     */


    /**
     * Returns an {@linkplain
     * java.util.Collections#unmodifiableSet(Set) unmodifiable} and
     * unchanging {@link Set} of {@link String}s representing the
     * names of properties whose values may be retrieved with the
     * {@link #getProperty(String, String)} method.
     *
     * <p>This method never returns {@code null}.</p>
     *
     * <p>Overrides of this method must not return {@code null}.</p>
     *
     * <p>Overrides of this method must ensure that the {@link Set}
     * returned may be used without the end user having to peform
     * explicit synchronization.</p>
     *
     * <p>This method and its overrides, if any, may return the same
     * {@link Set} instance with each invocation, or different {@link
     * Set} instances with different contents.</p>
     *
     * @return an {@linkplain
     * java.util.Collections#unmodifiableSet(Set) unmodifiable} and
     * unchanging {@link Set} of {@link String}s representing the
     * names of properties whose values may be retrieved with the
     * {@link #getProperty(String, String)} method
     *
     * @see #getProperty(String, String)
     */
    @Override
    public Set<String> getPropertyNames() {
        return this.properties.stringPropertyNames();
    }

    /**
     * Returns a value for the property described by the supplied
     * {@code propertyName}, or the value of the supplied {@code
     * defaultValue} parameter if no such property value exists.
     *
     * <p>This method will return {@code null} if {@code defaultValue}
     * is {@code null}.
     *
     * <p>Overrides of this method may return {@code null} if {@code
     * defaultValue} is {@code null}.</p>
     *
     * <p>This method and its overrides, if any, may return the same
     * or different values for each invocation with the same
     * parameters.</p>
     *
     * @param propertyName the name of the property whose value should
     * be returned; may be {@code null} in which case the value of the
     * supplied {@code defaultValue} parameter will be returned
     * instead
     *
     * @param defaultValue the value to return if a value for the
     * named property could not be found; may be {@code null}
     *
     * @return a value for the property described by the supplied
     * {@code propertyName}, or the value of the supplied {@code
     * defaultValue} parameter
     *
     * @see #getPropertyNames()
     */
    @Override
    public String getProperty(final String propertyName, final String defaultValue) {
        return this.properties.getProperty(propertyName, defaultValue);
    }


}

/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.service.configuration.microprofile.config;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.eclipse.microprofile.config.spi.ConfigSource;

/**
 * A {@link ConfigSource} implementation that wraps the {@linkplain
 * io.helidon.service.configuration.api.ServiceConfiguration#getInstance(String)
 * <code>ServiceConfiguration</code> in effect}.
 *
 * @see #getValue(String)
 *
 * @see #getProperties()
 *
 * @deprecated This class is slated for removal.
 */
@Deprecated
public class ServiceConfigurationConfigSource implements ConfigSource {


    /*
     * Instance fields.
     */


    /**
     * The {@link
     * io.helidon.service.configuration.api.ServiceConfiguration} this
     * {@link ServiceConfigurationConfigSource} wraps.
     *
     * <p>This field may be {@code null}.</p>
     *
     * @see
     * io.helidon.service.configuration.api.ServiceConfiguration#getInstance(String)
     */
    private final io.helidon.service.configuration.api.ServiceConfiguration sc;

    /**
     * The service identifier for which a {@linkplain
     * io.helidon.service.configuration.api.ServiceConfiguration#getInstance(String)
     * <code>ServiceConfiguration</code> should be retrieved}, and
     * also the {@linkplain #getName() name of this
     * <code>ConfigSource</code>} implementation.
     *
     * <p>This field will never be {@code null}.</p>
     *
     * @see #getName()
     */
    private final String name;


    /*
     * Constructors.
     */


    /**
     * Creates a new {@link ServiceConfigurationConfigSource}.
     *
     * <p>The name of this {@link ServiceConfigurationConfigSource}
     * will be equal to the value of the {@linkplain
     * java.lang.System#getProperties() system property} named by
     * concatenating this {@link ServiceConfigurationConfigSource}'s
     * {@linkplain Class#getSimpleName() simple class name} converted
     * to {@linkplain String#toLowerCase() lowercase} with {@code
     * .serviceIdentifier}, or, if that is {@code null}, the
     * {@linkplain Class#getSimpleName() simple class name} converted
     * to {@linkplain String#toLowerCase() lowercase} itself.</p>
     *
     * @see
     * #ServiceConfigurationConfigSource(io.helidon.service.configuration.api.ServiceConfiguration)
     */
    protected ServiceConfigurationConfigSource() {
        this(null);
    }

    /**
     * Creates a new {@link ServiceConfigurationConfigSource}.
     *
     * @param serviceConfiguration the {@link
     * io.helidon.service.configuration.api.ServiceConfiguration} this
     * {@link ServiceConfigurationConfigSource} will wrap.  If this
     * parameter is {@code null}, then the service identifier for
     * which to {@linkplain
     * io.helidon.service.configuration.api.ServiceConfiguration#getInstance(String)
     * find} a {@link
     * io.helidon.service.configuration.api.ServiceConfiguration} will
     * be determined by first trying to use the value of the
     * {@linkplain java.lang.System#getProperties() system property}
     * named by concatenating this {@link
     * ServiceConfigurationConfigSource}'s {@linkplain
     * Class#getSimpleName() simple class name} converted to
     * {@linkplain String#toLowerCase() lowercase} with {@code
     * .serviceIdentifier}, and then, if that is {@code null} (as it
     * commonly may be) by simply using this {@link
     * ServiceConfigurationConfigSource}'s {@linkplain
     * Class#getSimpleName() simple class name} converted to
     * {@linkplain String#toLowerCase() lowercase}.  The result of
     * invoking {@link
     * io.helidon.service.configuration.api.ServiceConfiguration#getInstance(String)}
     * on this value will then be used as if it had been passed
     * directly.
     *
     * @see
     * io.helidon.service.configuration.api.ServiceConfiguration#getInstance(String)
     *
     * @see
     * io.helidon.service.configuration.api.ServiceConfiguration#getServiceIdentifier()
     */
    protected ServiceConfigurationConfigSource(io.helidon.service.configuration.api.ServiceConfiguration serviceConfiguration) {
        super();
        if (serviceConfiguration == null) {
            final String lowerCaseSimpleClassName = this.getClass().getSimpleName().toLowerCase();
            this.name = System.getProperty(lowerCaseSimpleClassName + ".serviceIdentifier", lowerCaseSimpleClassName);
            this.sc = io.helidon.service.configuration.api.ServiceConfiguration.getInstance(this.name);
        } else {
            this.name = serviceConfiguration.getServiceIdentifier();
            this.sc = serviceConfiguration;
        }
    }


    /*
     * Instance methods.
     */


    /**
     * Returns the name of this {@link
     * ServiceConfigurationConfigSource}.
     *
     * <p>This method never returns {@code null}.</p>
     *
     * @return the name of this {@link
     * ServiceConfigurationConfigSource}; never {@code null}
     */
    @Override
    public final String getName() {
        return this.name;
    }

    /**
     * Returns all property names known to this {@link
     * ServiceConfigurationConfigSource} by returning the result of
     * invoking the {@link
     * io.helidon.service.configuration.api.ServiceConfiguration#getPropertyNames()}
     * method on the underlying {@link
     * io.helidon.service.configuration.api.ServiceConfiguration}.
     *
     * <p>This method never returns {@code null}.</p>
     *
     * @return all property names known to this {@link
     * ServiceConfigurationConfigSource}; never {@code null}
     *
     * @see
     * io.helidon.service.configuration.api.ServiceConfiguration#getPropertyNames()
     */
    @Override
    public final Set<String> getPropertyNames() {
        final Set<String> returnValue;
        if (this.sc == null) {
            returnValue = Collections.emptySet();
        } else {
            final Set<String> scPropertyNames = this.sc.getPropertyNames();
            if (scPropertyNames == null) {
                returnValue = Collections.emptySet();
            } else {
                returnValue = scPropertyNames;
            }
        }
        return returnValue;
    }

    /**
     * Returns a {@link Map} representing all properties known to this
     * {@link ServiceConfigurationConfigSource}.
     *
     * <p>This method never returns {@code null}.</p>
     *
     * <p>The {@link Map} that is returned is assembled using
     * invocations of the {@link
     * io.helidon.service.configuration.api.ServiceConfiguration#getPropertyNames()}
     * and {@link
     * io.helidon.service.configuration.api.ServiceConfiguration#getProperty(String)}
     * methods.</p>
     *
     * <p>The {@link Map} that is returned is immutable and
     * thread-safe.</p>
     *
     * @return a {@link Map} representing all properties known to this
     * {@link ServiceConfigurationConfigSource}; never {@code null}
     *
     * @see
     * io.helidon.service.configuration.api.ServiceConfiguration#getPropertyNames()
     *
     * @see
     * io.helidon.service.configuration.api.ServiceConfiguration#getProperty(String)
     */
    @Override
    public final Map<String, String> getProperties() {
        final Map<String, String> returnValue;
        if (this.sc == null) {
            returnValue = Collections.emptyMap();
        } else {
            final Collection<? extends String> propertyNames = this.sc.getPropertyNames();
            if (propertyNames == null || propertyNames.isEmpty()) {
                returnValue = Collections.emptyMap();
            } else {
                final Map<String, String> properties = new LinkedHashMap<>();
                for (final String propertyName : propertyNames) {
                    if (propertyName != null) {
                        properties.put(propertyName, this.sc.getProperty(propertyName));
                    }
                }
                if (properties.isEmpty()) {
                    returnValue = Collections.emptyMap();
                } else {
                    returnValue = Collections.unmodifiableMap(properties);
                }
            }
        }
        return returnValue;
    }

    /**
     * Returns the value of the property identified by the supplied
     * {@code name}, or {@code null} if there is no such property or a
     * value could not be found for some reason.
     *
     * <p>This method may return {@code null}.</p>
     *
     * <p>This method returns the result of invoking the {@link
     * io.helidon.service.configuration.api.ServiceConfiguration#getProperty(String)}
     * method with the supplied {@code name}.</p>
     *
     * @param name the name of the property; may be {@code null} in
     * which case {@code null} will be returned
     *
     * @return a value for the named property, or {@code null}
     */
    @Override
    public final String getValue(final String name) {
        final String returnValue;
        if (this.sc == null) {
            returnValue = null;
        } else {
            returnValue = this.sc.getProperty(name);
        }
        return returnValue;
    }

}

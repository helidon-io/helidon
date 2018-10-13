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
package io.helidon.service.configuration.hikaricp;

import java.util.Properties;
import java.util.Set;

import io.helidon.service.configuration.api.ServiceConfiguration;
import io.helidon.service.configuration.api.ServiceConfigurationProvider; // for javadoc only
import io.helidon.service.configuration.api.System;

/**
 * An abstract {@link ServiceConfiguration} implementation that
 * provides configuration information for <a
 * href="https://github.com/brettwooldridge/HikariCP">Hikari
 * connection pool</a> componentry.
 *
 * @author <a href="mailto:laird.nelson@oracle.com">Laird Nelson</a>
 *
 * @see #HikariCPServiceConfiguration(Properties, System, Properties)
 *
 * @see HikariCPServiceConfigurationProvider
 */
public class HikariCPServiceConfiguration extends ServiceConfiguration {


  /*
   * Instance fields.
   */

  /**
   * A {@link Properties} instance supplied {@linkplain
   * #HikariCPServiceConfiguration(Properties, System, Properties) at
   * construction time} containing the property values that will
   * ultimately be returned by the default implementation of the
   * {@link #getPropertyNames()} and {@link #getProperty(String,
   * String)} methods.
   *
   * <p>This field is never {@code null}.</p>
   *
   * @see #HikariCPServiceConfiguration(Properties, System,
   * Properties)
   */
  @SuppressWarnings("checkstyle:VisibilityModifier")
  protected final Properties properties;

  /**
   * The {@link System} that was determined to be the authoritative
   * {@link System} at the time this {@link
   * HikariCPServiceConfiguration} was {@linkplain
   * #HikariCPServiceConfiguration(Properties, System, Properties)
   * constructed}.
   *
   * <p>This field may be {@code null}.</p>
   *
   * @see #HikariCPServiceConfiguration(Properties, System,
   * Properties)
   *
   * @see ServiceConfigurationProvider#buildFor(Set, Properties)
   */
  @SuppressWarnings("checkstyle:VisibilityModifier")
  protected final System system;

  /**
   * A {@link Properties} instance representing the meta-properties in
   * effect at the time this {@link HikariCPServiceConfiguration} was
   * {@linkplain #HikariCPServiceConfiguration(Properties, System,
   * Properties) constructed}.
   *
   * <p>This field may be {@code null}.</p>
   *
   * @see #HikariCPServiceConfiguration(Properties, System,
   * Properties)
   *
   * @see ServiceConfigurationProvider#buildFor(Set, Properties)
   */
  @SuppressWarnings("checkstyle:VisibilityModifier")
  protected final Properties coordinates;


  /*
   * Constructors.
   */


  /**
   * Creates a new {@link HikariCPServiceConfiguration}.
   *
   * @param properties a {@link Properties} instance containing the
   * property values that will ultimately be returned by the default
   * implementation of the {@link #getPropertyNames()} and {@link
   * #getProperty(String, String)} methods; may be {@code null}
   *
   * @param system the {@link System} that was determined to be the
   * authoritative {@link System}; may be {@code null}
   *
   * @param coordinates a {@link Properties} instance representing the
   * meta-properties in effect; may be {@code null}
   *
   * @see ServiceConfigurationProvider#buildFor(Set, Properties)
   *
   * @see ServiceConfigurationProvider#getAuthoritativeSystem(Set, Properties)
   */
  protected HikariCPServiceConfiguration(final Properties properties, final System system, final Properties coordinates) {
    super("hikaricp");
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
   * Returns an {@linkplain java.util.Collections#unmodifiableSet(Set)
   * unmodifiable} and unchanging {@link Set} of {@link String}s
   * representing the names of properties whose values may be
   * retrieved with the {@link #getProperty(String, String)} method.
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
   * @return an {@linkplain java.util.Collections#unmodifiableSet(Set)
   * unmodifiable} and unchanging {@link Set} of {@link String}s
   * representing the names of properties whose values may be
   * retrieved with the {@link #getProperty(String, String)} method
   *
   * @see #getProperty(String, String)
   */
  @Override
  public Set<String> getPropertyNames() {
    return this.properties.stringPropertyNames();
  }

  /**
   * Returns a value for the property described by the supplied {@code
   * propertyName}, or the value of the supplied {@code defaultValue}
   * parameter if no such property value exists.
   *
   * <p>This method will return {@code null} if {@code defaultValue}
   * is {@code null}.
   *
   * <p>Overrides of this method may return {@code null} if {@code
   * defaultValue} is {@code null}.</p>
   *
   * <p>This method and its overrides, if any, may return the same or
   * different values for each invocation with the same
   * parameters.</p>
   *
   * @param propertyName the name of the property whose value should
   * be returned; may be {@code null} in which case the value of the
   * supplied {@code defaultValue} parameter will be returned instead
   *
   * @param defaultValue the value to return if a value for the named
   * property could not be found; may be {@code null}
   *
   * @return a value for the property described by the supplied {@code
   * propertyName}, or the value of the supplied {@code defaultValue}
   * parameter
   *
   * @see #getPropertyNames()
   */
  @Override
  public String getProperty(final String propertyName, final String defaultValue) {
    return this.properties.getProperty(propertyName, defaultValue);
  }


}

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
package io.helidon.service.configuration.ucp.localhost;

import java.util.Objects;
import java.util.Properties;

import io.helidon.service.configuration.api.System;
import io.helidon.service.configuration.localhost.LocalhostSystem;
import io.helidon.service.configuration.ucp.UCPServiceConfiguration;
import io.helidon.service.configuration.ucp.UCPServiceConfigurationProvider;

/**
 * A {@link UCPServiceConfigurationProvider} that {@linkplain
 * #installDataSourceProperties(Properties, System, Properties,
 * String) automatically creates} <a
 * href="http://www.h2database.com/html/features.html#in_memory_databases">in-memory
 * H2 databases</a> as needed.
 *
 * @see #installDataSourceProperties(Properties, System, Properties, String)
 *
 * @see UCPServiceConfigurationLocalhost
 *
 * @see UCPServiceConfigurationProvider
 */
public class UCPServiceConfigurationLocalhostProvider extends UCPServiceConfigurationProvider {


  /*
   * Constructors.
   */


  /**
   * Creates a new {@link UCPServiceConfigurationProvider}.
   */
  public UCPServiceConfigurationLocalhostProvider() {
    super();
  }


  /*
   * Instance methods.
   */


  /**
   * Overrides the {@link
   * UCPServiceConfigurationProvider#create(Properties, System,
   * Properties)} method to return a new {@link
   * UCPServiceConfigurationLocalhost} instance when invoked.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * <p>Overrides of this method must not return {@code null}.</p>
   *
   * <p>This method returns a new {@link
   * UCPServiceConfigurationLocalhost} instance with each
   * invocation.</p>
   *
   * <p>Overrides of this method must return a new {@link
   * UCPServiceConfiguration} implementation of some kind.</p>
   *
   * @param properties a {@link Properties} instance that will be used
   * as the basis of the {@link UCPServiceConfigurationLocalhost}
   * implementation that will be returned; must not be {@code null}
   *
   * @param system a {@link System} determined to be in effect; may,
   * strictly speaking, be {@code null} but ordinarily is non-{@code
   * null} and {@linkplain System#isEnabled() enabled}
   *
   * @param coordinates a {@link Properties} instance representing the
   * meta-properties in effect; may be {@code null}
   *
   * @return a new {@link UCPServiceConfigurationLocalhost}
   * instance; never {@code null}
   *
   * @exception NullPointerException if {@code properties} is {@code
   * null}
   */
  @Override
  protected UCPServiceConfiguration create(final Properties properties, final System system, final Properties coordinates) {
    return new UCPServiceConfigurationLocalhost(this, Objects.requireNonNull(properties), system, coordinates);
  }

  /**
   * Overrides the {@link
   * UCPServiceConfigurationProvider#appliesTo(Properties, System,
   * Properties)} method to return {@code true} if the supplied {@link
   * System} is {@linkplain System#isEnabled() enabled} and an
   * instance of {@link LocalhostSystem} and if the {@linkplain
   * UCPServiceConfigurationProvider#appliesTo(Properties, System,
   * Properties)} method returns {@code true}.
   *
   * @param properties a {@link Properties} instance that will be used
   * as the basis of the {@link UCPServiceConfigurationLocalhost}
   * implementation that will be returned by the {@link
   * #create(Properties, System, Properties)} method; must not be
   * {@code null}
   *
   * @param system a {@link System} determined to be in effect; may,
   * strictly speaking, be {@code null} but ordinarily is non-{@code
   * null} and {@linkplain System#isEnabled() enabled}
   *
   * @param coordinates a {@link Properties} instance representing the
   * meta-properties in effect; may be {@code null}
   *
   * @return {@code true} if this {@link
   * UCPServiceConfigurationLocalhostProvider} applies to the
   * configuration space implied by the supplied parameters; {@code
   * false} otherwise
   *
   * @see UCPServiceConfigurationProvider#appliesTo(Properties,
   * System, Properties)
   */
  @Override
  protected boolean appliesTo(final Properties properties, final System system, final Properties coordinates) {
    Objects.requireNonNull(properties);
    final boolean returnValue =
      system instanceof LocalhostSystem && system.isEnabled() && super.appliesTo(properties, system, coordinates);
    return returnValue;
  }

  /**
   * Overrides the {@link
   * UCPServiceConfigurationProvider#installDataSourceProperties(Properties,
   * System, Properties, String)} method to automatically create <a
   * href="http://www.h2database.com/html/features.html#in_memory_databases">in-memory
   * H2 databases</a> as needed.
   *
   * <p>Specifically, this method:</p>
   *
   * <ol>
   *
   * <li>Calls the {@link
   * UCPServiceConfigurationProvider#installDataSourceProperties(Properties,
   * System, Properties, String)} method with the supplied
   * parameters.</li>
   *
   * <li>Checks to see if a property named {@code
   * javax.sql.DataSource.}<em>{@code dataSourceName}</em>{@code
   * .explicitlyConfigured} has a {@link String} value equal to
   * anything other than {@code true}, including {@code null}.</li>
   *
   * <li>If so, then it {@linkplain Properties#setProperty(String,
   * String) sets} certain properties on {@code target} as
   * follows:
   *
   * <ol>
   *
   * <li>{@code javax.sql.DataSource.}<em>{@code
   * dataSourceName}</em>{@code .dataSourceClassName =
   * org.h2.jdbcx.JdbcDataSource}</li>
   *
   * <li>{@code javax.sql.DataSource.}<em>{@code
   * dataSourceName}</em>{@code .dataSource.description = A local,
   * transient, in-memory H2 database}</li>
   *
   * <li>{@code javax.sql.DataSource.}<em>{@code
   * dataSourceName}</em>{@code .dataSource.user = sa}</li>
   *
   * <li>{@code javax.sql.DataSource.}<em>{@code
   * dataSourceName}</em>{@code .dataSource.password = }</li>
   *
   * <li>{@code javax.sql.DataSource.}<em>{@code
   * dataSourceName}</em>{@code .dataSourceUrl =
   * jdbc:h2:mem:}<em>{@code dataSourceName}</em></li>
   *
   * </ol></li>
   *
   * </ol>
   *
   * @param target a {@link Properties} instance that will be used as
   * the basis of the {@link UCPServiceConfigurationLocalhost}
   * implementation that will be returned by the {@link
   * #create(Properties, System, Properties)} method and into which
   * properties may be installed; must not be {@code null}
   *
   * @param system a {@link System} determined to be in effect; may,
   * strictly speaking, be {@code null} but ordinarily is non-{@code
   * null} and {@linkplain System#isEnabled() enabled}
   *
   * @param coordinates a {@link Properties} instance representing the
   * meta-properties in effect; may be {@code null}
   *
   * @param dataSourceName the data source name in question; may be
   * {@code null}
   *
   * @exception NullPointerException if {@code target} is {@code null}
   *
   * @see
   * UCPServiceConfigurationProvider#installDataSourceProperties(Properties,
   * System, Properties, String)
   */
  @Override
  protected void installDataSourceProperties(final Properties target,
                                             final System system,
                                             final Properties coordinates,
                                             String dataSourceName) {
    Objects.requireNonNull(target);
    super.installDataSourceProperties(target, system, coordinates, dataSourceName);

    if (!"true".equalsIgnoreCase(this.getDataSourceProperty(target,
                                                            system,
                                                            coordinates,
                                                            dataSourceName,
                                                            "explicitlyConfigured"))) {
      final String prefix = this.getPrefix();
      assert prefix != null;
      assert !prefix.isEmpty();

      final String dataSourceClassName;
      final String description;
      final String url;
      final String urlValue;
      final String user;
      final String password;
      if (dataSourceName == null) {
        urlValue = "jdbc:h2:mem:test";
        dataSourceClassName = prefix + ".dataSourceClassName";
        description = prefix + ".dataSource.description";
        url = prefix + ".dataSource.url";
        user = prefix + ".dataSource.user";
        password = prefix + ".dataSource.password";
      } else {
        dataSourceName = dataSourceName.trim();
        if (dataSourceName.isEmpty()) {
          urlValue = "jdbc:h2:mem:test";
          dataSourceClassName = prefix + ".dataSourceClassName";
          description = prefix + ".dataSource.description";
          url = prefix + ".dataSource.url";
          user = prefix + ".dataSource.user";
          password = prefix + ".dataSource.password";
        } else {
          dataSourceClassName = prefix + "." + dataSourceName + ".dataSourceClassName";
          description = prefix + "." + dataSourceName + ".dataSource.description";
          url = prefix + "." + dataSourceName + ".dataSource.url";
          urlValue = "jdbc:h2:mem:" + dataSourceName;
          user = prefix + "." + dataSourceName + ".dataSource.user";
          password = prefix + "." + dataSourceName + ".dataSource.password";
        }
      }

      target.setProperty(dataSourceClassName, org.h2.jdbcx.JdbcDataSource.class.getName());
      target.setProperty(url, urlValue);
      target.setProperty(description, "A local, transient, in-memory H2 database");
      target.setProperty(user, "sa");
      target.setProperty(password, "");
    }
  }

  final String extractDataSourceName(final String prefixedProperty) {
    String returnValue = null;
    if (prefixedProperty != null && !prefixedProperty.isEmpty()) {
      final String prefix = this.getPrefix();
      assert prefix != null;
      assert !prefix.isEmpty();
      final String prefixWithDot = prefix + ".";
      final int prefixWithDotLength = prefixWithDot.length();
      if (prefixedProperty.startsWith(prefixWithDot) && prefixedProperty.length() > prefixWithDotLength) {
        final int dotIndex = prefixedProperty.indexOf('.', prefixWithDotLength);
        if (dotIndex > 0) {
          returnValue = prefixedProperty.substring(prefixWithDotLength, dotIndex);
        }
      }
    }
    return returnValue;
  }

}

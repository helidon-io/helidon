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
import io.helidon.service.configuration.ucp.UCPServiceConfiguration;

/**
 * A {@link UCPServiceConfiguration} that can dynamically add
 * data source properties when they are requested.
 *
 * @see #getProperty(String, String)
 *
 * @see UCPServiceConfigurationLocalhostProvider
 *
 * @see UCPServiceConfiguration
 */
public class UCPServiceConfigurationLocalhost extends UCPServiceConfiguration {


  /*
   * Instance fields.
   */


  /**
   * The {@link UCPServiceConfigurationLocalhostProvider} that
   * {@linkplain
   * io.helidon.service.configuration.ucp.UCPServiceConfigurationProvider#buildFor(Set,
   * Properties) built} this {@link UCPServiceConfigurationLocalhost}.
   *
   * <p>This field is never {@code null}.</p>
   *
   * @see
   * #UCPServiceConfigurationLocalhost(io.helidon.service.configuration.ucp.UCPServiceConfigurationLocalhostProvider,
   * Properties, System, Properties)
   */
  private final UCPServiceConfigurationLocalhostProvider provider;


  /*
   * Constructors.
   */


  /**
   * Creates a new {@link UCPServiceConfigurationLocalhost}.
   *
   * @param provider the {@link
   * UCPServiceConfigurationLocalhostProvider} that {@linkplain
   * io.helidon.service.configuration.ucp.UCPServiceConfigurationProvider#buildFor(Set,
   * Properties) is building} this {@link
   * UCPServiceConfigurationLocalhost}; must not be {@code null}
   *
   * @param properties a {@link Properties} instance that will be used
   * as the basis of this implementation; must not be {@code null}
   *
   * @param system a {@link System} determined to be in effect; may,
   * strictly speaking, be {@code null} but ordinarily is non-{@code
   * null} and {@linkplain System#isEnabled() enabled}
   *
   * @param coordinates a {@link Properties} instance representing the
   * meta-properties in effect; may be {@code null}
   *
   * @exception NullPointerException if {@code provider} or {@code
   * properties} is {@code null}
   *
   * @see UCPServiceConfigurationLocalhostProvider
   */
  public UCPServiceConfigurationLocalhost(final UCPServiceConfigurationLocalhostProvider provider,
                                               final Properties properties,
                                               final System system,
                                               final Properties coordinates) {
    super(Objects.requireNonNull(properties), system, coordinates);
    this.provider = Objects.requireNonNull(provider);
  }


  /*
   * Instance methods.
   */


  /**
   * Overrides the {@link UCPServiceConfiguration#getProperty(String,
   * String)} method to return a value for the supplied {@code
   * propertyName}, and, if one is not found and the {@code
   * propertyName} parameter value starts with {@code
   * javax.sql.Datasource.}, to "just-in-time" install certain
   * properties related to the data source in question, before
   * attempting its retrieval again.
   *
   * <p>This method may return {@code null} if {@code defaultValue} is
   * {@code null}.</p>
   *
   * <p>Overrides of this method may return {@code null}.</p>
   *
   * @param propertyName the name of the property in question; must
   * not be {@code null}
   *
   * @param defaultValue the value to return if all attempts to
   * retrieve a property value fail; may be {@code null}
   *
   * @return a value for the property named by the supplied {@code
   * propertyName}, or {@code defaultValue} if no such value exists
   * and none could be generated
   *
   * @exception NullPointerException if {@code propertyName} is {@code
   * null}
   *
   * @see
   * UCPServiceConfigurationLocalhostProvider#installDataSourceProperties(Properties,
   * System, Properties, String)
   */
  @Override
  public String getProperty(final String propertyName, final String defaultValue) {
    String returnValue = this.properties.getProperty(Objects.requireNonNull(propertyName));
    if (returnValue == null) {
      final String prefix = this.provider.getPrefix();
      assert prefix != null;
      assert !prefix.isEmpty();
      if (propertyName.startsWith(prefix + ".")) {
        String dataSourceName = this.provider.extractDataSourceName(propertyName);
        if (dataSourceName != null) {
          dataSourceName = dataSourceName.trim();
          if (!dataSourceName.isEmpty()) {
            this.provider.installDataSourceProperties(this.properties, this.system, this.coordinates, dataSourceName);
            returnValue = this.properties.getProperty(propertyName);
          }
        }
      }
    }
    return returnValue;
  }

}

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

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

import io.helidon.service.configuration.api.ServiceConfiguration;
import io.helidon.service.configuration.api.ServiceConfigurationProvider;
import io.helidon.service.configuration.api.System;

/**
 * An abstract {@link ServiceConfigurationProvider} implementation
 * that provides {@link UCPServiceConfiguration} instances.
 *
 * @see #buildFor(Set, Properties)
 *
 * @see UCPServiceConfiguration
 */
public abstract class UCPServiceConfigurationProvider extends ServiceConfigurationProvider {


  /*
   * Static fields.
   */


  /**
   * A {@link Pattern} used to split
   * whitespace-and-comma-separated&mdash;or just
   * comma-separated&mdash;tokens from a {@link String}.
   *
   * <p>This field is never {@code null}.</p>
   */
  private static final Pattern WHITESPACE_COMMA_PATTERN = Pattern.compile("\\s*,\\s*");


  /*
   * Instance fields.
   */


  /**
   * The prefix with which relevant property names will start.
   *
   * <p>This field is never {@code null}.</p>
   *
   * @see #getPrefix()
   */
  private final String prefix;


  /*
   * Constructors.
   */


  /**
   * Creates a new {@link UCPServiceConfigurationProvider} whose
   * {@linkplain
   * ServiceConfigurationProvider#ServiceConfigurationProvider(String)
   * service identifier} is {@code ucp}.
   */
  protected UCPServiceConfigurationProvider() {
    super("ucp");
    this.prefix = "javax.sql.DataSource";
  }


  /*
   * Instance methods.
   */


  /**
   * Returns the prefix with which relevant property names will start.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * <p>Overrides of this method must not return {@code null}.</p>
   *
   * <p>The default implementation of this method returns {@code
   * javax.sql.DataSource}.</p>
   *
   * <p>Undefined behavior will result if the return value of an
   * override of this method is {@linkplain String#isEmpty()
   * empty}.</p>
   *
   * @return the non-{@code null} {@code prefix} with which relevant
   * property names will start
   */
  public String getPrefix() {
    return this.prefix;
  }

  /**
   * Creates and returns a new {@link UCPServiceConfiguration}.
   *
   * <p>Normally this method is invoked as a result of an invocation
   * of the {@link #buildFor(Set, Properties)} method.  In these
   * cases, the {@link #appliesTo(Properties, System, Properties)}
   * method will have already been invoked and will have returned
   * {@code true}.</p>
   *
   * <p>Overrides of this method must not call the {@link
   * #buildFor(Set, Properties)} method, as an infinite loop may
   * result.</p>
   *
   * @param properties a {@link Properties} instance that can be used
   * as the basis of a {@link UCPServiceConfiguration}
   * implementation; must not be {@code null}
   *
   * @param system a {@link System} determined to be in effect; may,
   * strictly speaking, be {@code null} but ordinarily is non-{@code
   * null} and {@linkplain System#isEnabled() enabled}
   *
   * @param coordinates a {@link Properties} instance representing the
   * meta-properties in effect; may be {@code null}
   *
   * @return a new, non-{@code null} {@link UCPServiceConfiguration}
   *
   * @exception NullPointerException if {@code properties} is {@code
   * null}
   *
   * @see
   * UCPServiceConfiguration#UCPServiceConfiguration(Properties,
   * System, Properties)
   *
   * @see #buildFor(Set, Properties)
   *
   * @see #appliesTo(Properties, System, Properties)
   */
  protected UCPServiceConfiguration create(final Properties properties, final System system, final Properties coordinates) {
    Objects.requireNonNull(properties);
    return new UCPServiceConfiguration(properties, system, coordinates);
  }

  /**
   * Overrides the {@link ServiceConfigurationProvider#buildFor(Set,
   * Properties)} method to ensure that there is an {@linkplain
   * ServiceConfigurationProvider#getAuthoritativeSystem(Set,
   * Properties) authoritative <code>System</code>} and then, if so,
   * calls the {@link #appliesTo(Properties, System, Properties)}
   * method, and, if that returns {@code true}, then calls the {@link
   * #create(Properties, System, Properties)} method and returns its
   * result.
   *
   * <p>This method may&mdash;and often does&mdash;return {@code
   * null}.</p>
   *
   * @param systems a {@link Set} of {@link System}s that will help
   * determine whether a {@link ServiceConfiguration} is in effect or
   * not; may be {@code null}
   *
   * @param coordinates a {@link Properties} instance representing the
   * meta-properties in effect; may be {@code null}
   *
   * @return a new {@link ServiceConfiguration} instance suitable for
   * the configuration space implied by the supplied {@link Set} of
   * {@link System}s and coordinates, or {@code null}
   *
   * @see #appliesTo(Properties, System, Properties)
   *
   * @see #create(Properties, System, Properties)
   */
  @Override
  public final ServiceConfiguration buildFor(final Set<? extends System> systems, final Properties coordinates) {
    ServiceConfiguration returnValue = null;
    final System system = getAuthoritativeSystem(systems, coordinates);
    if (system != null && system.isEnabled()) {
      final Properties properties = new Properties();
      if (this.appliesTo(properties, system, coordinates)) {
        returnValue = this.create(properties, system, coordinates);
      }
    }
    return returnValue;
  }

  /**
   * Returns {@code true} if this {@link
   * UCPServiceConfigurationProvider} is relevant in the
   * configuration space described by the supplied properties, {@link
   * System} and coordinates.
   *
   * <p>The default implementation of this method:</p>
   *
   * <ol>
   *
   * <li>Looks for a property named {@code ucp.dataSourceNames}.
   * The value of this property is a comma-separated {@link String}
   * whose components are names of data sources that will ultimately
   * have Hikari connection pools set up for them.</li>
   *
   * <li>If that property is not found, then all property names found
   * in the supplied {@code properties}, {@code system} and {@code
   * coordinates} are scanned for those starting with {@code
   * javax.sql.DataSource.}, and for all names in that subset, the
   * next period-separated component of the name is taken to be a data
   * source name.  For example, a property named {@code
   * javax.sql.DataSource.test.dataSourceClassName} will yield a data
   * source name of {@code test}.</li>
   *
   * <li>For each such data source name discovered, the {@link
   * #installDataSourceProperties(Properties, System, Properties,
   * String)} method is called with the parameters supplied to this
   * method and the data source name.</li>
   *
   * <li>After this installation step, a property named according to
   * the following pattern is sought: {@code
   * javax.sql.DataSource.}<em>{@code dataSourceName}</em>{@code
   * .dataSourceClassName}.</li>
   *
   * <li>If that yields a class name and the corresponding class can
   * be {@linkplain Class#forName(String) loaded}, then {@code true}
   * is returned.</li>
   *
   * <li>If that does not yield a class name, then a property named
   * according to the following pattern is sought: {@code
   * javax.sql.DataSource.}<em>{@code dataSourceName}</em>{@code
   * .jdbcUrl}.</li>
   *
   * <li>If that yields a non-{@code null} {@link String} then it is
   * passed to the {@link DriverManager#getDriver(String)} method.  If
   * that method invocation results in a non-{@code null} return
   * value, then {@code true} is returned.</li>
   *
   * <li>In all other cases, {@code false} is returned.</li>
   *
   * </ol>
   *
   * @param properties a {@link Properties} instance that may be used
   * later by the {@link #create(Properties, System, Properties)}
   * method as the basis of a {@link UCPServiceConfiguration}
   * implementation; must not be {@code null}
   *
   * @param system a {@link System} determined to be in effect; may,
   * strictly speaking, be {@code null} but ordinarily is non-{@code
   * null} and {@linkplain System#isEnabled() enabled}
   *
   * @param coordinates a {@link Properties} instance representing the
   * meta-properties in effect; may be {@code null}
   *
   * @return {@code true} if this {@link
   * UCPServiceConfigurationProvider} applies to the
   * configuration space implied by the supplied parameters; {@code
   * false} otherwise
   *
   * @exception NullPointerException if {@code properties} is {@code
   * null}
   */
  protected boolean appliesTo(final Properties properties, final System system, final Properties coordinates) {
    Objects.requireNonNull(properties);
    boolean returnValue = false;
    if (system != null && system.isEnabled()) {
      final Collection<? extends String> dataSourceNames = this.getDataSourceNames(properties, system, coordinates);
      if (dataSourceNames != null && !dataSourceNames.isEmpty()) {
        final String prefix = this.getPrefix();
        assert prefix != null;
        for (final String dataSourceName : dataSourceNames) {
          installDataSourceProperties(properties, system, coordinates, dataSourceName);
          final String dataSourceClassName =
            this.getDataSourceProperty(properties, system, coordinates, dataSourceName, "dataSourceClassName");
          if (dataSourceClassName == null) {
            final String jdbcUrl = this.getDataSourceProperty(properties, system, coordinates, dataSourceName, "jdbcUrl");
            if (jdbcUrl != null) {
              try {
                final Object driver = DriverManager.getDriver(jdbcUrl);
                assert driver != null;
                returnValue = true;
              } catch (final SQLException ohWell) {
                assert !returnValue;
              }
            }
          } else {
            try {
              Class.forName(dataSourceClassName);
              returnValue = true;
            } catch (final ClassNotFoundException classNotFoundException) {
              assert !returnValue;
            }
          }
          if (!returnValue) {
            break;
          }
        }
      }
    }
    return returnValue;
  }

  private Set<String> getDataSourceNames(final Properties target, final System system, final Properties coordinates) {
    Objects.requireNonNull(target);

    Set<String> returnValue = new HashSet<>();

    final String dataSourceNamesProperty =
      getProperty(target, system, coordinates, this.getServiceIdentifier() + ".dataSourceNames", null);
    if (dataSourceNamesProperty == null || dataSourceNamesProperty.trim().isEmpty()) {

      final Set<String> allPropertyNames = getPropertyNames(target, system, coordinates);
      assert allPropertyNames != null;

      final String prefixWithDot = new StringBuilder(this.getPrefix()).append(".").toString();
      final int prefixWithDotLength = prefixWithDot.length();

      for (String propertyName : allPropertyNames) {
        if (propertyName != null
            && propertyName.length() > prefixWithDotLength
            && propertyName.startsWith(prefixWithDot)) {
          propertyName = propertyName.substring(prefixWithDotLength);
          final int dotIndex = propertyName.indexOf('.');
          if (dotIndex > 0) {
            returnValue.add(propertyName.substring(0, dotIndex));
          }
        }
      }

      if (returnValue.isEmpty()) {
        returnValue.add(null);
      }
    } else {
      returnValue.addAll(Arrays.asList(WHITESPACE_COMMA_PATTERN.split(dataSourceNamesProperty)));
    }

    return returnValue;
  }

  /**
   * Installs any discoverable properties that might exist that
   * pertain to the data source identified by the supplied {@code
   * dataSourceName} into the supplied {@code target} {@link
   * Properties} object, optionally using the supplied {@code system}
   * and {@code coordinates} objects in the process.
   *
   * <p>The default implementation of this method:</p>
   *
   * <ol>
   *
   * <li>Looks for a property named {@code ucp.}<em>{@code
   * dataSourceName}</em>{@code .propertiesPath}.  If it is found,
   * then it will be converted to a {@link Path} via the {@link
   * Paths#get(String, String...)} method.</li>
   *
   * <li>If the resulting {@link Path} identifies a readable file,
   * then the file is read into a temporary {@link Properties} object
   * via the {@link Properties#load(Reader)} method.</li>
   *
   * <li>{@code target} will now contain a property named {@code
   * javax.sql.DataSource.}<em>{@code dataSourceName}</em>{@code
   * .explicitlyConfigured} with a {@link String} value of {@code
   * true}.</li>
   *
   * <li>Every property that the temporary {@link Properties} object
   * contains as a result of reading the file will be added to {@code
   * target}, prefixed with {@code javax.sql.DataSource.}<em>{@code
   * dataSourceName}</em>{@code .}.</li>
   *
   * </ol>
   *
   * <p>If the supplied {@code dataSourceName} is {@code null} or
   * {@linkplain String#isEmpty() empty}, or if the property being
   * read out of the temporary {@link Properties} object already
   * starts with {@code javax.sql.DataSource.}, then it is copied into
   * {@code target} without any prefix or modification.</p>
   *
   * @param target the {@link Properties} into which data source
   * properties will be installed; must not be {@code null}
   *
   * @param system a {@link System} determined to be in effect; may,
   * strictly speaking, be {@code null} but ordinarily is non-{@code
   * null} and {@linkplain System#isEnabled() enabled}
   *
   * @param coordinates a {@link Properties} instance representing the
   * meta-properties in effect; may be {@code null}
   *
   * @param dataSourceName the name of the data source for which
   * properties should be installed; may be {@code null}
   *
   * @exception NullPointerException if {@code target} is {@code null}
   */
  protected void installDataSourceProperties(final Properties target,
                                             final System system,
                                             final Properties coordinates,
                                             final String dataSourceName) {
    Objects.requireNonNull(target);

    final String hikariPropertiesPathString =
      this.getDataSourceProperty(target,
                                 system,
                                 coordinates,
                                 this.getServiceIdentifier(),
                                 dataSourceName,
                                 "propertiesPath");

    if (hikariPropertiesPathString != null) {

      final Properties temp = new Properties();
      try (Reader reader = Files.newBufferedReader(Paths.get(hikariPropertiesPathString), StandardCharsets.UTF_8)) {
        temp.load(reader);
        this.setDataSourceProperty(target, dataSourceName, "explicitlyConfigured", "true");
      } catch (final IOException ohWell) {

      }

      if (!temp.isEmpty()) {

        // Now read each property out of temp, prefix its name with
        // <prefix>.<dataSourceName>, and set it in target.
        //
        // So test.properties' jdbcUrl=jdbc:foo:bar becomes
        // javax.sql.DataSource.test.jdbcUrl=jdbc:foo:bar.

        final Collection<? extends String> names = temp.stringPropertyNames();
        assert names != null;
        assert !names.isEmpty();

        final String prefix = this.getPrefix();
        assert prefix != null;

        for (final String unprefixedPropertyName : names) {
          if (unprefixedPropertyName != null && !unprefixedPropertyName.isEmpty()) {
            if (dataSourceName == null
                || dataSourceName.isEmpty()
                || unprefixedPropertyName.startsWith(prefix + "." + dataSourceName + ".")) {
              target.setProperty(unprefixedPropertyName,
                                 temp.getProperty(unprefixedPropertyName));
            } else {
              target.setProperty(prefix + "." + dataSourceName + "." + unprefixedPropertyName,
                                 temp.getProperty(unprefixedPropertyName));
            }
          }
        }

      }

    }
  }

  private Object setDataSourceProperty(final Properties properties,
                                       String dataSourceName,
                                       final String unprefixedPropertyName,
                                       final String propertyValue) {
    Objects.requireNonNull(properties);
    final Object returnValue =
      properties.setProperty(prefixDataSourcePropertyName(this.getPrefix(), dataSourceName, unprefixedPropertyName),
                             propertyValue);
    return returnValue;
  }


  /**
   * Returns the value of a property found in the {@code properties}
   * parameter value, or, failing that, in the supplied {@link
   * System}'s {@linkplain System#getProperties() properties}, or,
   * failing that, in the supplied {@code coordinates} parameter
   * value, that applies to the data source identified by the supplied
   * {@code dataSourceName} parameter, taking into account the
   * {@linkplain #getPrefix() prefix}.
   *
   * <p>This method may return {@code null}.</p>
   *
   * @param properties the {@link Properties} to check first; must not
   * be {@code null}
   *
   * @param system a {@link System} determined to be in effect; may,
   * strictly speaking, be {@code null} but ordinarily is non-{@code
   * null} and {@linkplain System#isEnabled() enabled}
   *
   * @param coordinates a {@link Properties} instance representing the
   * meta-properties in effect; may be {@code null}
   *
   * @param dataSourceName the name of a data source; may be {@code
   * null}
   *
   * @param unprefixedPropertyName the "simple" property name being
   * sought; must not be {@code null}
   *
   * @return the value of the property, or {@code null} if no such
   * property exists
   *
   * @exception NullPointerException if {@code properties} or {@code
   * unprefixedPropertyName} is {@code null}
   */
  protected final String getDataSourceProperty(final Properties properties,
                                               final System system,
                                               final Properties coordinates,
                                               String dataSourceName,
                                               final String unprefixedPropertyName) {
    return this.getDataSourceProperty(properties, system, coordinates, this.getPrefix(), dataSourceName, unprefixedPropertyName);
  }

  private String getDataSourceProperty(final Properties properties,
                                       final System system,
                                       final Properties coordinates,
                                       final String prefix,
                                       String dataSourceName,
                                       final String unprefixedPropertyName) {
    final String returnValue =
      getProperty(properties,
                  system,
                  coordinates,
                  prefixDataSourcePropertyName(prefix, dataSourceName, unprefixedPropertyName),
                  null);
    return returnValue;
  }


  /*
   * Static methods.
   */


  private static String prefixDataSourcePropertyName(final String prefix,
                                                     String dataSourceName,
                                                     final String unprefixedPropertyName) {
    Objects.requireNonNull(prefix);
    if (prefix.isEmpty()) {
      throw new IllegalArgumentException("prefix.isEmpty()");
    }
    Objects.requireNonNull(unprefixedPropertyName);

    final String prefixedPropertyName;
    if (dataSourceName == null) {
      prefixedPropertyName = prefix + "." + unprefixedPropertyName;
    } else {
      dataSourceName = dataSourceName.trim();
      if (dataSourceName.isEmpty()) {
        prefixedPropertyName = prefix + "." + unprefixedPropertyName;
      } else {
        prefixedPropertyName = prefix + "." + dataSourceName + "." + unprefixedPropertyName;
      }
    }
    return prefixedPropertyName;
  }

  private static Set<String> getPropertyNames(final Properties properties,
                                              final System system,
                                              final Properties coordinates) {
    Objects.requireNonNull(properties);
    final Set<String> returnValue = new HashSet<>();
    final Properties systemProperties;
    if (system == null || !system.isEnabled()) {
      systemProperties = null;
    } else {
      systemProperties = system.getProperties();
    }
    if (systemProperties != null) {
      returnValue.addAll(systemProperties.stringPropertyNames());
    }
    returnValue.addAll(properties.stringPropertyNames());
    if (coordinates != null) {
      returnValue.addAll(coordinates.stringPropertyNames());
    }
    return Collections.unmodifiableSet(returnValue);
  }

  private static String getProperty(final Properties properties,
                                    final System system,
                                    final Properties coordinates,
                                    final String propertyName,
                                    final String defaultValue) {
    Objects.requireNonNull(properties);
    Objects.requireNonNull(propertyName);
    String returnValue = properties.getProperty(propertyName);
    if (returnValue == null) {
      if (coordinates != null) {
        returnValue = coordinates.getProperty(propertyName);
      }
      if (returnValue == null) {
        if (system == null || !system.isEnabled()) {
          returnValue = java.lang.System.getProperty(propertyName, defaultValue);
        } else {
          final Properties systemProperties = system.getProperties();
          if (systemProperties == null) {
            returnValue = defaultValue;
          } else {
            returnValue = systemProperties.getProperty(propertyName, defaultValue);
          }
        }
      }
    }
    return returnValue;
  }

}

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
package io.helidon.service.configuration.api;

import java.util.Objects;
import java.util.Properties;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;

/**
 * An abstract encapsulation of an automatically discovered
 * configuration for a given <em>service</em>.
 *
 * <p>For the purposes of configuration, a service is normally
 * represented as a client library that connects to a remote provider
 * of business value.  Typically the client library needs to be
 * configured in some way in order to function at all.  Instances of
 * this class aim to provide such configuration in as automated a
 * manner as possible.</p>
 *
 * <p>{@link ServiceConfiguration} instances are typically produced by
 * {@link ServiceConfigurationProvider} instances.  Obtaining a {@link
 * ServiceConfiguration} by any other means may result in undefined
 * behavior.</p>
 *
 * @see #getInstance(String)
 *
 * @see ServiceConfigurationProvider
 *
 * @deprecated This class is slated for removal.
 */
@Deprecated
public abstract class ServiceConfiguration {


  /*
   * Static fields.
   */


  /**
   * An {@link Iterable} of {@link ServiceConfiguration} instances
   * normally set to the return value of the {@link
   * ServiceLoader#load(Class)} method.
   *
   * <p>This field may be {@code null}.</p>
   *
   * <p>This field exists primarily to ensure that the built in {@link
   * ServiceLoader} cache of discovered instances is used
   * properly.</p>
   *
   * @see #getInstance(String, Properties)
   */
  private static volatile Iterable<ServiceConfigurationProvider> serviceConfigurationProviders;


  /*
   * Instance fields.
   */


  /**
   * The identifier of the service this {@link ServiceConfiguration}
   * implementation provides configuration for.
   *
   * <p>This field is never {@code null}.</p>
   *
   * @see #ServiceConfiguration(String)
   *
   * @see #getServiceIdentifier()
   */
  private final String serviceIdentifier;


  /*
   * Constructors.
   */


  /**
   * Creates a new {@link ServiceConfiguration}.
   */
  private ServiceConfiguration() {
    super();
    this.serviceIdentifier = null;
  }

  /**
   * Creates a new {@link ServiceConfiguration}.
   *
   * @param serviceIdentifier the identifier of the service this
   * {@link ServiceConfiguration} implementation will provide
   * configuration for; must not be {@code null}
   *
   * @exception NullPointerException if {@code serviceIdentifier} is
   * {@code null}
   *
   * @see #getServiceIdentifier()
   */
  protected ServiceConfiguration(final String serviceIdentifier) {
    super();
    this.serviceIdentifier = Objects.requireNonNull(serviceIdentifier);
  }


  /*
   * Instance methods.
   */


  /**
   * Returns the identifier of the service this {@link ServiceConfiguration}
   * implementation provides configuration for.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * @return the identifier of the service this {@link
   * ServiceConfiguration} implementation provides configuration for;
   * never {@code null}
   *
   * @see #ServiceConfiguration(String)
   */
  public final String getServiceIdentifier() {
    return this.serviceIdentifier;
  }

  /**
   * Returns an {@linkplain java.util.Collections#unmodifiableSet(Set)
   * unmodifiable} and unchanging {@link Set} of {@link String}s
   * representing the names of properties whose values may be
   * retrieved with the {@link #getProperty(String)} method.
   *
   * <p>Implementations of this method must not return {@code
   * null}.</p>
   *
   * <p>Implementations of this method must ensure that the {@link
   * Set} returned may be used without the end user having to peform
   * explicit synchronization.</p>
   *
   * <p>Implementations of this method may return the same {@link Set}
   * instance with each invocation, or different {@link Set} instances
   * with different contents.</p>
   *
   * @return an {@linkplain java.util.Collections#unmodifiableSet(Set)
   * unmodifiable} and unchanging {@link Set} of {@link String}s
   * representing the names of properties whose values may be
   * retrieved with the {@link #getProperty(String)} method
   */
  public abstract Set<String> getPropertyNames();

  /**
   * Returns a value for the property described by the supplied {@code
   * propertyName}, or {@code null} if no such property value exists.
   *
   * <p>This method may return {@code null}.</p>
   *
   * @param propertyName the name of the property whose value should
   * be returned; must not be {@code null}
   *
   * @return a value for the property described by the supplied {@code
   * propertyName}, or {@code null}
   *
   * @see #getProperty(String, String)
   */
  public final String getProperty(final String propertyName) {
    return this.getProperty(propertyName, null);
  }

  /**
   * Returns a value for the property described by the supplied {@code
   * propertyName}, or the value of the supplied {@code defaultValue}
   * parameter if no such property value exists.
   *
   * <p>Implementations of this method may return {@code null} if {@code
   * defaultValue} is {@code null}.</p>
   *
   * <p>Implementations of this method may return the same or
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
   */
  public abstract String getProperty(String propertyName, String defaultValue);


  /*
   * Static methods.
   */


  /**
   * Returns the sole {@link ServiceConfiguration} implementation in
   * effect for the {@linkplain System#getSystems() current
   * <code>System</code>s} and identified by the supplied {@code
   * serviceIdentifier}, if there is one, <strong>or {@code null} if
   * there is no such {@link ServiceConfiguration}</strong>.
   *
   * @param serviceIdentifier the {@linkplain #getServiceIdentifier()
   * service identifier} for which a {@link ServiceConfiguration}
   * should be sought; must not be {@code null}
   *
   * @return the sole {@link ServiceConfiguration} implementation in
   * effect for the {@linkplain System#getSystems() current
   * <code>System</code>s} and identified by the supplied {@code
   * serviceIdentifier}, if there is one, or {@code null}
   *
   * @exception NullPointerException if {@code serviceIdentifier} is
   * {@code null}
   *
   * @exception ServiceConfigurationError if there was a problem
   * {@linkplain ServiceLoader#load(Class) loading Java services}
   *
   * @see #getInstance(String, Properties)
   */
  public static final ServiceConfiguration getInstance(final String serviceIdentifier) {
    return getInstance(serviceIdentifier, null);
  }

  /**
   * Returns the sole {@link ServiceConfiguration} implementation in
   * effect for the {@linkplain System#getSystems() current
   * <code>System</code>s} and identified by the supplied {@code
   * serviceIdentifier} and suitable for the supplied {@code
   * coordinates}, if there is one, <strong>or {@code null} if there
   * is no such {@link ServiceConfiguration}.</strong>
   *
   * <p>This method may&mdash;and often does&mdash;return {@code
   * null}.</p>
   *
   * <p>While the current implementation of this method performs no
   * caching other than that performed as a side effect by {@link
   * ServiceLoader} method invocations, caching is deliberately not a
   * component of the specification of this method's behavior.</p>
   *
   * @param serviceIdentifier the {@linkplain #getServiceIdentifier()
   * service identifier} for which a {@link ServiceConfiguration}
   * should be sought; must not be {@code null}
   *
   * @param coordinates a {@link Properties} object containing
   * coordinates that might assist a {@link ServiceConfiguration}
   * implementation in implementing its {@link #getPropertyNames()}
   * and {@link #getProperty(String)} methods; may be {@code null}
   *
   * @return the sole {@link ServiceConfiguration} implementation in
   * effect for the {@linkplain System#getSystems() current
   * <code>System</code>s} and identified by the supplied {@code
   * serviceIdentifier}, if there is one, or {@code null}
   *
   * @exception NullPointerException if {@code serviceIdentifier} is
   * {@code null}
   *
   * @exception ServiceConfigurationError if there was a problem
   * {@linkplain ServiceLoader#load(Class) loading Java services}
   */
  public static final ServiceConfiguration getInstance(final String serviceIdentifier, final Properties coordinates) {
    Objects.requireNonNull(serviceIdentifier);
    ServiceConfiguration returnValue = null;
    Iterable<ServiceConfigurationProvider> serviceConfigurationProviders = ServiceConfiguration.serviceConfigurationProviders;
    if (serviceConfigurationProviders == null) {
      serviceConfigurationProviders = ServiceLoader.load(ServiceConfigurationProvider.class);
      assert serviceConfigurationProviders != null;
      ServiceConfiguration.serviceConfigurationProviders = serviceConfigurationProviders;
    }
    assert serviceConfigurationProviders != null;
    final Set<System> systems = System.getSystems();
    for (final ServiceConfigurationProvider serviceConfigurationProvider : serviceConfigurationProviders) {
      assert serviceConfigurationProvider != null;
      if (serviceIdentifier.equals(serviceConfigurationProvider.getServiceIdentifier())) {
        returnValue = serviceConfigurationProvider.buildFor(systems, coordinates);
        if (returnValue != null) {
          if (!serviceIdentifier.equals(returnValue.getServiceIdentifier())) {
            throw new IllegalStateException("!serviceIdentifier.equals(returnValue.getServiceIdentifier()): !\""
                                            + serviceIdentifier
                                            + "\".equals(\"" + returnValue.getServiceIdentifier() + "\")");
          }
          break;
        }
      }
    }
    return returnValue;
  }

}

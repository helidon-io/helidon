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

import java.util.Iterator;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

/**
 * An abstract factory of {@link ServiceConfiguration} instances.
 *
 * @see #buildFor(Set, Properties)
 *
 * @see ServiceConfiguration
 *
 * @deprecated This class is slated for removal.
 */
@Deprecated
public abstract class ServiceConfigurationProvider {


  /*
   * Instance fields.
   */


  /**
   * The identifier of the service this {@link
   * ServiceConfigurationProvider} implementation provides
   * configuration for.
   *
   * <p>This field is never {@code null}.</p>
   *
   * @see #ServiceConfigurationProvider(String)
   *
   * @see #getServiceIdentifier()
   */
  private final String serviceIdentifier;


  /*
   * Constructors.
   */


  /**
   * Creates a new {@link ServiceConfigurationProvider}.
   */
  private ServiceConfigurationProvider() {
    super();
    this.serviceIdentifier = null;
  }

  /**
   * Creates a new {@link ServiceConfigurationProvider}.
   *
   * @param serviceIdentifier the identifier of the service this
   * {@link ServiceConfigurationProvider} implementation will provide
   * configuration for; must not be {@code null}
   *
   * @exception NullPointerException if {@code serviceIdentifier} is
   * {@code null}
   *
   * @see #getServiceIdentifier()
   */
  protected ServiceConfigurationProvider(final String serviceIdentifier) {
    super();
    this.serviceIdentifier = Objects.requireNonNull(serviceIdentifier);
  }


  /*
   * Instance methods.
   */


  /**
   * Returns the identifier of the logical service this {@link
   * ServiceConfigurationProvider} implementation provides
   * {@link ServiceConfiguration} instances for.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * <p>Repeated invocations of this method will yield identical
   * return values.</p>
   *
   * @return the identifier of the logical service this {@link
   * ServiceConfigurationProvider} implementation provides {@link
   * ServiceConfiguration} instances for; never {@code null}
   *
   * @see #ServiceConfigurationProvider(String)
   */
  public final String getServiceIdentifier() {
    return this.serviceIdentifier;
  }

  /**
   * Given a {@link Set} of {@link System}s and an optional {@link
   * Properties} object representing <em>coordinates</em> identifying
   * a configuration space in which configuration discovery is to take
   * place, returns a new {@link ServiceConfiguration} appropriate for
   * the configuration space implied by the supplied parameters,
   * <strong>or {@code null} if no such {@link ServiceConfiguration}
   * is applicable</strong>.
   *
   * <p>Implementations of this method may&mdash;and often
   * will&mdash;return {@code null}.</p>
   *
   * <p>Multiple invocations of an implementation of this method must
   * result in distinct, though perhaps equal, {@link
   * ServiceConfiguration} instances.</p>
   *
   * <p>Implementations of this method may rely on the fact that all
   * members of the supplied {@link Set} of {@link System}s will be
   * {@linkplain System#isEnabled() enabled}.</p>
   *
   * <p>Implementations of this method must not call the {@link
   * ServiceConfiguration#getInstance(String, Properties)} method, as an
   * infinite loop may result.</p>
   *
   * @param systems a {@link Set} of {@link System}s found to be in
   * effect; may be {@code null}
   *
   * @param coordinates a {@link Properties} object containing hints
   * that may help in the implementation of the {@link
   * ServiceConfiguration} to be returned; may be {@code null}
   *
   * @return a {@link ServiceConfiguration} suitable for the
   * configuration space implied by the supplied parameters,
   * <strong>or {@code null} if no such {@link ServiceConfiguration}
   * is applicable</strong>
   *
   * @see ServiceConfiguration#getInstance(String, Properties)
   */
  public abstract ServiceConfiguration buildFor(Set<? extends System> systems, Properties coordinates);


  /*
   * Static methods.
   */


  /**
   * Given a {@link Set} of {@link System}s, returns the {@link
   * System} from that {@link Set} that is deemed to be the
   * authoritative system, either because it is the only {@linkplain
   * System#isEnabled() enabled} member of the {@link Set} or it is
   * the first {@linkplain System#isEnabled() enabled} member of the
   * {@link Set} whose {@link System#isAuthoritative()} method returns
   * {@code true}, <strong>or {@code null} if there is no
   * authoritative {@link System} to be returned</strong>.
   *
   * <p>This method may&mdash;and often does&mdash;return {@code
   * null}.</p>
   *
   * <p>A {@link System} is deemed to be the authoritative system if
   * it {@linkplain System#isEnabled is enabled} and either of the
   * following is true:</p>
   *
   * <ul>
   *
   * <li>It is the sole member of the supplied {@link Set} of {@link
   * System}s.</li>
   *
   * <li>It {@linkplain System#isAuthoritative() describes itself as
   * being authoritative}.</li>
   *
   * </ul>
   *
   * @param systems the {@link System}s to inspect; may be {@code
   * null}
   *
   * @param coordinates a {@link Properties} object containing hints
   * that may help in the implementation of this {@link
   * ServiceConfigurationProvider} not currently used but reserved for
   * future use; may be {@code null}.
   *
   * @return a {@link System} that is deemed to be authoritative,
   * <strong>or {@code null} if no such {@link System} could be
   * found</strong>
   */
  protected static final System getAuthoritativeSystem(final Set<? extends System> systems, final Properties coordinates) {
    System returnValue = null;
    if (systems != null && !systems.isEmpty()) {
      final Iterator<? extends System> iterator = systems.iterator();
      if (iterator != null && iterator.hasNext()) {
        returnValue = iterator.next();
        if (iterator.hasNext()) {
          assert systems.size() > 1;
          do {
            if (returnValue != null && returnValue.isEnabled() && returnValue.isAuthoritative()) {
              break;
            } else {
              returnValue = iterator.next();
            }
          } while (iterator.hasNext());
        } else if (returnValue != null && !returnValue.isEnabled()) {
          assert systems.size() == 1;
          returnValue = null;
        }
      }
    }
    return returnValue;
  }

}

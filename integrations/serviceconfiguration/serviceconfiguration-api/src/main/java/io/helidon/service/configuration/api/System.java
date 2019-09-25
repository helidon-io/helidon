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

import java.util.Collection; // for javadoc only
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.Set;

/**
 * A {@linkplain ServiceLoader service provider} that represents some
 * kind of <em>system</em> in which the current program is running.
 *
 * <p>The meaning of <em>system</em> is deliberately loose.  A system
 * might be a laptop, a platform-as-a-service such as the <a
 * href="https://cloud.oracle.com/en_US/acc">Oracle Application
 * Container Cloud Service</a> or <a
 * href="https://www.heroku.com/">Heroku</a>, a generic Linux-like
 * ecosystem running as part of a <a
 * href="https://kubernetes.io/">Kubernetes</a> cluster, and so on.</p>
 *
 * <p>{@link System} instances {@linkplain #getenv() provide access to
 * their environment} as well as to {@link #getProperties() their
 * properties}.</p>
 *
 * <p>In an arbitrary collection of {@link System} instances, zero or
 * one of them may be {@linkplain #isAuthoritative() marked as being
 * authoritative}.  An {@linkplain #isAuthoritative() authoritative
 * <code>System</code>} holds sway, and its {@linkplain
 * #getProperties() properties} and {@linkplain #getenv() environment
 * values} are to be preferred over all others.</p>
 *
 * @see #getSystems()
 *
 * @see #getenv()
 *
 * @see #getProperties()
 *
 * @see #isAuthoritative()
 *
 * @deprecated This class is slated for removal.
 */
@Deprecated
public abstract class System {


  /*
   * Instance fields.
   */


  /**
   * The name of this {@link System}.
   *
   * <p>This field will never be {@code null}.</p>
   *
   * @see #System(String, boolean)
   *
   * @see #getName()
   */
  private final String name;

  /**
   * Whether this {@link System} is authoritative.
   *
   * <p>A {@link System} that is authoritative is one whose
   * {@linkplain #getenv() environment} and {@linkplain
   * #getProperties() properties} are to be preferred over all
   * others that may be present.</p>
   *
   * @see #System(String, boolean)
   *
   * @see #isAuthoritative()
   */
  private final boolean authoritative;


  /*
   * Constructors.
   */


  /**
   * Creates a new {@link System}.
   *
   * @param name the name of the {@link System}; must not be {@code
   * null}
   *
   * @param authoritative whether the {@link System} will be
   * {@linkplain #isAuthoritative() authoritative}
   *
   * @exception NullPointerException if {@code name} is {@code null}
   *
   * @see #getName()
   *
   * @see #isAuthoritative()
   */
  protected System(final String name, final boolean authoritative) {
    super();
    this.name = Objects.requireNonNull(name);
    this.authoritative = authoritative;
  }


  /*
   * Instance methods.
   */


  /**
   * Returns the name of this {@link System}.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * <p>Multiple invocations of this method will return identical
   * {@link String} instances.</p>
   *
   * @return the name of this {@link System}; never {@code null}
   *
   * @see #System(String, boolean)
   */
  public final String getName() {
    return this.name;
  }

  /**
   * Returns {@code true} if this {@link System} is <em>enabled</em>.
   *
   * <p>If a {@link System} is enabled, then its {@linkplain #getenv()
   * environment values} and {@linkplain #getProperties() properties}
   * may be used.  If a {@link System} is not enabled, then usage of
   * its {@linkplain #getenv() environment values} and {@linkplain
   * #getProperties() properties} may result in undefined
   * behavior.</p>
   *
   * @return {@code true} if this {@link System} is enabled; {@code
   * false} otherwise
   *
   * @see #System(String, boolean)
   */
  public abstract boolean isEnabled();

  /**
   * Returns {@code true} if this {@link System} is
   * <em>authoritative</em>.
   *
   * <p>If a {@link System} is authoritative, then its {@linkplain
   * #getenv() environment values} and {@linkplain #getProperties()
   * properties} are to be preferred over any others that might be
   * present.</p>
   *
   * <p>In the presence of an authoritative {@link System}, usage of a
   * non-authoritative {@link System} may lead to undefined
   * behavior.</p>
   *
   * @return {@code true} if this {@link System} is authoritative;
   * {@code false} otherwise
   *
   * @see #System(String, boolean)
   */
  public boolean isAuthoritative() {
    return this.authoritative;
  }

  /**
   * Returns the <em>environment</em> of this {@link System} as a
   * non-{@code null}, unchanging and {@linkplain
   * Collections#unmodifiableMap(Map) unmodifiable <code>Map</code>}.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * <p>Overrides of this method must not return {@code null}.</p>
   *
   * <p>Overrides of this method must ensure that the {@link Map}
   * returned may be used without requiring the user to perform
   * synchronization.</p>
   *
   * <p>Multiple invocations of this method or any overridden variants
   * of it are not guaranteed to return equal or identical {@link Map}
   * instances.</p>
   *
   * <p>The default implementation of this method returns the result
   * of invoking {@link java.lang.System#getenv()}.</p>
   *
   * @return the <em>environment</em> of this {@link System} as a
   * non-{@code null}, unchanging and {@linkplain
   * Collections#unmodifiableMap(Map) unmodifiable <code>Map</code>};
   * never {@code null}
   *
   * @see #getProperties()
   *
   * @see java.lang.System#getenv()
   */
  public Map<String, String> getenv() {
    return java.lang.System.getenv();
  }

  /**
   * Returns the properties of this {@link System} as a non-{@code
   * null}, unchanging and unmodifiable {@link Properties} object.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * <p>Overrides of this method must not return {@code null}.</p>
   *
   * <p>Callers must not mutate the {@link Properties} object that is
   * returned.  Attempts to do so may result in an {@link
   * UnsupportedOperationException}.</p>
   *
   * <p>Multiple invocations of this method or any overridden variants
   * of it are not guaranteed to return equal or identical {@link
   * Properties} instances.</p>
   *
   * <p>The default implementation of this method returns the result
   * of invoking {@link java.lang.System#getProperties()}.</p>
   *
   * @return the properties of this {@link System} as a non-{@code
   * null}, unchanging and unmodifiable {@link Properties} object;
   * never {@code null}
   *
   * @see #getenv()
   *
   * @see java.lang.System#getProperties()
   */
  public Properties getProperties() {
    return java.lang.System.getProperties();
  }

  /**
   * Returns a hashcode for this {@link System} that varies with only
   * its {@linkplain #getName() name}, {@linkplain #isEnabled()
   * enablement} and {@linkplain #isAuthoritative() authority}.
   *
   * @return a hashcode for this {@link System}
   *
   * @see #equals(Object)
   */
  @Override
  public int hashCode() {
    int hashCode = 17;

    Object value = this.getName();
    int c = value == null ? 0 : value.hashCode();
    hashCode = 37 * hashCode + c;

    c = this.isEnabled() ? 1 : 0;
    hashCode = 37 * hashCode + c;

    c = this.isAuthoritative() ? 1 : 0;
    hashCode = 37 * hashCode + c;

    return hashCode;
  }

  /**
   * Returns {@code true} if this {@link System} is equal to the
   * supplied {@link Object}.
   *
   * <p>This {@link System} is deemed to be equal to an {@link Object}
   * passed to this method if the supplied {@link Object} is an
   * instance of {@link System} and {@linkplain #getName() has a name}
   * equal to the {@linkplain #getName() name} of this {@link System}
   * and {@linkplain #isEnabled() has an enabled status} equal to
   * {@linkplain #isEnabled() that of this <code>System</code>} and
   * {@linkplain #isAuthoritative() has an authoritative status} equal
   * to {@linkplain #isAuthoritative() that of this
   * <code>System</code>}.</p>
   *
   * @param other the {@link Object} to test; may be {@code null} in
   * which case {@code false} will be returned
   *
   * @return {@code true} if this {@link System} is equal to the
   * supplied {@link Object}; {@code false} otherwise
   *
   * @see #hashCode()
   */
  @Override
  public boolean equals(final Object other) {
    if (other == this) {
      return true;
    } else if (other instanceof System) {
      final System her = (System) other;

      final Object name = this.getName();
      if (name == null) {
        if (her.getName() != null) {
          return false;
        }
      } else if (!name.equals(her.getName())) {
        return false;
      }

      return this.isEnabled() && her.isEnabled() && this.isAuthoritative() && her.isAuthoritative();
    } else {
      return false;
    }
  }


  /*
   * Static methods.
   */


  /**
   * Returns a non-{@code null}, unchanging and {@linkplain
   * Collections#unmodifiableSet(Set) unmodifiable <code>Set</code>}
   * of {@link System} instances as found by the {@linkplain
   * ServiceLoader Java service provider mechanism}.
   *
   * <p>This method never returns {@code null} but may return an
   * {@linkplain Collection#isEmpty() empty <code>Set</code>}.</p>
   *
   * <p>If one of the {@link System} instances so discovered
   * {@linkplain #isAuthoritative() is authoritative}, then it will be
   * the only member of the returned {@link Set}.</p>
   *
   * <p>Multiple invocations of this method are not guaranteed to
   * return equal or identical {@link Set} instances.</p>
   *
   * @return a non-{@code null} {@linkplain
   * Collections#unmodifiableSet(Set) unmodifiable <code>Set</code>}
   * of {@link System} instances as found by the {@linkplain
   * ServiceLoader Java service provider mechanism}; never {@code
   * null}
   *
   * @see #isAuthoritative()
   *
   * @see ServiceLoader#load(Class)
   */
  public static final Set<System> getSystems() {
    final Iterable<System> systemsIterable = ServiceLoader.load(System.class);
    assert systemsIterable != null;
    Set<System> returnValue = null;
    for (final System system : systemsIterable) {
      assert system != null;
      if (system.isEnabled()) {
        if (system.isAuthoritative()) {
          returnValue = Collections.singleton(system);
          break;
        } else if (returnValue == null) {
          returnValue = new HashSet<>();
          returnValue.add(system);
        } else {
          returnValue.add(system);
          break;
        }
      }
    }
    if (returnValue == null) {
      returnValue = Collections.emptySet();
    } else {
      assert !returnValue.isEmpty();
      returnValue = Collections.unmodifiableSet(returnValue);
    }
    return returnValue;
  }

}

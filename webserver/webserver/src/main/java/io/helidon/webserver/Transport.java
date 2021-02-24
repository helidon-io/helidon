/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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

package io.helidon.webserver;

import java.util.Optional;

/**
 * A very abstract notion of a <em>transport</em> for a particular
 * implementation of the {@link WebServer} interface.
 *
 * <p>A {@link Transport} encompasses certain parts of the inner
 * workings of a given {@link WebServer} implementation that
 * simultaneously should not be exposed but need to be configurable.</p>
 *
 * <p>A {@link WebServer} implementation may call {@link
 * #createTransportArtifact(Class, String, ServerConfiguration)}
 * during startup, and then never again, passing non-{@code null}
 * arguments.  Only one call per combination of {@code artifactType}
 * and {@code artifactName} is permitted.</p>
 *
 * <p>It is not expected or required that implementations of this
 * interface be safe for concurrent use by multiple threads.</p>
 *
 * @see #isAvailableFor(WebServer)
 *
 * @see #createTransportArtifact(Class, String, ServerConfiguration)
 *
 * @see WebServer.Builder#transport(Transport)
 */
public interface Transport {

  /**
   * Returns {@code true} if this {@link Transport} implementation is
   * available for use by the given {@link WebServer} implementation;
   * {@code false} otherwise.
   *
   * <p>Implementations of this method must be idempotent and
   * deterministic.</p>
   *
   * <p>{@link WebServer} implementations normally call this method,
   * supplying themselves, and interpret its return value before
   * performing any invocation of {@link
   * #createTransportArtifact(Class, String,
   * ServerConfiguration)}.</p>
   *
   * <p>If a given {@link Transport} implementation returns {@code
   * false} from its implementation of this method, then a given
   * {@link WebServer} implementation is permitted to discard it and
   * use another {@link Transport} implementation instead.</p>
   *
   * <p>The {@link WebServer} passed to this method is in a
   * deliberately undefined state.  Most notably, it has not yet been
   * configured, and it is in the process of being constructed.  Most
   * {@link Transport} implementations should either not use this
   * parameter at all or, for example, should only test it to see if
   * it is an implementation of {@link WebServer} that they
   * support.</p>
   *
   * @param webserver the {@link WebServer} implementation in a
   * deliberately undefined state and currently being constructed
   *
   * @return {@code true} if this {@link Transport} implementation is
   * available for use; {@code false} otherwise
   */
  boolean isAvailableFor(WebServer webserver);

  /**
   * Creates and returns a suitable <em>transport artifact</em>, if
   * one can be created or is available, and an {@linkplain
   * Optional#empty() empty <code>Optional</code>} otherwise.
   *
   * <p>A <em>transport artifact</em> is a deliberately opaque item
   * that is defined by a particular {@link WebServer} implementation
   * that is needed at startup time.  A transport artifact has a
   * {@link String}-typed <em>name</em> and a {@link Class}-typed
   * <em>type</em>.  A {@link WebServer} implementation may call this
   * method only after having received {@code true} from an invocation
   * of {@link #isAvailableFor(WebServer)}.</p>
   *
   * <p>A {@link WebServer} implementation must not call this method
   * more than once per combination of {@code type} and {@code name}
   * or undefined behavior will result.</p>
   *
   * <p>No further specifications of any kind are made regarding the
   * behavior of any implementation of this method.  Specifically, an
   * implementation may create a new artifact, or return a cached
   * one.</p>
   *
   * <p>Implementations of this method are called while a {@link
   * WebServer} implementation is in the process of being constructed
   * and while it is in a deliberately undefined state.</p>
   *
   * @param <T> the type of the transport artifact
   *
   * @param type a {@link Class} that can help to identify the kind of
   * artifact to be returned
   *
   * @param name the {@link WebServer}-implementation-specific name of
   * a specific transport artifact of the given type that should be
   * returned
   *
   * @param config the finalized {@link ServerConfiguration} in effect
   * during construction of the {@link WebServer} implementation
   *
   * @return an {@link Optional}, possibly {@linkplain
   * Optional#empty() empty}, representing the desired transport
   * artifact
   */
  <T> Optional<T> createTransportArtifact(Class<T> type,
                                          String name,
                                          ServerConfiguration config);

}

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
package io.helidon.service.configuration.localhost;

import io.helidon.service.configuration.api.System;

/**
 * A {@linkplain System#isAuthoritative() non-authoritative} {@link
 * System} implementation describing the current local host.
 *
 * @author <a href="mailto:laird.nelson@oracle.com">Laird Nelson</a>
 *
 * @see System
 */
public final class LocalhostSystem extends System {


  /*
   * Constructors.
   */


  /**
   * Creates a new {@link LocalhostSystem} {@linkplain #getName()
   * named} {@code localhost} that {@linkplain #isEnabled() is
   * enabled} and {@linkplain #isAuthoritative() not authoritative}.
   *
   * @see #isEnabled()
   *
   * @see #isAuthoritative()
   *
   * @see System
   */
  public LocalhostSystem() {
    super("localhost", false);
  }


  /*
   * Instance methods.
   */


  /**
   * Returns {@code true} when invoked.
   *
   * @return {@code true} when invoked
   *
   * @see System#isEnabled()
   */
  @Override
  public boolean isEnabled() {
    return true;
  }

}

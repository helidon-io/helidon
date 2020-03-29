/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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
package io.helidon.integrations.cdi.hibernate;

import javax.enterprise.inject.spi.CDI;

import org.hibernate.engine.transaction.jta.platform.spi.JtaPlatform;
import org.hibernate.engine.transaction.jta.platform.spi.JtaPlatformProvider;

/**
 * A {@link JtaPlatformProvider} that uses a {@link CDI} instance to
 * {@linkplain CDI#select(Class, Annotation...) provide} a {@link
 * JtaPlatform}.
 *
 * <p>Normally this class is instantiated by the {@linkplain
 * java.util.ServiceLoader Java service provider infrastructure}.</p>
 *
 * @see JtaPlatform
 *
 * @see CDISEJtaPlatform
 */
public final class CDISEJtaPlatformProvider implements JtaPlatformProvider {
  /**
   * Creates a new {@link CDISEJtaPlatformProvider}.
   */
  public CDISEJtaPlatformProvider() {
    super();
  }

  /**
   * Returns a non-{@code null} {@link JtaPlatform}.
   *
   * @return a non-{@code null} {@link JtaPlatform}
   */
  @Override
  public JtaPlatform getProvidedJtaPlatform() {
    return CDI.current().select(JtaPlatform.class).get();
  }

}

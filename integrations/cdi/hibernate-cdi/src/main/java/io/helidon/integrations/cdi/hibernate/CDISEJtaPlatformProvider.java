/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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

import java.lang.System.Logger;

import jakarta.enterprise.inject.spi.CDI;
import org.hibernate.engine.transaction.jta.platform.spi.JtaPlatform;
import org.hibernate.engine.transaction.jta.platform.spi.JtaPlatformProvider;

import static java.lang.System.Logger.Level.WARNING;

/**
 * A {@link JtaPlatformProvider} that uses a {@link CDI} instance to {@linkplain CDI#select(Class,
 * java.lang.annotation.Annotation...) provide} a {@link JtaPlatform}.
 *
 * <p>Normally this class is instantiated by the {@linkplain java.util.ServiceLoader Java service provider
 * infrastructure}.</p>
 *
 * @see JtaPlatform
 *
 * @see CDISEJtaPlatform
 */
public final class CDISEJtaPlatformProvider implements JtaPlatformProvider {


    /*
     * Static fields.
     */


    /**
     * A {@link Logger} for instances of this class.
     */
    private static final Logger LOGGER = System.getLogger(CDISEJtaPlatformProvider.class.getName());


    /*
     * Constructors.
     */


    /**
     * Creates a new {@link CDISEJtaPlatformProvider}.
     *
     * @deprecated For Hibernate use only.
     */
    @Deprecated
    public CDISEJtaPlatformProvider() {
        super();
    }


    /*
     * Instance methods.
     */


    /**
     * Returns a {@link JtaPlatform}.
     *
     * <p>If CDI is not available, this method returns {@code null} <a
     * href="https://github.com/hibernate/hibernate-orm/blob/6.3.1/hibernate-core/src/main/java/org/hibernate/engine/transaction/jta/platform/internal/StandardJtaPlatformResolver.java#L35-L37">as
     * permitted by Hibernate in violation of its own contract documentation</a>.</p>
     *
     * @return a {@link JtaPlatform}, or {@code null}
     *
     * @deprecated For Hibernate use only.
     */
    @Deprecated
    @Override
    public JtaPlatform getProvidedJtaPlatform() {
        CDI<Object> cdi;
        try {
            cdi = CDI.current();
        } catch (IllegalStateException e) {
            if (LOGGER.isLoggable(WARNING)) {
                LOGGER.log(WARNING, "CDI is not available.", e);
            }
            return null;
        }
        return cdi.select(JtaPlatform.class).get();
    }

}

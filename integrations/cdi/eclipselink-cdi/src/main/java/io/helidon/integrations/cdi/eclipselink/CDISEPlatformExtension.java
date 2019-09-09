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
package io.helidon.integrations.cdi.eclipselink;

import java.lang.reflect.Field;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.enterprise.inject.spi.Extension;

import org.eclipse.persistence.platform.server.NoServerPlatform;
import org.eclipse.persistence.platform.server.ServerPlatformUtils;

/**
 * An {@link Extension} that sets {@link CDISEPlatform} as the default
 * server platform in EclipseLink.
 *
 * <p>This class is {@code public} only to conform to the new
 * accessibility requirements of service provider classes in JDKs
 * whose versions are higher than 1.8.  There is no need for end users
 * to instantiate this class.  Any public APIs exposed by this class
 * are subject to change without prior notice.</p>
 *
 * @deprecated Only a CDI container should instantiate this class.
 */
@Deprecated
public final class CDISEPlatformExtension implements Extension {


    /*
     * Constructors.
     */


    /**
     * Creates a new {@link CDISEPlatformExtension}.
     *
     * @deprecated Only a CDI container should invoke this constructor.
     */
    @Deprecated
    public CDISEPlatformExtension() {
        super();
        final Object serverPlatformClassName = ServerPlatformUtils.detectServerPlatform(null);
        if (serverPlatformClassName == null
            || serverPlatformClassName.equals("UNKNOWN")
            || serverPlatformClassName.equals(NoServerPlatform.class.getName())) {
            try {
                final Field serverPlatformClassField = ServerPlatformUtils.class.getDeclaredField("SERVER_PLATFORM_CLS");
                serverPlatformClassField.setAccessible(true);
                serverPlatformClassField.set(null, CDISEPlatform.class.getName());
            } catch (final ReflectiveOperationException | RuntimeException exception) {
                final String cn = this.getClass().getName();
                final Logger logger = Logger.getLogger(cn);
                if (logger.isLoggable(Level.FINE)) {
                    logger.logp(Level.FINE, cn, "<init>", "Installation of "
                                + CDISEPlatform.class.getName()
                                + " as the default ServerPlatform failed",
                                exception);
                }
            }
        }
    }

}

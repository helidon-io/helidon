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
package io.helidon.service.configuration.accs;

import java.util.Map;

/**
 * A {@link io.helidon.service.configuration.api.System}
 * implementation that represents the <a
 * href="https://docs.oracle.com/en/cloud/paas/app-container-cloud/csjse/getting-started-oracle-application-container-cloud-service.html">Oracle
 * Application Container Cloud Service</a> system.
 *
 * <p>This {@link io.helidon.service.configuration.api.System} is
 * {@linkplain #isEnabled() enabled} when {@linkplain #getenv() its
 * environment} {@linkplain Map#containsKey(Object) contains the
 * <code>String</code> key} {@code ORA_APP_NAME}.</p>
 *
 * @see #isEnabled()
 *
 * @see io.helidon.service.configuration.api.System
 *
 * @deprecated This class is slated for removal.
 */
@Deprecated
public final class ACCSSystem extends io.helidon.service.configuration.api.System {


    /*
     * Constructors.
     */


    /**
     * Creates a new {@link ACCSSystem} whose {@link #getName() name} is
     * {@code accs} and whose {@linkplain #isAuthoritative()
     * authoritative status} is {@code true}.
     *
     * @see #isAuthoritative()
     */
    public ACCSSystem() {
        super("accs", true);
    }


    /*
     * Instance methods.
     */


    /**
     * Returns {@code true} if this {@link ACCSSystem}'s {@linkplain
     * #getenv() environment} {@linkplain Map#containsKey(Object)
     * contains the <code>String</code> key} {@code ORA_APP_NAME}.
     *
     * @return {@code true} if this {@link ACCSSystem} is enabled;
     * {@code false} otherwise
     *
     * @see <a
     * href="https://docs.oracle.com/en/cloud/paas/app-container-cloud/csjse/exploring-application-deployments-page.html#GUID-843F7013-B6FA-45E0-A9D3-29A0EFD53E11">Configuring
     * Environment Variables in the Oracle Application Container Cloud
     * Service documentation</a>
     *
     * @see io.helidon.service.configuration.api.System#isEnabled()
     */
    @Override
    public boolean isEnabled() {
        final Map<?, ?> env = this.getenv();
        return env != null && env.containsKey("ORA_APP_NAME");
    }

}

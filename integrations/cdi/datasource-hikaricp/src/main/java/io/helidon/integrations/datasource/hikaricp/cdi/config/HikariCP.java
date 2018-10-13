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
package io.helidon.integrations.datasource.hikaricp.cdi.config;

import io.helidon.service.configuration.api.ServiceConfiguration;
import io.helidon.service.configuration.microprofile.config.ServiceConfigurationConfigSource;

/**
 * A {@link ServiceConfigurationConfigSource} that sits atop the
 * {@code hikaricp} {@link ServiceConfiguration} in effect (if there
 * is one).
 *
 * @author <a href="mailto:laird.nelson@oracle.com">Laird Nelson</a>
 */
public final class HikariCP extends ServiceConfigurationConfigSource {

    /**
     * Creates a new {@link HikariCP}.
     */
    public HikariCP() {
        super();
    }

}

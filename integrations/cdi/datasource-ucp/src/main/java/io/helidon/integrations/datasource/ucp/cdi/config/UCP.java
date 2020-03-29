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
package io.helidon.integrations.datasource.ucp.cdi.config;

/**
 * A {@link
 * io.helidon.service.configuration.microprofile.config.ServiceConfigurationConfigSource}
 * that sits atop the {@code ucp} {@link
 * io.helidon.service.configuration.api.ServiceConfiguration} in
 * effect (if there is one).
 *
 * @deprecated This class is slated for removal.
 */
@Deprecated
public final class UCP extends io.helidon.service.configuration.microprofile.config.ServiceConfigurationConfigSource {

    /**
     * Creates a new {@link UCP}.
     */
    public UCP() {
        super();
    }

}

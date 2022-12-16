/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.security.providers.oidc.common.spi;

import java.util.Optional;

import io.helidon.security.ProviderRequest;

/**
 * Finder of the tenant if from the request.
 */
public interface TenantIdFinder {
    /**
     * Identify a tenant from the request.
     *
     * @param providerRequest request of the security provider with access to headers
     *                       (see {@link ProviderRequest#env()}), and other information about the request
     * @return the identified tenant id, or empty option if tenant id cannot be identified from the request
     */
    Optional<String> tenantId(ProviderRequest providerRequest);
}

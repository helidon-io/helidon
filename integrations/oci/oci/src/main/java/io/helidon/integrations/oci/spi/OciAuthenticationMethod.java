/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.integrations.oci.spi;

import java.util.Optional;

import io.helidon.service.registry.Service;

import com.oracle.bmc.auth.AbstractAuthenticationDetailsProvider;

/**
 * An OCI Authentication Details Provider service contract.
 * <p>
 * This service is implemented by:
 * <ul>
 *     <li>Config based method</li>
 *     <li>Config file based method</li>
 *     <li>Resource principal method</li>
 *     <li>Instance principal method</li>
 * </ul>
 * The first one that provides an instance will be used as the value.
 * To customize, create your own service with a default or higher weight.
 */
@Service.Contract
public interface OciAuthenticationMethod {
    /**
     * The OCI authentication method name, can be used to explicitly select a method using configuration.
     *
     * @return OCI authentication method name
     */
    String method();

    /**
     * Provide an instance of the {@link com.oracle.bmc.auth.AbstractAuthenticationDetailsProvider} to be used
     * by other services.
     *
     * @return authentication details provider, or empty if nothing can be provided
     */
    Optional<AbstractAuthenticationDetailsProvider> provider();
}

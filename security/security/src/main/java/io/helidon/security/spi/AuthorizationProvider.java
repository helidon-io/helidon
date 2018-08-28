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

package io.helidon.security.spi;

import java.util.concurrent.CompletionStage;

import io.helidon.security.AuthorizationResponse;
import io.helidon.security.ProviderRequest;
import io.helidon.security.Role;
import io.helidon.security.Security;
import io.helidon.security.Subject;

/**
 * Authorization security provider. Validates the request and decides whether it
 * should continue processing.
 *
 * @see #supportedAnnotations()
 * @see #supportedCustomObjects()
 * @see #supportedConfigKeys()
 */
@FunctionalInterface
public interface AuthorizationProvider extends SecurityProvider {
    /**
     * Authorize a request based on configuration.
     *
     * Authorization cannot be optional. If this method is called, it should always attempt to authorize the current request.
     * This method will be invoked for inbound requests ONLY.
     *
     * @param context context of this security enforcement/validation
     * @return response that either permits, denies or abstains from decision
     * @see AuthorizationResponse#permit()
     */
    CompletionStage<AuthorizationResponse> authorize(ProviderRequest context);

    /**
     * Return true if current user is in the specified role.
     * Only providers that support role based access should implement this method.
     * For others it checks the subject for the presence of {@link Role} grant of the specified name.
     *
     * This method is defined to conform with one of the most commonly spread authorization concept, as it is required
     * for frameworks such as Servlet and JAX-RS.
     *
     * @param subject current subject
     * @param role    role name
     * @return true if current user is in this role
     */
    default boolean isUserInRole(Subject subject, String role) {
        return Security.getRoles(subject).contains(role);
    }
}

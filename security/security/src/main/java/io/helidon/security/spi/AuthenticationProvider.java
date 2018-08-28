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

import io.helidon.security.AuthenticationResponse;
import io.helidon.security.Principal;
import io.helidon.security.ProviderRequest;
import io.helidon.security.SecurityContext;
import io.helidon.security.Subject;

/**
 * Authentication security provider. Expected to authenticate the request and provide
 * a Subject to the security component, that would be available through {@link SecurityContext}.
 *
 * @see #supportedAnnotations()
 * @see #supportedCustomObjects()
 * @see #supportedConfigKeys()
 */
@FunctionalInterface
public interface AuthenticationProvider extends SecurityProvider {
    /**
     * Authenticate a request.
     * This may be just resolving headers (tokens) or full authentication (basic auth).
     * Do not throw exception for normal processing (e.g. invalid credentials; you may throw an exception in case of
     * misconfiguration).
     *
     * This method will be invoked for inbound requests ONLY.
     *
     * <p>
     * This method must provide either a {@link Principal} or a whole
     * {@link Subject} either for a user or for service (or both).
     *
     * @param providerRequest context of this security enforcement/validation
     * @return response that either authenticates the request, fails authentication or abstains from authentication
     * @see AuthenticationResponse#success(Subject)
     */
    CompletionStage<AuthenticationResponse> authenticate(ProviderRequest providerRequest);
}

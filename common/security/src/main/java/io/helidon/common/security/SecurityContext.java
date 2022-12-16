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

package io.helidon.common.security;

import java.security.Principal;
import java.util.Optional;

/**
 * Security context.
 * Can be obtained either from context, or from APIs of Helidon components.
 *
 * @param <P> type of principal used by the implementation, this common interface uses Java security Principal
 */
public interface SecurityContext<P extends Principal> {
    /**
     * Return true if the user is authenticated.
     * This only cares about USER! not about service. To check if service is authenticated, use
     * {@link #servicePrincipal()} and check the resulting optional.
     *
     * @return {@code true} for authenticated user
     */
    boolean isAuthenticated();

    /**
     * Return true if authorization was handled for current context.
     *
     * @return {@code true} for authorized requests
     */
    boolean isAuthorized();

    /**
     * User principal if user is authenticated.
     *
     * @return current context user principal, or empty if none authenticated
     */
    Optional<P> userPrincipal();

    /**
     * Service principal if service is authenticated.
     *
     * @return current context service principal, or empty if none authenticated
     */
    Optional<P> servicePrincipal();
}

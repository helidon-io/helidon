/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.webserver.http;

import io.helidon.http.ForbiddenException;
import io.helidon.http.UnauthorizedException;

/**
 * WebServer security.
 */
public interface HttpSecurity {
    /**
     * Create a default implementation of server security.
     *
     * @return a new server security that is not backed by any configuration or providers.
     */
    static HttpSecurity create() {
        return new DefaultServerSecurity();
    }

    /**
     * Authenticates the current request according to security configuration.
     * When there is no security implementation present, and required hint is set to {@code false} this is a no-op.
     *
     * @param request      server request to read data for authentication
     * @param response     server response
     * @param requiredHint whether authentication is expected
     * @return whether you should continue with other tasks in this request, if {@code false} is returned, the response
     *         was already sent, and you should immediately return without modifying it
     * @throws io.helidon.http.UnauthorizedException when authentication was expected but could not be resolved
     */
    boolean authenticate(ServerRequest request, ServerResponse response, boolean requiredHint) throws UnauthorizedException;

    /**
     * Authorize the current request according to security configuration.
     * When there is no security implementation present and there are no roles defined, this is a no-op; if roles are defined
     * this method throws {@link io.helidon.http.ForbiddenException} by default.
     *
     * @param request  server request to read data for authorization
     * @param response server response
     * @param roleHint the use should have at least one of the roles specified (only used when the security is configured
     *                 to support roles)
     * @return whether you should continue with other tasks in this request, if {@code false} is returned, the response
     *         was already sent, and you should immediately return without modifying it
     * @throws io.helidon.http.ForbiddenException when authorization failed and this request cannot proceed
     */
    boolean authorize(ServerRequest request, ServerResponse response, String... roleHint) throws ForbiddenException;
}

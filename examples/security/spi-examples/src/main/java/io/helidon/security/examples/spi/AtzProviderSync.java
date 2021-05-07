/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

package io.helidon.security.examples.spi;

import io.helidon.security.AuthorizationResponse;
import io.helidon.security.ProviderRequest;
import io.helidon.security.spi.AuthorizationProvider;
import io.helidon.security.spi.SynchronousProvider;

/**
 * Authorization provider example. The most simplistic approach.
 *
 * @see AtnProviderSync on how to use custom objects, config and annotations in a provider
 */
public class AtzProviderSync extends SynchronousProvider implements AuthorizationProvider {
    @Override
    protected AuthorizationResponse syncAuthorize(ProviderRequest providerRequest) {
        // just check the path contains the string "public", otherwise allow only if user is logged in
        // if no path is defined, abstain (e.g. I do not care about such requests - I can neither allow or deny them)
        return providerRequest.env().path()
                .map(path -> {
                    if (path.contains("public")) {
                        return AuthorizationResponse.permit();
                    }
                    if (providerRequest.securityContext().isAuthenticated()) {
                        return AuthorizationResponse.permit();
                    } else {
                        return AuthorizationResponse.deny();
                    }
                }).orElse(AuthorizationResponse.abstain());
    }
}

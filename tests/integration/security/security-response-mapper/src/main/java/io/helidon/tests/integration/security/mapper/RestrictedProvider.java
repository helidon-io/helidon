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

package io.helidon.tests.integration.security.mapper;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.List;

import io.helidon.common.context.Contexts;
import io.helidon.security.AuthenticationResponse;
import io.helidon.security.ProviderRequest;
import io.helidon.security.spi.AuthenticationProvider;
import io.helidon.security.spi.SynchronousProvider;

public class RestrictedProvider extends SynchronousProvider implements AuthenticationProvider {

    /**
     * Register an entry in {@link io.helidon.common.context.Context} and fail authentication.
     *
     * @param providerRequest provider request
     * @return authentication response
     */
    @Override
    protected AuthenticationResponse syncAuthenticate(ProviderRequest providerRequest) {
        // Use context to communicate with MySecurityResponseMapper
        Contexts.context()
                .ifPresent(c -> c.register(RestrictedProvider.class, getClass().getSimpleName()));
        return AuthenticationResponse.failed("Oops");

    }

    @Override
    public Collection<Class<? extends Annotation>> supportedAnnotations() {
        return List.of(Restricted.class);
    }
}

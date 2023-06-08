/*
 * Copyright (c) 2018, 2023 Oracle and/or its affiliates.
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

package io.helidon.security;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import io.helidon.security.spi.AuthorizationProvider;
import io.helidon.security.spi.ProviderConfig;

/**
 * A provider building a single authentication result from one or more authentication providers.
 */
final class CompositeAuthorizationProvider implements AuthorizationProvider {
    private final List<Atz> providers = new LinkedList<>();

    CompositeAuthorizationProvider(List<Atz> parts) {
        providers.addAll(parts);
    }

    @Override
    public Collection<Class<? extends Annotation>> supportedAnnotations() {
        Set<Class<? extends Annotation>> result = new HashSet<>();
        providers.forEach(atzConfig -> result.addAll(atzConfig.provider.supportedAnnotations()));
        return result;
    }

    @Override
    public Collection<String> supportedConfigKeys() {
        Set<String> configKeys = new HashSet<>();
        providers.forEach(atzConfig -> configKeys.addAll(atzConfig.provider.supportedConfigKeys()));
        return configKeys;
    }

    @Override
    public Collection<Class<? extends ProviderConfig>> supportedCustomObjects() {
        Set<Class<? extends ProviderConfig>> result = new HashSet<>();
        providers.forEach(atzConfig -> result.addAll(atzConfig.provider.supportedCustomObjects()));
        return result;
    }

    @Override
    public Collection<String> supportedAttributes() {
        Set<String> result = new HashSet<>();
        providers.forEach(atzConfig -> result.addAll(atzConfig.provider.supportedAttributes()));
        return result;
    }

    @Override
    public AuthorizationResponse authorize(ProviderRequest context) {
        AuthorizationResponse previous = AuthorizationResponse.abstain();

        try {
            for (Atz providerConfig : providers) {
                AuthorizationResponse thisResponse = providerConfig.provider.authorize(context);
                previous = processProvider(providerConfig, previous, thisResponse);
            }
        } catch (Exception exception) {
            Throwable cause = exception.getCause();
            if (null == cause) {
                cause = exception;
            }
            if (cause instanceof AsyncAtzException) {
                return ((AsyncAtzException) cause).response;
            }
            return AuthorizationResponse.builder()
                    .status(SecurityResponse.SecurityStatus.FAILURE)
                    .description("Failed processing: " + exception.getMessage())
                    .throwable(exception)
                    .build();
        }

        if (previous.status() == SecurityResponse.SecurityStatus.ABSTAIN) {
            return AuthorizationResponse.abstain();
        }
        return previous;
    }

    private AuthorizationResponse processProvider(Atz providerConfig,
                                                  AuthorizationResponse prevResponse, AuthorizationResponse thisResponse) {
        CompositeProviderFlag flag = providerConfig.config.flag();

        if (!flag.isValid(thisResponse.status())) {
            // not a valid response for this provider, terminate sequence
            // if the response is other than fail, create a new fail
            switch (thisResponse.status()) {
            case SUCCESS:
            case SUCCESS_FINISH:
            case ABSTAIN:
                AuthorizationResponse.Builder builder = AuthorizationResponse.builder();
                builder.status(SecurityResponse.SecurityStatus.FAILURE);
                builder.description("Composite flag forbids this response: "
                                            + thisResponse.status());
                thisResponse.description().map(builder::description);
                thisResponse.throwable().map(builder::throwable);
                throw new AsyncAtzException(builder.build());
            case FAILURE:
            case FAILURE_FINISH:
            default:
                throw new AsyncAtzException(thisResponse);
            }
        }

        if ((flag == CompositeProviderFlag.SUFFICIENT) && (
                thisResponse.status() == SecurityResponse.SecurityStatus.SUCCESS)) {
            // if flag is sufficient and we have success, finish processing
            throw new AsyncAtzException(thisResponse);
        }

        if (prevResponse.status() == SecurityResponse.SecurityStatus.ABSTAIN) {
            // previous was abstain, no need to modify anything, just return
            // "better" result
            return thisResponse.status().isSuccess() ? thisResponse : prevResponse;
        }

        // this request failed, return previous without modification
        if (!thisResponse.status().isSuccess()) {
            return prevResponse;
        }

        return thisResponse;
    }

    private static final class AsyncAtzException extends RuntimeException {
        private AuthorizationResponse response;

        private AsyncAtzException(AuthorizationResponse response) {
            this.response = response;
        }
    }

    static class Atz {
        private final CompositeProviderSelectionPolicy.FlaggedProvider config;
        private final AuthorizationProvider provider;

        Atz(CompositeProviderSelectionPolicy.FlaggedProvider config, AuthorizationProvider provider) {
            this.config = config;
            this.provider = provider;
        }
    }

}

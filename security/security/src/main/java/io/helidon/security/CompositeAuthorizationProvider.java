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

package io.helidon.security;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

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
    public CompletionStage<AuthorizationResponse> authorize(ProviderRequest context) {
        CompletionStage<AuthorizationResponse> previous = CompletableFuture.completedFuture(AuthorizationResponse.abstain());

        for (Atz providerConfig : providers) {
            previous = previous.thenCombine(providerConfig.provider.authorize(context),
                                            (prevResponse, thisResponse) -> processProvider(providerConfig,
                                                                                            prevResponse,
                                                                                            thisResponse));
        }

        return previous.exceptionally(throwable -> {
            Throwable cause = throwable.getCause();
            if (null == cause) {
                cause = throwable;
            }
            if (cause instanceof AsyncAtzException) {
                return ((AsyncAtzException) cause).response;
            }
            return AuthorizationResponse.builder()
                    .status(SecurityResponse.SecurityStatus.FAILURE)
                    .description("Failed processing: " + throwable.getMessage())
                    .throwable(throwable)
                    .build();
        }).thenApply(atzResponse -> {
            if (atzResponse.getStatus() == SecurityResponse.SecurityStatus.ABSTAIN) {
                // TODO how to resolve optional - too many places to configure it
                //                if (context.getSecurityContext().getEndpointConfig().isOptional()) {
                //                    return AuthorizationResponse.permit();
                //                } else {
                //                    return AuthorizationResponse.abstain();
                //                }
                return AuthorizationResponse.abstain();
            }
            return atzResponse;
        });
    }

    private AuthorizationResponse processProvider(Atz providerConfig,
                                                  AuthorizationResponse prevResponse, AuthorizationResponse thisResponse) {
        CompositeProviderFlag flag = providerConfig.config.getFlag();

        if (!flag.isValid(thisResponse.getStatus())) {
            // not a valid response for this provider, terminate sequence
            // if the response is other than fail, create a new fail
            switch (thisResponse.getStatus()) {
            case SUCCESS:
            case SUCCESS_FINISH:
            case ABSTAIN:
                AuthorizationResponse.Builder builder = AuthorizationResponse.builder();
                builder.status(SecurityResponse.SecurityStatus.FAILURE);
                builder.description("Composite flag forbids this response: "
                                            + thisResponse.getStatus());
                thisResponse.getDescription().map(builder::description);
                thisResponse.getThrowable().map(builder::throwable);
                throw new AsyncAtzException(builder.build());
            case FAILURE:
            case FAILURE_FINISH:
            default:
                throw new AsyncAtzException(thisResponse);
            }
        }

        if ((flag == CompositeProviderFlag.SUFFICIENT) && (
                thisResponse.getStatus() == SecurityResponse.SecurityStatus.SUCCESS)) {
            // if flag is sufficient and we have success, finish processing
            throw new AsyncAtzException(thisResponse);
        }

        if (prevResponse.getStatus() == SecurityResponse.SecurityStatus.ABSTAIN) {
            // previous was abstain, no need to modify anything, just return
            // "better" result
            return thisResponse.getStatus().isSuccess() ? thisResponse : prevResponse;
        }

        // this request failed, return previous without modification
        if (!thisResponse.getStatus().isSuccess()) {
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

/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import io.helidon.security.spi.AuthenticationProvider;
import io.helidon.security.spi.ProviderConfig;

import static io.helidon.security.CompositeProviderFlag.SUFFICIENT;
import static io.helidon.security.SecurityResponse.SecurityStatus.ABSTAIN;
import static io.helidon.security.SecurityResponse.SecurityStatus.SUCCESS;

/**
 * A provider building a single authentication result from one or more authentication providers.
 */
final class CompositeAuthenticationProvider implements AuthenticationProvider {
    private static final AuthenticationResponse ABSTAIN_RESPONSE = AuthenticationResponse.abstain();

    private final List<Atn> providers = new LinkedList<>();

    CompositeAuthenticationProvider(List<Atn> parts) {
        providers.addAll(parts);
    }

    @Override
    public Collection<Class<? extends Annotation>> supportedAnnotations() {
        Set<Class<? extends Annotation>> result = new HashSet<>();
        providers.forEach(atnConfig -> result.addAll(atnConfig.provider.supportedAnnotations()));
        return result;
    }

    @Override
    public Collection<String> supportedConfigKeys() {
        Set<String> configKeys = new HashSet<>();
        providers.forEach(atnConfig -> configKeys.addAll(atnConfig.provider.supportedConfigKeys()));
        return configKeys;
    }

    @Override
    public Collection<Class<? extends ProviderConfig>> supportedCustomObjects() {
        Set<Class<? extends ProviderConfig>> result = new HashSet<>();
        providers.forEach(atnConfig -> result.addAll(atnConfig.provider.supportedCustomObjects()));
        return result;
    }

    @Override
    public Collection<String> supportedAttributes() {
        Set<String> result = new HashSet<>();
        providers.forEach(atnConfig -> result.addAll(atnConfig.provider.supportedAttributes()));
        return result;
    }

    @Override
    public CompletionStage<AuthenticationResponse> authenticate(ProviderRequest providerRequest) {
        CompletionStage<AtnResponse> result = CompletableFuture.completedFuture(new AtnResponse(ABSTAIN_RESPONSE));

        for (Atn providerConfig : providers) {
            // go through all providers and validate each response, collecting successes
            result = result.thenCompose(theResponse -> invokeProvider(theResponse, providerConfig, providerRequest));
        }

        return result.thenApply(atnResponse -> {
            // when we get here, we should have all the successes and the response is the last one

            List<AuthenticationResponse> successes = atnResponse.successResponses;
            if (successes.isEmpty()) {
                // no success - abstain
                // if this was not a valid result of the authentication, we would have
                // thrown an exception in invokeProvider();
                return ABSTAIN_RESPONSE;
            }

            AuthenticationResponse.Builder responseBuilder = AuthenticationResponse.builder().status(SUCCESS);
            combineSubjects(successes, responseBuilder);

            // build response
            return responseBuilder.build();
        }).exceptionally(throwable -> {
            Throwable cause = throwable.getCause();
            if (null == cause) {
                cause = throwable;
            }
            if (cause instanceof AsyncAtnException) {
                return ((AsyncAtnException) cause).response;
            }
            return AuthenticationResponse.failed("Failed processing: " + throwable.getMessage(), throwable);
        });
    }

    private CompletionStage<AtnResponse> invokeProvider(AtnResponse previous,
                                                        Atn nextProviderConfig,
                                                        ProviderRequest providerRequest) {
        List<AuthenticationResponse> successes = previous.successResponses;
        CompositeProviderFlag flag = nextProviderConfig.config.flag();

        return nextProviderConfig.provider
                .authenticate(providerRequest)
                .thenApply(atnResponse -> {
                    checkAtnResponseStatus(flag, atnResponse, atnResponse.status());
                    if (atnResponse.status() == SUCCESS) {
                        successes.add(atnResponse);
                    }
                    if ((flag == SUFFICIENT) && (atnResponse.status() == SUCCESS)) {
                        // no need to go any further
                        AuthenticationResponse.Builder responseBuilder = AuthenticationResponse.builder();
                        combineSubjects(successes, responseBuilder);

                        // build response
                        AuthenticationResponse newResponse = responseBuilder
                                .status(SUCCESS)
                                .build();
                        throw new AsyncAtnException(newResponse);
                    }

                    if (atnResponse.status() == ABSTAIN) {
                        // if we abstain, we want to return the previous response
                        return new AtnResponse(previous.response, successes);
                    }

                    return new AtnResponse(atnResponse, successes);
                });
    }

    private void combineSubjects(List<AuthenticationResponse> successes, AuthenticationResponse.Builder responseBuilder) {
        Subject userSubject = null;
        Subject serviceSubject = null;
        for (AuthenticationResponse success : successes) {
            Optional<Subject> maybeUser = success.user();
            Optional<Subject> maybeService = success.service();

            if (maybeUser.isPresent()) {
                Subject newSubject = maybeUser.get();
                if (null == userSubject) {
                    userSubject = newSubject;
                } else {
                    userSubject = newSubject.combine(userSubject);
                }
            }

            if (maybeService.isPresent()) {
                Subject newSubject = maybeService.get();
                if (null == serviceSubject) {
                    serviceSubject = newSubject;
                } else {
                    serviceSubject = newSubject.combine(serviceSubject);
                }
            }
        }
        if (null != userSubject) {
            responseBuilder.user(userSubject);
        }
        if (null != serviceSubject) {
            responseBuilder.service(serviceSubject);
        }
    }

    private void checkAtnResponseStatus(CompositeProviderFlag flag,
                                        AuthenticationResponse response,
                                        SecurityResponse.SecurityStatus status) {
        if (!flag.isValid(status)) {
            // invalid response for the flag, must fail
            // terminate sequence
            // not a valid response for this provider, terminate sequence
            // if the response is other than fail, create a new fail
            switch (status) {
            case SUCCESS:
            case SUCCESS_FINISH:
            case ABSTAIN:
                AuthenticationResponse.Builder builder = AuthenticationResponse.builder();
                builder.status(SecurityResponse.SecurityStatus.FAILURE);
                builder.description("Composite flag forbids this response: "
                                            + response.status());
                response.description().map(builder::description);
                response.throwable().map(builder::throwable);
                throw new AsyncAtnException(builder.build());
            case FAILURE:
            case FAILURE_FINISH:
            default:
                throw new AsyncAtnException(response);
            }
        }
    }

    private static final class AtnResponse {
        private final List<AuthenticationResponse> successResponses = new LinkedList<>();
        private final AuthenticationResponse response;

        AtnResponse(AuthenticationResponse response) {
            this.response = response;
        }

        AtnResponse(AuthenticationResponse atnResponse,
                    List<AuthenticationResponse> successes) {
            this(atnResponse);
            this.successResponses.addAll(successes);
        }
    }

    private static final class AsyncAtnException extends RuntimeException {
        private final AuthenticationResponse response;

        private AsyncAtnException(AuthenticationResponse response) {
            this.response = response;
        }
    }

    static class Atn {
        private final CompositeProviderSelectionPolicy.FlaggedProvider config;
        private final AuthenticationProvider provider;

        Atn(CompositeProviderSelectionPolicy.FlaggedProvider config, AuthenticationProvider provider) {
            this.config = config;
            this.provider = provider;
        }
    }
}

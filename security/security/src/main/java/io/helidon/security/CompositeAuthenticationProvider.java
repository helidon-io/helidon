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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import io.helidon.common.OptionalHelper;
import io.helidon.security.spi.AuthenticationProvider;
import io.helidon.security.spi.ProviderConfig;

/**
 * A provider building a single authentication result from one or more authentication providers.
 */
final class CompositeAuthenticationProvider implements AuthenticationProvider {
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
        CompletionStage<AuthenticationResponse> previous = CompletableFuture.completedFuture(AuthenticationResponse.abstain());

        for (Atn providerConfig : providers) {
            previous = previous.thenCombine(providerConfig.provider.authenticate(providerRequest),
                                            (prevRes, thisRes) -> processProvider(providerConfig, prevRes, thisRes));
        }

        return previous.exceptionally(throwable -> {
            Throwable cause = throwable.getCause();
            if (null == cause) {
                cause = throwable;
            }
            if (cause instanceof AsyncAtnException) {
                return ((AsyncAtnException) cause).response;
            }
            return AuthenticationResponse.failed("Failed processing: " + throwable.getMessage(), throwable);
        }).thenApply(authenticationResponse -> {
            if (authenticationResponse.getStatus() == SecurityResponse.SecurityStatus.ABSTAIN) {
                // todo check if this is correct - optional is possible on too many places, maybe atn client should check it?
                //                if (request.isOptional()) {
                //                    return AuthenticationResponse
                //                            .success(SecurityContext.ANONYMOUS, Security.getUser(SecurityContext.ANONYMOUS)
                // .orElse(null));
                //                } else {
                //                    return AuthenticationResponse.failed("All providers abstained, cannot authenticate");
                //                }
                return AuthenticationResponse.abstain();
            }
            return authenticationResponse;
        });
    }

    private AuthenticationResponse processProvider(Atn providerConfig,
                                                   AuthenticationResponse prevResponse,
                                                   AuthenticationResponse thisResponse) {
        CompositeProviderFlag flag = providerConfig.config.getFlag();

        if (!flag.isValid(thisResponse.getStatus())) {
            // terminate sequence
            // not a valid response for this provider, terminate sequence
            // if the response is other than fail, create a new fail
            switch (thisResponse.getStatus()) {
            case SUCCESS:
            case SUCCESS_FINISH:
            case ABSTAIN:
                AuthenticationResponse.Builder builder = AuthenticationResponse.builder();
                builder.status(SecurityResponse.SecurityStatus.FAILURE);
                builder.description("Composite flag forbids this response: "
                                            + thisResponse.getStatus());
                thisResponse.getDescription().map(builder::description);
                thisResponse.getThrowable().map(builder::throwable);
                throw new AsyncAtnException(builder.build());
            case FAILURE:
            case FAILURE_FINISH:
            default:
                throw new AsyncAtnException(thisResponse);
            }
        }

        if ((flag == CompositeProviderFlag.SUFFICIENT) && (
                thisResponse.getStatus() == SecurityResponse.SecurityStatus.SUCCESS)) {
            // if flag is sufficient and we have success, finish processing
            throw new AsyncAtnException(thisResponse);
        }

        // I only care about success
        if (prevResponse.getStatus() == SecurityResponse.SecurityStatus.ABSTAIN) {
            return thisResponse.getStatus().isSuccess() ? thisResponse : prevResponse;
        }

        if (!thisResponse.getStatus().isSuccess()) {
            return prevResponse;
        }

        AuthenticationResponse.Builder responseBuilder = AuthenticationResponse.builder();

        // combine principals
        OptionalHelper.from(prevResponse.getUser()
                                    .map(user -> combine(user, thisResponse.getUser())))
                .or(thisResponse::getUser)
                .asOptional()
                .ifPresent(responseBuilder::user);

        OptionalHelper.from(prevResponse.getService()
                                    .map(service -> combine(service, thisResponse.getService())))
                .or(thisResponse::getService)
                .asOptional()
                .ifPresent(responseBuilder::service);

        // build response
        return responseBuilder
                .status(SecurityResponse.SecurityStatus.SUCCESS)
                .build();
    }

    private Subject combine(Subject older, Optional<Subject> newSubject) {
        if (newSubject.isPresent()) {
            Subject newer = newSubject.get();

            return newer.combine(older);
        } else {
            return older;
        }
    }

    private static final class AsyncAtnException extends RuntimeException {
        private AuthenticationResponse response;

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

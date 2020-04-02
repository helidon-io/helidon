/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
 *
 */
package io.helidon.cors;

import io.helidon.common.HelidonFeatures;
import io.helidon.common.HelidonFlavor;
import io.helidon.common.http.Headers;
import io.helidon.common.http.Http;
import io.helidon.config.Config;
import io.helidon.cors.CrossOriginConfig.CrossOriginConfigMapper;
import io.helidon.cors.CrossOriginHelper.RequestType;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;

import static io.helidon.cors.CrossOriginHelper.requestType;

/**
 * Provides support for CORS in an application or a built-in Helidon service.
 * <p>
 * The application uses the {@link Builder} to set CORS-related values, including the @{code cors} config node from the
 * application config, if any.
 */
public class CORSSupport implements Service {

    public static CORSSupport create(Config config) {
        return builder().config(config).build();
    }

    static CORSSupport.Builder builder() {
        return new Builder();
    }

    private static final Logger LOGGER = Logger.getLogger(CORSSupport.class.getName());

    static {
        HelidonFeatures.register(HelidonFlavor.SE, "CORS");
    }

    private final List<CrossOriginConfig> crossOriginConfigs;

    private CORSSupport(Builder builder) {
        crossOriginConfigs = builder.configs();
    }

    @Override
    public void update(Routing.Rules rules) {
        configureCORS(rules);
    }

    private void configureCORS(Routing.Rules rules) {
        if (!crossOriginConfigs.isEmpty()) {
            rules.any(this::handleCORS);
        }
    }

    private void handleCORS(ServerRequest request, ServerResponse response) {
        RequestType requestType = requestType(firstHeaderGetter(request),
                containsKeyFn(request),
                request.method().name());

        switch (requestType) {
            case PREFLIGHT:
                ServerResponse preflightResponse = CrossOriginHelper.processPreFlight(request.path().toString(),
                        crossOriginConfigs,
                        firstHeaderGetter(request),
                        allGetter(request),
                        responseSetter(response),
                        responseStatusSetter(response),
                        headerAdder(response));
                preflightResponse.send();
                break;

            case CORS:
                Optional<ServerResponse> corsResponse = CrossOriginHelper.processCorsRequest(request.path().toString(),
                        crossOriginConfigs,
                        firstHeaderGetter(request),
                        responseSetter(response));
                /*
                 * An actual response means there is an error. Otherwise, since we know this is a CORS request, do the CORS
                 * post-processing after other
                 */
                corsResponse.ifPresentOrElse(ServerResponse::send, () -> finishCORSResponse(request, response));
                break;

            case NORMAL:
                request.next();
                break;

            default:
                throw new IllegalStateException(String.format("Unrecognized request type during CORS checking: %s", requestType));

        }
    }

    private Function<String, String> firstHeaderGetter(ServerRequest request) {
        return firstHeaderGetter(request::headers);
    }

    private Function<String, String> firstHeaderGetter(ServerResponse response) {
        return firstHeaderGetter(response::headers);
    }

    private Function<String, String> firstHeaderGetter(Supplier<Headers> headers) {
        return (headerName) -> headers.get().first(headerName).orElse(null);
    }

    private Function<String, Boolean> containsKeyFn(ServerRequest request) {
        return (key) -> request.headers().first(key).isPresent();
    }

    private Function<String, List<String>> allGetter(ServerRequest request) {
        return (key) -> request.headers().all(key);
    }

    private BiFunction<String, Integer, ServerResponse> responseSetter(ServerResponse response) {
        return (errorMsg, statusCode) -> response.status(Http.ResponseStatus.create(statusCode, errorMsg));
    }

    private Function<Integer, ServerResponse> responseStatusSetter(ServerResponse response) {
        return (statusCode) -> response.status(statusCode);
    }

    private BiConsumer<String, Object> headerAdder(ServerResponse response) {
        return (key, value) -> response.headers().add(key, value.toString());
    }

    private void finishCORSResponse(ServerRequest request, ServerResponse response) {
        CrossOriginHelper.prepareCorsResponse(request.path().toString(),
                crossOriginConfigs,
                firstHeaderGetter(response),
                headerAdder(response));

        request.next();
    }

    public static class Builder implements io.helidon.common.Builder<CORSSupport> {

        private Optional<Config> corsConfig = Optional.empty();

        @Override
        public CORSSupport build() {
            return new CORSSupport(this);
        }

        /**
         * Saves CORS config information derived from the {@code Config}. Typically, the app or component will retrieve the
         * provided {@code Config} instance from its own config using the key {@value CrossOriginHelper#CORS_CONFIG_KEY}.
         *
         * @param config the CORS config
         * @return the updated builder
         */
        public Builder config(Config config) {
            this.corsConfig = Optional.of(config);
            return this;
        }

        /**
         * Returns CORS-related information that was derived from the app's or component's config node.
         *
         * @return list of CrossOriginConfig instances, each describing a path and its associated constraints or permissions
         */
        List<CrossOriginConfig> configs() {
            return corsConfig.map(c -> c.as(new CrossOriginConfigMapper()).get())
                         .orElse(Collections.emptyList());
        }
    }
}

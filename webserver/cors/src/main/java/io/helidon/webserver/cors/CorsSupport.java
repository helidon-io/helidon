/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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
package io.helidon.webserver.cors;

import java.util.Optional;

import io.helidon.config.Config;
import io.helidon.webserver.Handler;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;

/**
 * SE implementation of {@link CorsSupportBase}.
 */
public class CorsSupport extends CorsSupportBase<ServerRequest, ServerResponse, CorsSupport, CorsSupport.Builder>
        implements Service, Handler {

    private CorsSupport(Builder builder) {
        super(builder);
    }

    /**
     *
     * @return new builder for CorsSupport
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     *
     * @return new CorsSupport with default settings
     */
    public static CorsSupport create() {
        return builder().build();
    }

    @Override
    public void update(Routing.Rules rules) {
        if (helper().isActive()) {
            rules.any(this);
        }
    }

    @Override
    public void accept(ServerRequest request, ServerResponse response) {
        if (!helper().isActive()) {
            request.next();
            return;
        }
        RequestAdapter<ServerRequest> requestAdapter = new RequestAdapterSe(request);
        ResponseAdapter<ServerResponse> responseAdapter = new ResponseAdapterSe(response);

        Optional<ServerResponse> responseOpt = helper().processRequest(requestAdapter, responseAdapter);

        responseOpt.ifPresentOrElse(ServerResponse::send, () -> prepareCORSResponseAndContinue(requestAdapter, responseAdapter));
    }

    private void prepareCORSResponseAndContinue(RequestAdapter<ServerRequest> requestAdapter,
            ResponseAdapter<ServerResponse> responseAdapter) {
        helper().prepareResponse(requestAdapter, responseAdapter);

        requestAdapter.next();
    }

    /**
     * Creates a new {@code CorsSupport} instance based on the provided configuration expected to match the basic
     * {@code CrossOriginConfig} format.
     *
     * @param config node containing the cross-origin information
     * @return initialized {@code CorsSupport} instance
     */
    public static CorsSupport create(Config config) {
        return builder().config(config).build();
    }

    /**
     * Creates a new {@code CorsSupport} instance based on the provided configuration expected to contain mapped cross-origin
     * config information.
     *
     * @param config node containing the mapped cross-origin information
     * @return initialized {@code CorsSupport} instance
     */
    public static CorsSupport createMapped(Config config) {
        return builder().mappedConfig(config).build();
    }

    @Override
    public String toString() {
        return String.format("CorsSupport[%s]{%s}", name(), describe());
    }

    /**
     * Fluent API builder for {@link io.helidon.webserver.cors.CorsSupport}.
     */
    public static class Builder extends CorsSupportBase.Builder<ServerRequest, ServerResponse, CorsSupport, Builder> {

        private static int builderCount = 0; // To help distinguish otherwise-unnamed CorsSupport instances in log messages

        Builder() {
            name("SE " + builderCount++); // Initial name. The developer can (should) provide a more descriptive one.
            requestDefaultBehaviorIfNone();
        }

        @Override
        public CorsSupport build() {
            return new CorsSupport(this);
        }

        @Override
        protected Builder me() {
            return this;
        }
    }
}

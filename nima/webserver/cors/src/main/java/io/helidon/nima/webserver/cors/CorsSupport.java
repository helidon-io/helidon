/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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
package io.helidon.nima.webserver.cors;

import java.util.Optional;

import io.helidon.config.Config;
import io.helidon.cors.CorsRequestAdapter;
import io.helidon.cors.CorsResponseAdapter;
import io.helidon.cors.CorsSupportBase;
import io.helidon.cors.CorsSupportHelper;
import io.helidon.nima.webserver.http.Handler;
import io.helidon.nima.webserver.http.HttpRules;
import io.helidon.nima.webserver.http.HttpService;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;

/**
 * SE implementation of {@link CorsSupportBase}.
 */
public class CorsSupport extends CorsSupportBase<ServerRequest, ServerResponse, CorsSupport, CorsSupport.Builder>
        implements HttpService, Handler {

    private CorsSupport(Builder builder) {
        super(builder);
    }

    /**
     * A new fluent API builder to customize setup of CorsSupport.
     *
     * @return new builder for CorsSupport
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create CORS support with defaults.
     *
     * @return new CorsSupport with default settings
     */
    public static CorsSupport create() {
        return builder().build();
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
    public void routing(HttpRules rules) {
        if (helper().isActive()) {
            rules.any(this);
        }
    }

    @Override
    public void handle(ServerRequest req, ServerResponse res) {
        if (!helper().isActive()) {
            res.next();
            return;
        }
        CorsRequestAdapter<ServerRequest> requestAdapter = new RequestAdapterNima(req, res);
        CorsResponseAdapter<ServerResponse> responseAdapter = new ResponseAdapterNima(res);

        Optional<ServerResponse> responseOpt = helper().processRequest(requestAdapter, responseAdapter);

        responseOpt.ifPresentOrElse(ServerResponse::send, () -> prepareCORSResponseAndContinue(requestAdapter, responseAdapter));
    }

    @Override
    public String toString() {
        return String.format("CorsSupport[%s]{%s}", name(), describe());
    }

    private void prepareCORSResponseAndContinue(CorsRequestAdapter<ServerRequest> requestAdapter,
                                                CorsResponseAdapter<ServerResponse> responseAdapter) {
        helper().prepareResponse(requestAdapter, responseAdapter);

        requestAdapter.next();
    }

    @Override
    protected CorsSupportHelper<ServerRequest, ServerResponse> helper() {
        return super.helper();
    }

    /**
     * Fluent API builder for {@link CorsSupport}.
     */
    public static class Builder extends CorsSupportBase.Builder<ServerRequest, ServerResponse, CorsSupport, Builder> {

        private static int builderCount = 0; // To help distinguish otherwise-unnamed CorsSupport instances in log messages

        Builder() {
            name("Nima " + builderCount++); // Initial name. The developer can (should) provide a more descriptive one.
            requestDefaultBehaviorIfNone();
        }

        @Override
        public CorsSupport build() {
            return new CorsSupport(this);
        }
    }
}

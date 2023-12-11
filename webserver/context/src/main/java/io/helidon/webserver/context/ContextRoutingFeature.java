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

package io.helidon.webserver.context;

import io.helidon.common.Weighted;
import io.helidon.common.context.Contexts;
import io.helidon.webserver.http.FilterChain;
import io.helidon.webserver.http.HttpFeature;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.RoutingRequest;
import io.helidon.webserver.http.RoutingResponse;

/**
 * Adds {@link io.helidon.common.context.Context} support to Helidon WebServer.
 * When added to the processing, further processing will be executed in a request specific context.
 */
class ContextRoutingFeature implements HttpFeature, Weighted {

    private final double weight;

    ContextRoutingFeature(double weight) {
        this.weight = weight;
    }

    @Override
    public void setup(HttpRouting.Builder routing) {
        routing.addFilter(this::filter);
    }

    @Override
    public double weight() {
        return weight;
    }

    private void filter(FilterChain chain, RoutingRequest req, RoutingResponse res) {
        Contexts.runInContext(req.context(), chain::proceed);
    }
}

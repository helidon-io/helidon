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

package io.helidon.nima.webserver.context;

import io.helidon.common.Weighted;
import io.helidon.common.context.Contexts;
import io.helidon.config.Config;
import io.helidon.nima.webserver.http.FilterChain;
import io.helidon.nima.webserver.http.HttpFeature;
import io.helidon.nima.webserver.http.HttpRouting;
import io.helidon.nima.webserver.http.RoutingRequest;
import io.helidon.nima.webserver.http.RoutingResponse;

/**
 * Adds {@link io.helidon.common.context.Context} support to Níma WebServer.
 * When added to the processing, further processing will be executed in a request specific context.
 */
public class ContextFeature implements HttpFeature, Weighted {
    private static final double WEIGHT = Weighted.DEFAULT_WEIGHT + 1000;

    private final double weight;

    private ContextFeature(double weight) {
        this.weight = weight;
    }

    /**
     * Create a new context feature with default setup.
     *
     * @return a new feature
     */
    public static ContextFeature create() {
        return new ContextFeature(WEIGHT);
    }

    /**
     * Create a new context feature with custom setup.
     *
     * @param config configuration
     * @return a new configured feature
     */
    public static ContextFeature create(Config config) {
        return new ContextFeature(config.get("weight").asDouble().orElse(WEIGHT));
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

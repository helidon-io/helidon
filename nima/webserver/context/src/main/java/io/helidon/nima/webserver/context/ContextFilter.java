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

package io.helidon.nima.webserver.context;

import io.helidon.common.context.Contexts;
import io.helidon.nima.webserver.http.Filter;
import io.helidon.nima.webserver.http.FilterChain;
import io.helidon.nima.webserver.http.RoutingRequest;
import io.helidon.nima.webserver.http.RoutingResponse;

/**
 * Adds {@link io.helidon.common.context.Context} support to Níma WebServer.
 * When added to the processing, further processing will be executed in a request specific context.
 */
public class ContextFilter implements Filter {
    private ContextFilter() {
    }

    /**
     * Create a new context filter with default setup.
     *
     * @return a new filter
     */
    public static ContextFilter create() {
        return new ContextFilter();
    }

    @Override
    public void filter(FilterChain chain, RoutingRequest req, RoutingResponse res) {
        Contexts.runInContext(req.context(), chain::proceed);
    }
}

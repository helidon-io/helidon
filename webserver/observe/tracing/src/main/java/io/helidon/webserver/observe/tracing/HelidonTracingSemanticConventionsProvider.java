/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.webserver.observe.tracing;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.service.registry.Service;
import io.helidon.tracing.config.SpanTracingConfig;
import io.helidon.webserver.http.RoutingRequest;
import io.helidon.webserver.http.RoutingResponse;
import io.helidon.webserver.observe.tracing.spi.TracingSemanticConventionsProvider;

/**
 * Provider for Helidon's tracing semantic conventions.
 * <p>
 * Use a low weight to allow other implementations to have priority if they are present.
 */
@Service.Singleton
@Weight(Weighted.DEFAULT_WEIGHT - 50)
class HelidonTracingSemanticConventionsProvider implements TracingSemanticConventionsProvider {
    @Override
    public TracingSemanticConventions create(SpanTracingConfig spanTracingConfig,
                                             String socketName,
                                             RoutingRequest routingRequest,
                                             RoutingResponse routingResponse) {
        return HelidonTracingSemanticConventions.create(spanTracingConfig, socketName, routingRequest, routingResponse);
    }
}

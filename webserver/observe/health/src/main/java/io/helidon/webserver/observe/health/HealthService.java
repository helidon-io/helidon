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

package io.helidon.webserver.observe.health;

import java.util.List;

import io.helidon.health.HealthCheck;
import io.helidon.health.HealthCheckType;
import io.helidon.http.media.EntityWriter;
import io.helidon.http.media.jsonp.JsonpSupport;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;

import jakarta.json.JsonObject;

import static io.helidon.health.HealthCheckType.LIVENESS;
import static io.helidon.health.HealthCheckType.READINESS;
import static io.helidon.health.HealthCheckType.STARTUP;

/**
 * Observe health endpoints.
 * This service provides endpoints for {@link io.helidon.http.Method#GET} and
 * {@link io.helidon.http.Method#HEAD} methods.
 */
class HealthService implements HttpService {
    private final boolean details;
    private final List<HealthCheck> all;
    private final List<HealthCheck> ready;
    private final List<HealthCheck> live;
    private final List<HealthCheck> start;

    HealthService(HealthObserverConfig config, List<HealthCheck> healthChecks) {
        this.details = config.details();

        this.all = List.copyOf(healthChecks);
        this.ready = healthChecks.stream()
                .filter(it -> it.type() == HealthCheckType.READINESS)
                .toList();
        this.live = healthChecks.stream()
                .filter(it -> it.type() == HealthCheckType.LIVENESS)
                .toList();
        this.start = healthChecks.stream()
                .filter(it -> it.type() == HealthCheckType.STARTUP)
                .toList();
    }

    @Override
    public void routing(HttpRules rules) {
        EntityWriter<JsonObject> entityWriter = JsonpSupport.serverResponseWriter();

        rules.get("/", new HealthHandler(entityWriter, details, all))
                .get("/" + READINESS.defaultEndpoint(), new HealthHandler(entityWriter, details, ready))
                .get("/" + LIVENESS.defaultEndpoint(), new HealthHandler(entityWriter, details, live))
                .get("/" + STARTUP.defaultEndpoint(), new HealthHandler(entityWriter, details, start))
                .get("/" + READINESS.defaultEndpoint() + "/{name}", new SingleCheckHandler(entityWriter, details, ready))
                .get("/" + LIVENESS.defaultEndpoint() + "/{name}", new SingleCheckHandler(entityWriter, details, live))
                .get("/" + STARTUP.defaultEndpoint() + "/{name}", new SingleCheckHandler(entityWriter, details, start))
                .get("/check/{name}", new SingleCheckHandler(entityWriter, details, all))
                .head("/", new HealthHandler(entityWriter, false, all))
                .head("/" + READINESS.defaultEndpoint(), new HealthHandler(entityWriter, false, ready))
                .head("/" + LIVENESS.defaultEndpoint(), new HealthHandler(entityWriter, false, live))
                .head("/" + STARTUP.defaultEndpoint(), new HealthHandler(entityWriter, false, start))
                .head("/" + READINESS.defaultEndpoint() + "/{name}", new SingleCheckHandler(entityWriter, false, ready))
                .head("/" + LIVENESS.defaultEndpoint() + "/{name}", new SingleCheckHandler(entityWriter, false, live))
                .head("/" + STARTUP.defaultEndpoint() + "/{name}", new SingleCheckHandler(entityWriter, false, start))
                .head("/check/{name}", new SingleCheckHandler(entityWriter, false, all));

    }
}

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

package io.helidon.integrations.micrometer.reactive;

import java.io.IOException;
import java.io.StringWriter;

import io.helidon.common.http.Http;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.integrations.micrometer.MicrometerPrometheusRegistrySupport;
import io.helidon.reactive.webserver.Handler;
import io.helidon.reactive.webserver.ServerRequest;
import io.helidon.reactive.webserver.ServerResponse;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheus.PrometheusMeterRegistry;

/**
 * Handler for dealing with HTTP requests to the Micrometer endpoint that specify prometheus as the registry type.
 */
class ReactivePrometheusHandler implements Handler {

    private final PrometheusMeterRegistry registry;

    private ReactivePrometheusHandler(PrometheusMeterRegistry registry) {
        this.registry = registry;
    }

    static ReactivePrometheusHandler create(MeterRegistry registry) {
        return new ReactivePrometheusHandler(PrometheusMeterRegistry.class.cast(registry));
    }

    @Override
    public void accept(ServerRequest req, ServerResponse res) {
        res.headers().contentType(MediaTypes.TEXT_PLAIN);
        if (req.method() == Http.Method.GET) {
            res.send(registry.scrape());
        } else if (req.method() == Http.Method.OPTIONS) {
            StringWriter writer = new StringWriter();
            try {
                MicrometerPrometheusRegistrySupport.metadata(writer, registry);
                res.send(writer.toString());
            } catch (IOException e) {
                res.status(Http.Status.INTERNAL_SERVER_ERROR_500)
                        .send(e);
            }
        } else {
            res.status(Http.Status.NOT_IMPLEMENTED_501)
                    .send();
        }
    }
}

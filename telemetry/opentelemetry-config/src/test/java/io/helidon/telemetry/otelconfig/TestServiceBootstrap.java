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

package io.helidon.telemetry.otelconfig;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.service.registry.GlobalServiceRegistry;
import io.helidon.service.registry.Lookup;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.Services;
import io.helidon.testing.junit5.Testing;

import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;

@Testing.Test
class TestServiceBootstrap {

    @Test
    void testServiceBootstrap() {

        Config config = Config.just(ConfigSources.create(
                """
                        telemetry:
                          service: "test-otel"
                          global: false
                          signals:
                            tracing:
                              sampler:
                                type: "always_on"
                              exporters:
                                - type: otlp
                                  protocol: http/proto
                                  name: my-oltp
                                - type: zipkin
                              processors:
                                - max-queue-size: 21
                                  type: batch
                        """,
                MediaTypes.APPLICATION_YAML));

        Services.set(Config.class, config);

        /*
        The following is not really needed in a test, because our first attempt to get the HelidonOpenTelemetry
        would trigger its supplier on demand. But SE user code should do something like this to make sure that
        we can initialize OpenTelemetry via config (if that's how the user wants to do it) early, before some other
        code might assign the OpenTelemetry global instance.
         */
        GlobalServiceRegistry.registry().all(Lookup.builder()
                                                     .runLevel(Service.RunLevel.STARTUP)
                                                     .build());

        OpenTelemetry ot = Services.get(OpenTelemetry.class);
        assertThat("OpenTelemetry instance via service registry", ot, notNullValue());

    }
}

/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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
package io.helidon.microprofile.telemetry;

import io.helidon.microprofile.testing.junit5.AddBean;
import io.helidon.microprofile.testing.junit5.AddConfig;
import io.helidon.microprofile.testing.junit5.HelidonTest;
import io.helidon.tracing.providers.opentelemetry.HelidonOpenTelemetry;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Configuration;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@HelidonTest
@AddBean(AutoSpanIncludesWriteAgentTest.RegistrationResource.class)
@AddConfig(key = HelidonTelemetryContainerFilter.AUTO_SPAN_INCLUDES_RESPONSE_WRITE, value = "true")
@AddConfig(key = HelidonOpenTelemetry.OTEL_AGENT_PRESENT_PROPERTY, value = "true")
@SuppressWarnings("removal")
class AutoSpanIncludesWriteAgentTest {

    @Inject
    private WebTarget webTarget;

    @Test
    void agentPresentOmitsResponseWriteProviders() {
        boolean responseWriteProvidersRegistered = webTarget.path("/agent-provider-registration")
                .request(MediaType.TEXT_PLAIN)
                .get(Boolean.class);

        assertThat("Response-write providers registered", responseWriteProvidersRegistered, is(false));
    }

    @Path("/agent-provider-registration")
    @ApplicationScoped
    public static class RegistrationResource {

        @Context
        private Configuration configuration;

        @GET
        @Produces(MediaType.TEXT_PLAIN)
        public boolean responseWriteProvidersRegistered() {
            return configuration.isRegistered(HelidonTelemetryWriterInterceptor.class)
                    || configuration.isRegistered(HelidonTelemetryRequestEventListener.class);
        }
    }
}

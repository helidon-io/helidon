/*
 * Copyright (c) 2020, 2024 Oracle and/or its affiliates.
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

package io.helidon.microprofile.messaging.health;

import java.util.Map;

import io.helidon.microprofile.config.ConfigCdiExtension;
import io.helidon.microprofile.health.HealthCdiExtension;
import io.helidon.microprofile.messaging.MessagingCdiExtension;
import io.helidon.microprofile.server.JaxRsCdiExtension;
import io.helidon.microprofile.server.ServerCdiExtension;
import io.helidon.microprofile.testing.junit5.AddBean;
import io.helidon.microprofile.testing.junit5.AddBeans;
import io.helidon.microprofile.testing.junit5.AddExtension;
import io.helidon.microprofile.testing.junit5.AddExtensions;
import io.helidon.microprofile.testing.junit5.DisableDiscovery;
import io.helidon.microprofile.testing.junit5.HelidonTest;

import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.glassfish.jersey.ext.cdi1x.internal.CdiComponentProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import static io.helidon.microprofile.messaging.health.TestMessagingBean.CHANNEL_1;
import static io.helidon.microprofile.messaging.health.TestMessagingBean.CHANNEL_2;
import static java.time.Duration.ofMillis;
import static org.eclipse.microprofile.health.HealthCheckResponse.Status.DOWN;
import static org.eclipse.microprofile.health.HealthCheckResponse.Status.UP;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests for messaging health:
 * <pre>{@code
 * {
 *     "status": "UP",
 *     "checks": [
 *         {
 *             "name": "messaging",
 *             "status": "UP",
 *             "data": {
 *                 "test-channel-1": "UP",
 *                 "test-channel-2": "UP"
 *             }
 *         }
 *     ]
 * }</pre>
 */
@HelidonTest(resetPerTest = true)
@DisableDiscovery
@AddBeans({
        @AddBean(MessagingLivenessCheck.class),
        @AddBean(TestMessagingBean.class),
})
@AddExtensions({
        @AddExtension(ConfigCdiExtension.class),
        @AddExtension(ServerCdiExtension.class),
        @AddExtension(JaxRsCdiExtension.class),
        @AddExtension(HealthCdiExtension.class),
        @AddExtension(MessagingCdiExtension.class),
        @AddExtension(CdiComponentProvider.class)
})
public class MessagingHealthTest {

    private static final String ERROR_MESSAGE = "BOOM!";

    @BeforeEach
    void setUp() {
        ServerCdiExtension server = CDI.current().select(ServerCdiExtension.class).get();
    }

    @Test
    void alivenessWithErrorSignal(WebTarget webTarget, SeContainer container) {
        TestMessagingBean bean = container.select(TestMessagingBean.class).get();

        assertMessagingHealth(webTarget, UP, Map.of(
                CHANNEL_1, UP,
                CHANNEL_2, UP
        ));

        bean.getEmitter1().fail(new RuntimeException(ERROR_MESSAGE));
        assertMessagingHealth(webTarget, DOWN, Map.of(
                CHANNEL_1, DOWN,
                CHANNEL_2, UP
        ));
        assertThat(bean.getSubscriber1().error().await(ofMillis(200)).getMessage(),
                equalTo(ERROR_MESSAGE));

        bean.getEmitter2().fail(new RuntimeException(ERROR_MESSAGE));
        assertMessagingHealth(webTarget, DOWN, Map.of(
                CHANNEL_1, DOWN,
                CHANNEL_2, DOWN
        ));
        assertThat(bean.getSubscriber1().error().await(ofMillis(200)).getMessage(),
                equalTo(ERROR_MESSAGE));
    }

    @Test
    void alivenessWithCancelSignal(WebTarget webTarget, SeContainer container) {
        TestMessagingBean bean = container.select(TestMessagingBean.class).get();

        assertMessagingHealth(webTarget, UP, Map.of(
                CHANNEL_1, UP,
                CHANNEL_2, UP
        ));
        assertThat(bean.getEmitter1().isCancelled(), equalTo(Boolean.FALSE));
        assertThat(bean.getEmitter2().isCancelled(), equalTo(Boolean.FALSE));

        bean.getSubscriber1().cancel();
        assertMessagingHealth(webTarget, DOWN, Map.of(
                CHANNEL_1, DOWN,
                CHANNEL_2, UP
        ));
        assertThat(bean.getEmitter1().isCancelled(), equalTo(Boolean.TRUE));

        bean.getSubscriber2().cancel();
        assertMessagingHealth(webTarget, DOWN, Map.of(
                CHANNEL_1, DOWN,
                CHANNEL_2, DOWN
        ));
        assertThat(bean.getEmitter2().isCancelled(), equalTo(Boolean.TRUE));
    }

    private void assertMessagingHealth(WebTarget webTarget, HealthCheckResponse.Status rootState, Map<String, HealthCheckResponse.Status> channels) {
        JsonObject messaging = getHealthCheck(webTarget, "messaging");
        assertThat(messaging.getString("status"), equalTo(rootState.name()));
        JsonObject data = messaging.getJsonObject("data");
        channels.forEach((name, state) -> assertThat(data.getString(name), equalTo(state.name())));
    }

    private JsonObject getHealthCheck(WebTarget webTarget, String checkName) {
        Response response = webTarget.path("/health").request().get();
        JsonObject jsonObject = response.readEntity(JsonObject.class);
        return jsonObject.getValue("/checks")
                         .asJsonArray().stream()
                         .map(JsonValue::asJsonObject)
                         .filter(check -> check.getString("name").equals(checkName))
                         .findFirst()
                         .orElseThrow(() -> new AssertionFailedError("Health check 'messaging' is missing!"));
    }
}

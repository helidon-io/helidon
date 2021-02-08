/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
 *
 */

package io.helidon.microprofile.messaging.hook;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.annotation.Priority;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Initialized;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.BeanManager;
import javax.inject.Inject;

import io.helidon.microprofile.config.ConfigCdiExtension;
import io.helidon.microprofile.messaging.MessagingCdiExtension;
import io.helidon.microprofile.tests.junit5.AddConfig;
import io.helidon.microprofile.tests.junit5.AddExtension;
import io.helidon.microprofile.tests.junit5.DisableDiscovery;
import io.helidon.microprofile.tests.junit5.HelidonTest;

import static javax.interceptor.Interceptor.Priority.PLATFORM_AFTER;
import static org.hamcrest.MatcherAssert.assertThat;

import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

@HelidonTest
@DisableDiscovery
@AddExtension(ConfigCdiExtension.class)
@AddExtension(MessagingCdiExtension.class)
@AddConfig(key = "mp.messaging.outgoing.channel1.lraTest", value = "outgoingTestValue")
@AddConfig(key = "mp.messaging.incoming.channel1.lraTest", value = "incomingTestValue")
public class ChannelConfigTest {

    CompletableFuture<Integer> payloadFuture = new CompletableFuture<>();
    CompletableFuture<Optional<String>> incomingConfigValueBeforeFuture = new CompletableFuture<>();
    CompletableFuture<Optional<String>> incomingConfigValueAfterFuture = new CompletableFuture<>();
    CompletableFuture<Optional<String>> outgoingConfigValueBeforeFuture = new CompletableFuture<>();
    CompletableFuture<Optional<String>> outgoingConfigValueAfterFuture = new CompletableFuture<>();
    CompletableFuture<Optional<String>> incomingErrorConfigValueAfterFuture = new CompletableFuture<>();

    @Inject
    MessagingCdiExtension messagingCdiExtension;

    @Outgoing("channel0")
    public PublisherBuilder<Message<String>> publish() {
        return ReactiveStreams.of("1", "NaN")
                .map(Message::of);
    }

    @Incoming("channel0")
    @Outgoing("channel1")
    public Message<String> process(Message<String> msg) {
        return msg;
    }

    @Incoming("channel1")
    public CompletionStage<Void> consume(Message<String> msg) {
        payloadFuture.complete(Integer.parseInt(msg.getPayload()));
        return CompletableFuture.completedStage(null);
    }

    private void makeConnections(@Observes @Priority(PLATFORM_AFTER - 1) @Initialized(ApplicationScoped.class) Object event,
                                 BeanManager beanManager) {
        messagingCdiExtension.beforeMethodInvocation((m, message) -> {
            if ("consume".equals(m.getName())) {
                incomingConfigValueBeforeFuture.complete(m.getIncomingChannelConfig().getOptionalValue("lraTest", String.class));
            }
            if ("process".equals(m.getName())) {
                outgoingConfigValueBeforeFuture.complete(m.getOutgoingChannelConfig().getOptionalValue("lraTest", String.class));
            }
        });
        messagingCdiExtension.afterMethodInvocation((m, o) -> {
            if ("consume".equals(m.getName())) {
                incomingConfigValueAfterFuture.complete(m.getIncomingChannelConfig().getOptionalValue("lraTest", String.class));
            }
            if ("process".equals(m.getName())) {
                outgoingConfigValueAfterFuture.complete(m.getOutgoingChannelConfig().getOptionalValue("lraTest", String.class));
            }
        });
        messagingCdiExtension.onMethodInvocationFailure((m, message, e) -> {
            incomingErrorConfigValueAfterFuture.complete(m.getIncomingChannelConfig().getOptionalValue("lraTest", String.class));
        });
    }

    @Test
    void configTest() throws InterruptedException, ExecutionException, TimeoutException {
        assertThat(payloadFuture.get(2, TimeUnit.SECONDS), Matchers.equalTo(1));
        assertTestConfigValue(incomingConfigValueBeforeFuture, "incomingTestValue");
        assertTestConfigValue(outgoingConfigValueBeforeFuture, "outgoingTestValue");
        assertTestConfigValue(incomingConfigValueAfterFuture, "incomingTestValue");
        assertTestConfigValue(outgoingConfigValueAfterFuture, "outgoingTestValue");
        assertTestConfigValue(incomingErrorConfigValueAfterFuture, "incomingTestValue");
    }

    private static void assertTestConfigValue(CompletableFuture<Optional<String>> future, String expected)
            throws InterruptedException, ExecutionException, TimeoutException {
        assertThat(future.get(2, TimeUnit.SECONDS), Matchers.equalTo(Optional.of(expected)));
    }
}

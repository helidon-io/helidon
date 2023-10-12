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
package io.helidon.microprofile.messaging;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.helidon.microprofile.config.ConfigCdiExtension;
import io.helidon.microprofile.server.JaxRsCdiExtension;
import io.helidon.microprofile.server.ServerCdiExtension;
import io.helidon.microprofile.testing.junit5.AddExtension;
import io.helidon.microprofile.testing.junit5.DisableDiscovery;
import io.helidon.microprofile.testing.junit5.HelidonTest;

import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.glassfish.jersey.ext.cdi1x.internal.CdiComponentProvider;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

@HelidonTest
@DisableDiscovery
@AddExtension(ConfigCdiExtension.class)
@AddExtension(ServerCdiExtension.class)
@AddExtension(MessagingCdiExtension.class)
@AddExtension(JaxRsCdiExtension.class)
@AddExtension(CdiComponentProvider.class)
class EmitterTest {

    private final CountDownLatch countDownLatch = new CountDownLatch(3);
    private final List<String> payloads = Collections.synchronizedList(new ArrayList<>(3));

    @Inject
    @Channel("channel-1")
    private Emitter<String> emitter;

    @Incoming("channel-1")
    public void rec(String payload) {
        payloads.add(payload);
        countDownLatch.countDown();
    }

    @Test
    void name() throws InterruptedException {
        emitter.send(Message.of("Test1"));
        emitter.send("Test2");
        emitter.send(Message.of("Test3"));
        emitter.complete();
        assertThat(countDownLatch.await(10, TimeUnit.SECONDS), is(true));
        assertThat(payloads, contains("Test1", "Test2", "Test3"));
    }
}

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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
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
import io.helidon.microprofile.messaging.MethodSignatureType;
import io.helidon.microprofile.tests.junit5.AddExtension;
import io.helidon.microprofile.tests.junit5.DisableDiscovery;
import io.helidon.microprofile.tests.junit5.HelidonTest;

import static javax.interceptor.Interceptor.Priority.PLATFORM_AFTER;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.eclipse.microprofile.reactive.streams.operators.SubscriberBuilder;
import org.junit.jupiter.api.Test;

@HelidonTest
@DisableDiscovery
@AddExtension(ConfigCdiExtension.class)
@AddExtension(MessagingCdiExtension.class)
public class MessageExtensionTest {

    @Inject
    MessagingCdiExtension messagingCdiExtension;

    List<ExtendedMessage<String>> actual = new ArrayList<>(3);
    CountDownLatch latch = new CountDownLatch(2);
    CompletableFuture<Object> beforeProcessorMethod = new CompletableFuture<>();
    CompletableFuture<Object> afterProcessorMethod = new CompletableFuture<>();
    CompletableFuture<Object> onFailureProcessorMethod = new CompletableFuture<>();

    @Outgoing("channel0")
    public PublisherBuilder<Message<String>> publish() {
        return ReactiveStreams.of("1", "2")
                .map(ExtendedMessage::of);
    }

    @Incoming("channel0")
    @Outgoing("channel1")
    public Message<String> process(ExtendedMessage<String> msg) {
        if (msg.getPayload().equals("2")) {
            throw new RuntimeException("BOOM!");
        }
        return msg;
    }

    @Incoming("channel1")
    public SubscriberBuilder<String, Void> consume() {
        return ReactiveStreams.<String>builder()
                .onErrorResume(throwable -> "2")
                .peek(s -> latch.countDown())
                .ignore();
    }

    private void makeConnections(@Observes @Priority(PLATFORM_AFTER + 100)
                                 @Initialized(ApplicationScoped.class) Object event,
                                 BeanManager beanManager) {
        messagingCdiExtension.beforeMethodInvocation((m, message) -> {
            if (m.getType().equals(MethodSignatureType.PROCESSOR_MSG_2_MSG)) {
                beforeProcessorMethod.complete(message);
            }
        });
        messagingCdiExtension.afterMethodInvocation((m, o) -> {
            if (m.getType().equals(MethodSignatureType.PROCESSOR_MSG_2_MSG)) {
                afterProcessorMethod.complete(o);
            }
        });
        messagingCdiExtension.onMethodInvocationFailure((m, message, e) -> {
            if (m.getType().equals(MethodSignatureType.PROCESSOR_MSG_2_MSG)) {
                onFailureProcessorMethod.complete(message);
            }
        });
    }


    @Test
    void configTest() throws InterruptedException, ExecutionException, TimeoutException {
        latch.await();
        assertTrue(beforeProcessorMethod.get(100, TimeUnit.MILLISECONDS) instanceof ExtendedMessage);
        assertTrue(afterProcessorMethod.get(100, TimeUnit.MILLISECONDS) instanceof ExtendedMessage);
        assertTrue(onFailureProcessorMethod.get(100, TimeUnit.MILLISECONDS) instanceof ExtendedMessage);
    }

    interface ExtendedMessage<P> extends Message<P> {
        static <T> ExtendedMessage<T> of(T payload) {
            return () -> payload;
        }
    }
}

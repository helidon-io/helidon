/*
 * Copyright (c)  2020 Oracle and/or its affiliates.
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

package io.helidon.microprofile.messaging.inner;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import javax.enterprise.context.ApplicationScoped;

import io.helidon.microprofile.messaging.AsyncTestBean;

import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.reactivestreams.Subscriber;

@ApplicationScoped
public class CompletionStageV1Bean extends AbstractShapeTestBean implements AsyncTestBean {

    AtomicInteger testSequence = new AtomicInteger();
    private final ExecutorService executor = createExecutor();

    @Outgoing("generator-payload-async")
    public CompletionStage<Integer> getPayloadAsync() {
        return CompletableFuture.supplyAsync(() -> testSequence.incrementAndGet(), executor);
    }

    @Incoming("generator-payload-async")
    public Subscriber<Integer> getFromInfiniteAsyncPayloadGenerator() {
        return ReactiveStreams.<Integer>builder()
                .limit(TEST_DATA.size())
                .forEach(s -> getTestLatch().countDown())
                .build();
    }

    @Override
    public void tearDown() {
        awaitShutdown(executor);
    }
}

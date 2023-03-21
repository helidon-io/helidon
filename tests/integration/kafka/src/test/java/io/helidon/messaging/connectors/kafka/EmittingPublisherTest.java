/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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

package io.helidon.messaging.connectors.kafka;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import io.helidon.common.reactive.EmittingPublisher;
import io.helidon.microprofile.reactive.HelidonReactiveStreamsEngine;

import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.eclipse.microprofile.reactive.streams.operators.spi.ReactiveStreamsEngine;
import org.junit.jupiter.api.Test;
import org.reactivestreams.FlowAdapters;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class EmittingPublisherTest {

    private static final Logger LOGGER = Logger.getLogger(EmittingPublisherTest.class.getName());
    private static final List<String> TEST_DATA = List.of("first", "second", "third");
    private static final ReactiveStreamsEngine ENGINE = new HelidonReactiveStreamsEngine();

    @Test
    void happyPathWithLongMaxReq() {
        List<String> result = new ArrayList<>();

        EmittingPublisher<String> emitter = EmittingPublisher.create();
        emitter.onRequest((n, d) -> LOGGER.fine(() -> Long.toString(n)));

        ReactiveStreams
                .fromPublisher(FlowAdapters.toPublisher(emitter))
                .forEach(result::add)
                .run(ENGINE);

        TEST_DATA.forEach(emitter::emit);

        assertThat(result, contains(TEST_DATA.toArray()));
    }

    @Test
    void notReady() {
        EmittingPublisher<String> emitter = EmittingPublisher.create();
        emitter.onRequest((n, d) -> LOGGER.fine(() -> Long.toString(n)));
        assertThat(emitter.emit(""), is(false));
    }

    @Test
    void emitOnCancelled() {
        List<String> forbiddenSigns = new ArrayList<>();

        EmittingPublisher<String> emitter = EmittingPublisher.create();
        emitter.onRequest((n, d) -> LOGGER.fine(() -> Long.toString(n)));

        ReactiveStreams
                .fromPublisher(FlowAdapters.toPublisher(emitter))
                .buildRs(ENGINE).subscribe(new Subscriber<>() {
            @Override
            public void onSubscribe(final Subscription s) {
                s.cancel();
            }

            @Override
            public void onNext(final String s) {
                forbiddenSigns.add("onNext");
            }

            @Override
            public void onError(final Throwable t) {
                forbiddenSigns.add("onError");
            }

            @Override
            public void onComplete() {
                forbiddenSigns.add("onComplete");
            }
        });

        assertThat(emitter.isCancelled(), is(true));
        assertThat(emitter.emit("should false"), is(false));
        assertThat(forbiddenSigns, empty());
    }

    @Test
    void emitOnCompleted() {
        List<String> forbiddenSigs = new ArrayList<>();
        AtomicBoolean onCompleteCalled = new AtomicBoolean();
        EmittingPublisher<String> emitter = EmittingPublisher.create();
        emitter.onRequest((n, d) -> LOGGER.fine(() -> Long.toString(n)));
        emitter.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(final Flow.Subscription s) {
                s.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(final String s) {
                forbiddenSigs.add("onNext");
            }

            @Override
            public void onError(final Throwable t) {
                forbiddenSigs.add("onError");
            }

            @Override
            public void onComplete() {
                onCompleteCalled.set(true);
            }
        });
        emitter.complete();
        assertThat(onCompleteCalled.get(), is(true));
        assertThat(emitter.isCompleted(), is(true));
        assertThrows(IllegalStateException.class, () -> emitter.emit("should false"));
        assertThat(forbiddenSigs, empty());
    }
}

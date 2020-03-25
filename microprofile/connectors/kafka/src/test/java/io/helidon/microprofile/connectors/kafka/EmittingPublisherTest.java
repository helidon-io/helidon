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

package io.helidon.microprofile.connectors.kafka;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.microprofile.reactive.HelidonReactiveStreamsEngine;

import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.eclipse.microprofile.reactive.streams.operators.spi.ReactiveStreamsEngine;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

public class EmittingPublisherTest {

    private static final List<String> TEST_DATA = List.of("first", "second", "third");
    private static final ReactiveStreamsEngine ENGINE = new HelidonReactiveStreamsEngine();

    @Test
    void happyPathWithLongMaxReq() {
        List<String> result = new ArrayList<>();

        EmittingPublisher<String> emitter = new EmittingPublisher<>(Optional.empty());

        ReactiveStreams
                .fromPublisher(emitter)
                .forEach(result::add)
                .run(ENGINE);

        TEST_DATA.forEach(emitter::emit);

        assertEquals(TEST_DATA, result);
    }

    @Test
    void notEnoughReq() {
        List<String> result = new ArrayList<>();

        List<String> forbiddenSigs = new ArrayList<>();

        EmittingPublisher<String> emitter = new EmittingPublisher<>(Optional.empty());

        ReactiveStreams
                .fromPublisher(emitter)
                .buildRs(ENGINE).subscribe(new Subscriber<>() {
            @Override
            public void onSubscribe(final Subscription s) {
                s.request(TEST_DATA.size());
            }

            @Override
            public void onNext(final String s) {
                result.add(s);
            }

            @Override
            public void onError(final Throwable t) {
                forbiddenSigs.add("onError");
            }

            @Override
            public void onComplete() {
                forbiddenSigs.add("onComplete");
            }
        });

        TEST_DATA.forEach(emitter::emit);

        assertFalse(emitter.emit("should be false"));
        assertEquals(List.of(), forbiddenSigs);
        assertEquals(TEST_DATA, result);
    }

    @Test
    void notReady() {
        assertFalse(new EmittingPublisher<>(Optional.empty()).emit(""));
    }

    @Test
    void emitOnCancelled() {
        List<String> forbiddenSigs = new ArrayList<>();

        EmittingPublisher<String> emitter = new EmittingPublisher<>(Optional.empty());

        ReactiveStreams
                .fromPublisher(emitter)
                .buildRs(ENGINE).subscribe(new Subscriber<>() {
            @Override
            public void onSubscribe(final Subscription s) {
                s.cancel();
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
                forbiddenSigs.add("onComplete");
            }
        });

        assertTrue(emitter.isCancelled());
        assertThrows(IllegalStateException.class, () -> emitter.emit("should throw"));
        assertEquals(List.of(), forbiddenSigs);
    }

    @Test
    void emitOnCompleted() {
        List<String> forbiddenSigs = new ArrayList<>();
        AtomicBoolean onCompleteCalled = new AtomicBoolean();
        EmittingPublisher<String> emitter = new EmittingPublisher<>(Optional.empty());
        emitter.subscribe(new Subscriber<>() {
            @Override
            public void onSubscribe(final Subscription s) {
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
        assertTrue(onCompleteCalled.get());
        assertTrue(emitter.isCompleted());
        assertThrows(IllegalStateException.class, () -> emitter.emit("should throw"));
        assertEquals(List.of(), forbiddenSigs);
    }
}

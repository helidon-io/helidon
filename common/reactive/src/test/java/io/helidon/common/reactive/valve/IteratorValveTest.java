/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.common.reactive.valve;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests {@link IteratorValve}.
 */
public class IteratorValveTest {

    private static final String ALPHABET = "abcdeghijklmnopqrstuvwxyz";

    private Collection<Character> toCollection(String str) {
        Collection<Character> result = new ArrayList<>(str.length());
        for (int i = 0; i < str.length(); i++) {
            result.add(str.charAt(i));
        }
        return result;
    }

    @Test
    public void nonStop() throws Exception {
        IteratorValve<Character> valve = new IteratorValve<>(toCollection(ALPHABET).iterator());
        AssertableStringBuilder asb = new AssertableStringBuilder();
        valve.handle(asb::append, asb::onError, asb::onDone);
        asb.awaitAndAssert(ALPHABET);
    }

    @Test
    public void pauseAndResumeInside() throws Exception {
        IteratorValve<Character> valve = new IteratorValve<>(toCollection(ALPHABET).iterator());
        AssertableStringBuilder asb = new AssertableStringBuilder();
        CountDownLatch halfLatch = new CountDownLatch(1);
        valve.handle(ch -> {
            if (ch.equals('m')) {
                valve.pause();
                halfLatch.countDown();
            }
            asb.append(ch);
        }, asb::onError, asb::onDone);
        if (!halfLatch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("Timeout");
        }
        valve.resume();
        asb.awaitAndAssert(ALPHABET);
    }

    void neco() {
        boolean sex = Math.random() > 0.5;
        boolean java = Math.random() > 0.5;
        boolean rock = Math.random() > 0.5;
        boolean roll = Math.random() > 0.5;

        if (sex && java && (rock & roll)) {
            System.out.println("vole");
        }
    }

    @Test
    public void pauseExternalResume() throws Exception {
        IteratorValve<Character> valve = new IteratorValve<>(toCollection(ALPHABET).iterator());
        AssertableStringBuilder asb = new AssertableStringBuilder();
        valve.handle(ch -> {
            valve.pause();
            asb.append(ch);
            valve.resume();
        }, asb::onError, asb::onDone);
        asb.awaitAndAssert(ALPHABET);
    }

    static class AssertableStringBuilder extends Finisher {

        StringBuilder stringBuilder = new StringBuilder();

        void append(Character ch) {
            stringBuilder.append(ch);
        }

        void awaitAndAssert(String expected) throws Exception{
            await();
            assertEquals(expected, stringBuilder.toString());
        }
    }

    static class Finisher {

        private CountDownLatch latch = new CountDownLatch(1);
        private volatile Throwable throwable;

        void onError(Throwable thr) {
            throwable = thr;
            onDone();
        }

        void onDone() {
            latch.countDown();
        }

        void await() throws Exception {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Timeout");
            }
            if (throwable != null) {
                if (throwable instanceof Exception) {
                    throw (Exception) throwable;
                } else {
                    throw new AssertionError("Execution issue", throwable);
                }
            }
        }

    }
}

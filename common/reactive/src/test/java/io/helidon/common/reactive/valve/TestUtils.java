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
import java.util.List;
import java.util.function.Consumer;

class TestUtils {

    static void generate(int from, int to, Consumer<Integer> consumer) {
        for (int i = from; i < to; i++) {
            consumer.accept(i);
        }
    }

    static List<Integer> generateList(int from, int to) {
        ArrayList<Integer> result = new ArrayList<>(to - from);
        for (int i = from; i < to; i++) {
            result.add(i);
        }
        return result;
    }

    static void assertException(Class<? extends Throwable> expected, ExceptionalRunnable runnable) {
        try {
            runnable.run();
            throw new AssertionError("Expecting " + expected.getName() + " but no exception thrown!");
        } catch (Throwable thr) {
            Class<? extends Throwable> thrClass = thr.getClass();
            if (!thrClass.isAssignableFrom(expected)) {
                throw new AssertionError("Expecting " + expected.getName() + " but has " + thrClass.getName());
            }
        }
    }

    @FunctionalInterface
    public interface ExceptionalRunnable {
        void run() throws Throwable;
    }
}

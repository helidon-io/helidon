/*
 * Copyright (c)  2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.common.reactive;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class RecursionDepthTest {
    @Test
    void depthTest() {
        assertFalse(StreamValidationUtils.getRecursionDepth() > 1);
        recursionMethod(4, new AtomicInteger(5), Assertions::assertTrue);
        recursionMethod(1, new AtomicInteger(1), Assertions::assertFalse);
        recursionMethod(10, new AtomicInteger(9), Assertions::assertFalse);
        recursionMethod(15, new AtomicInteger(20), Assertions::assertTrue);
    }

    void recursionMethod(int maxDepth, AtomicInteger counter, Consumer<Boolean> runInDepth) {
        if (counter.decrementAndGet() == 0) {
            runInDepth.accept(StreamValidationUtils.getRecursionDepth() > maxDepth);
            return;
        }
        recursionMethod(maxDepth, counter, runInDepth);
    }
}

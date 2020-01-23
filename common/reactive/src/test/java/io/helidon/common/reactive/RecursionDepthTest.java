/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

public class RecursionDepthTest {
    @Test
    void depthTestPositive() {
        recursiveMethod(5, new AtomicInteger(5));
        recursiveMethod(5, new AtomicInteger(4));
        recursiveMethod(5, new AtomicInteger(3));
        recursiveMethod(5, new AtomicInteger(2));
        recursiveMethod(5, new AtomicInteger(1));
    }

    @Test
    void depthTestNegative() {
        assertThrows(RuntimeException.class, () -> recursiveMethod(5, new AtomicInteger(6)));
        assertThrows(RuntimeException.class, () -> recursiveMethod(5, new AtomicInteger(7)));
        assertThrows(RuntimeException.class, () -> recursiveMethod(5, new AtomicInteger(200)));
    }

    private void recursiveMethod(int maxDepth, AtomicInteger counter) {
        if (counter.decrementAndGet() >= 0) {
            StreamValidationUtils.checkRecursionDepth("RecursionDepthTest1", maxDepth,
                    () -> recursiveMethod(maxDepth, counter),
                    (aLong, throwable) -> {
                        throw new RuntimeException(throwable);
                    });
        }
    }
}

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
import java.util.logging.Logger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

public class RecursionDepthTest {

    private static final Logger LOGGER = Logger.getLogger(RecursionDepthTest.class.getName());

    @Test
    void depthTestPositive() {
        IntStream.rangeClosed(1, 5)
                .mapToObj(AtomicInteger::new)
                .forEach(i -> recursiveMethod(5, i));
    }

    @Test
    void stackJumpPollutionTest() {
        recursiveMethod(5, 1, 3, new AtomicInteger(5));
        recursiveMethod(5, new AtomicInteger(5));
        recursiveMethod(10, 2, 8, new AtomicInteger(10));
        recursiveMethod(10, new AtomicInteger(10));
        recursiveMethod(5, 1, 2, new AtomicInteger(5));
        recursiveMethod(5, new AtomicInteger(5));
        recursiveMethod(5, new AtomicInteger(5));
    }

    @Test
    void stackJumpPollutionTestWithInnerCallbackException() {
        assertThrows(RuntimeException.class, () -> recursiveMethod(2, 1, 3, new AtomicInteger(5)));
        recursiveMethod(5, new AtomicInteger(5));
        recursiveMethod(9, 2, 8, new AtomicInteger(10));
        recursiveMethod(10, new AtomicInteger(10));
        recursiveMethod(5, 1, 2, new AtomicInteger(5));
        recursiveMethod(5, new AtomicInteger(5));
        recursiveMethod(5, new AtomicInteger(5));
    }

    @Test
    void depthTestNegative() {
        assertThrows(RuntimeException.class, () -> recursiveMethod(5, new AtomicInteger(6)));
        assertThrows(RuntimeException.class, () -> recursiveMethod(5, new AtomicInteger(7)));
        assertThrows(RuntimeException.class, () -> recursiveMethod(5, new AtomicInteger(200)));
    }

    private void recursiveMethod(int maxDepth, AtomicInteger counter) {
        recursiveMethod(maxDepth, -1, -1, counter);
    }

    private void recursiveMethod(int maxDepth, int throwAtLevel, int catchAtLevel, AtomicInteger counter) {
        int currentLevelCounter = counter.decrementAndGet();
        if (currentLevelCounter >= 0) {
            if (currentLevelCounter == throwAtLevel) {
                throw new StackJumpException(throwAtLevel);
            }
            try {
                StreamValidationUtils.checkRecursionDepth("RecursionDepthTest1", maxDepth,
                        () -> recursiveMethod(maxDepth, throwAtLevel, catchAtLevel, counter),
                        (aLong, throwable) -> {
                            throw new RuntimeException(throwable);
                        });
            } catch (StackJumpException sje) {
                if (currentLevelCounter == catchAtLevel) {

                    LOGGER.info(String.format("%s to %d", sje.getMessage(), catchAtLevel));
                } else {
                    throw sje;
                }
            }
        }
    }

    private class StackJumpException extends RuntimeException {
        public StackJumpException(int throwAtLevel) {
            super(String.format("Jumped from stack frame %d", throwAtLevel));
        }
    }
}

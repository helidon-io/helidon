/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.service.tests.toolbox;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Testing.
 */
public class Verification {
    /**
     * Testing.
     *
     * @param injectee      injectee
     * @param tag           tag
     * @param injectedCount injectedCount
     * @param expected      expected
     * @param expectedType  expectedType
     */
    public static void verifyInjected(Optional<?> injectee,
                                      String tag,
                                      Integer injectedCount,
                                      boolean expected,
                                      Class<?> expectedType) {
        if (expected && injectee.isEmpty()) {
            throw new AssertionError(tag + " was expected to be present");
        } else if (!expected && injectee.isPresent()) {
            throw new AssertionError(tag + " was not expected to be present");
        }

        if (expectedType != null && expected && !expectedType.isInstance(injectee.get())) {
            throw new AssertionError(tag + " was expected to be of type " + expectedType + " : " + injectee);
        }

        if (injectedCount != null && injectedCount != 1) {
            throw new AssertionError(tag
                                             + " was was expected to be injected 1 time; it was actually injected " + injectedCount + " times");
        }
    }

    /**
     * Testing.
     *
     * @param injectee          injectee
     * @param tag               tag
     * @param injectedCount     injectedCount
     * @param expectedSingleton expectedSingleton
     * @param expectedType      expectedType
     */
    public static void verifyInjected(Supplier<?> injectee,
                                      String tag,
                                      Integer injectedCount,
                                      boolean expectedSingleton,
                                      Class<?> expectedType) {
        Objects.requireNonNull(injectee, tag + " was not injected");
        Object provided = Objects.requireNonNull(injectee.get(), tag + " was expected to be provided");

        if (expectedType != null && !expectedType.isInstance(provided)) {
            throw new AssertionError(tag + " was expected to be of type " + expectedType + " : " + provided);
        }

        Object provided2 = injectee.get();
        if (expectedSingleton && provided != provided2) {
            throw new AssertionError(tag + " was expected to be a singleton provided type");
        }
        if (expectedType != null && !(expectedType.isInstance(provided2))) {
            throw new AssertionError(tag + " was expected to be of type " + expectedType + " : " + provided2);
        }

        if (injectedCount != null && injectedCount != 1) {
            throw new AssertionError(tag
                                             + " was was expected to be injected 1 time; it was actually injected "
                                             + injectedCount + " times");
        }
    }

    /**
     * Testing.
     *
     * @param injectee      injectee
     * @param tag           tag
     * @param injectedCount injectedCount
     * @param expectedSize  expectedSize
     * @param expectedType  expectedType
     */
    public static void verifyInjected(List<?> injectee,
                                      String tag,
                                      Integer injectedCount,
                                      int expectedSize,
                                      Class<?> expectedType) {
        Objects.requireNonNull(injectee, tag + " was not injected");

        int size = injectee.size();
        if (size != expectedSize) {
            throw new AssertionError(tag + " was expected to be size of " + expectedSize
                                             + " but instead was injected with: " + injectee);
        }

        if (injectedCount != null && injectedCount != 1) {
            throw new AssertionError(tag
                                             + " was was expected to be injected 1 time; it was actually injected "
                                             + injectedCount + " times");
        }

        if (expectedType != null) {
            injectee.forEach(item -> {
                if (!expectedType.isInstance(item)) {
                    throw new AssertionError(tag + " was expected to be of type " + expectedType + " : " + item);
                }
            });
        }
    }

}

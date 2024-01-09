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
package io.helidon.common;

import java.util.function.Supplier;

/**
 * Utility class to properly report integer parsing errors.
 */
public class IntegerParser {

    private IntegerParser() {
    }

    /**
     * Throws exception if value returned is negative. Method {@link Integer#parseUnsignedInt(String, int)}
     * can return negative numbers.
     *
     * @param s string to parse
     * @param radix the number radix
     * @param supplier throwable value
     * @return the number
     * @param <T> type of throwable
     * @throws T throwable if number is negative
     */
    public static <T extends Throwable> int parseNonNegative(String s, int radix, Supplier<T> supplier) throws T {
        int v = Integer.parseUnsignedInt(s, radix);
        if (v < 0) {
            throw supplier.get();
        }
        return v;
    }
}

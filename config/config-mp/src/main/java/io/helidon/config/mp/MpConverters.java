/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
package io.helidon.config.mp;

import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

final class MpConverters {
    private MpConverters() {
    }

    static OptionalLong toOptionalLong(String value) {
        return OptionalLong.of(Long.parseLong(value));
    }

    static OptionalDouble toOptionalDouble(String value) {
        return OptionalDouble.of(Double.parseDouble(value));
    }

    static OptionalInt toOptionalInt(String value) {
        return OptionalInt.of(Integer.parseInt(value));
    }
}

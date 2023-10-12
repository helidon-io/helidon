/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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
package io.helidon.http;

import java.util.ArrayList;
import java.util.List;

final class MethodHelper {
    private static final List<Method> KNOWN = new ArrayList<>(10);
    private static AsciiMethodPair[] methods;

    private MethodHelper() {
    }

    static void add(Method method) {
        KNOWN.add(method);
    }

    static void methodsDone() {
        methods = new AsciiMethodPair[KNOWN.size()];
        for (int i = 0; i < KNOWN.size(); i++) {
            methods[i] = AsciiMethodPair.create(KNOWN.get(i));
        }
        KNOWN.clear();
    }

    static Method byName(String upperCase) {
        // optimization over Map (most commonly used methods fastest)
        for (AsciiMethodPair method : methods) {
            if (method.string().equals(upperCase)) {
                return method.method();
            }
        }
        return null;
    }

    private record AsciiMethodPair(String string, Method method) {
        public static AsciiMethodPair create(Method method) {
            return new AsciiMethodPair(method.text(), method);
        }
    }
}

/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
package io.helidon.logging.jul;

import java.util.HashMap;
import java.util.Map;

/**
 * MDC implementation for Java Util Logging framework.
 */
class JulMdc {

    private final static ThreadLocal<Map<String, String>> MDC_PROPERTIES = ThreadLocal.withInitial(HashMap::new);

    private JulMdc() {
        throw new IllegalStateException("This class cannot be instantiated");
    }

    public static void put(String key, String value) {
        MDC_PROPERTIES.get().put(key, value);
    }

    static String get(String key) {
        return MDC_PROPERTIES.get().get(key);
    }

    static void remove(String key) {
        MDC_PROPERTIES.get().remove(key);
    }


    static void clear() {
        MDC_PROPERTIES.get().clear();
    }

    static Map<String, String> properties() {
        return new HashMap<>(MDC_PROPERTIES.get());
    }

    static void properties(Map<String, String> properties) {
        MDC_PROPERTIES.set(new HashMap<>(properties));
    }

}

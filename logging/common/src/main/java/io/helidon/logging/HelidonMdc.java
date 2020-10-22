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
package io.helidon.logging;

import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;

import io.helidon.common.serviceloader.HelidonServiceLoader;
import io.helidon.logging.spi.MdcProvider;

/**
 * Helidon MDC delegates values across all of the supported logging frameworks on the classpath.
 */
public class HelidonMdc {

    private static final List<MdcProvider> SERVICE_LOADER = HelidonServiceLoader
            .builder(ServiceLoader.load(MdcProvider.class)).build().asList();

    private HelidonMdc() {
        throw new UnsupportedOperationException("This class cannot be instantiated");
    }

    /**
     * Propagate value to all of the {@link MdcProvider} registered via SPI.
     *
     * @param key entry key
     * @param value entry value
     */
    public static void set(String key, Object value) {
        SERVICE_LOADER.forEach(provider -> provider.put(key, value));
    }

    /**
     * Remove value with the specific key from all of the instances of {@link MdcProvider}.
     *
     * @param key key
     */
    public static void remove(String key) {
        SERVICE_LOADER.forEach(provider -> provider.remove(key));
    }

    /**
     * Remove all of the entries bound to the current thread from the instances of {@link MdcProvider}.
     */
    public static void clear() {
        SERVICE_LOADER.forEach(MdcProvider::clear);
    }

    /**
     * Return the first value found to the specific key.
     *
     * @param key key
     * @return found value bound to key
     */
    public static String get(String key) {
        return SERVICE_LOADER.stream()
                .map(provider -> provider.get(key))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse("");
    }

}

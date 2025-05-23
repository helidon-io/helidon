/*
 * Copyright (c) 2020, 2025 Oracle and/or its affiliates.
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
package io.helidon.logging.common;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.function.Supplier;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.logging.common.spi.MdcProvider;

/**
 * Helidon MDC delegates values across all of the supported logging frameworks on the classpath.
 * <p>
 * Helidon permits adding MDC entries using {@code Supplier<String>} values as well as direct {@code String} values.
 * Although some logging implementations provide their own context maps (for example {@code ThreadContext} in Log4J and
 * {@code MDC} in SLF4J), they map MDC keys to {@code String} values, not to arbitrary objects that would accommodate
 * {@code Supplier<String>}. Therefore, Helidon manages its own map of key/supplier pairs and resolves all lookups using that map.
 * <p>
 * Helidon also propagates key/string pair assignments to the logging implementations' context maps.
 */
public class HelidonMdc {

    private static final List<MdcProvider> MDC_PROVIDERS = HelidonServiceLoader
            .builder(ServiceLoader.load(MdcProvider.class)).build().asList();

    private static final ThreadLocal<Map<String, Supplier<String>>> SUPPLIERS = ThreadLocal.withInitial(HashMap::new);

    private HelidonMdc() {
        throw new UnsupportedOperationException("This class cannot be instantiated");
    }

    /**
     * Propagate value to all of the {@link MdcProvider} registered via SPI.
     *
     * @param key entry key
     * @param value entry value
     */
    public static void set(String key, String value) {
        MDC_PROVIDERS.forEach(provider -> provider.put(key, value));
    }

    /**
     * Propagate the value supplier to all {@link MdcProvider} instances registered.
     *
     * @param key entry key
     * @param valueSupplier supplier of the entry value
     */
    public static void set(String key, Supplier<String> valueSupplier) {
        SUPPLIERS.get().put(key, valueSupplier);
        MDC_PROVIDERS.forEach(provider -> provider.put(key, valueSupplier.get()));
    }

    /**
     * Sets a value supplier <em>without</em> immediately getting the value and propagating the value to
     * underlying logging implementations.
     * <p>
     * Normally, user code should use {@link #set(String, java.util.function.Supplier)} instead.
     *
     * @param key entry key
     * @param valueSupplier  supplier of the entry value
     */
    public static void setDeferred(String key, Supplier<String> valueSupplier) {
        SUPPLIERS.get().put(key, valueSupplier);
    }

    /**
     * Remove value with the specific key from all of the instances of {@link MdcProvider}.
     *
     * @param key key
     */
    public static void remove(String key) {
        SUPPLIERS.get().remove(key);
        MDC_PROVIDERS.forEach(provider -> provider.remove(key));
    }

    /**
     * Remove all of the entries bound to the current thread from the instances of {@link MdcProvider}.
     */
    public static void clear() {
        SUPPLIERS.get().clear();
        MDC_PROVIDERS.forEach(MdcProvider::clear);
    }

    /**
     * Return the first value found to the specific key.
     *
     * @param key key
     * @return found value bound to key
     */
    public static Optional<String> get(String key) {
        /*
        User or 3rd-party code might have added values directly to the logger's own context store. So look in other
        providers if our data structure cannot resolve the key.
         */
        return SUPPLIERS.get().containsKey(key)
                ? Optional.of(SUPPLIERS.get().get(key).get())
                : MDC_PROVIDERS.stream()
                        .map(provider -> provider.get(key))
                        .filter(Objects::nonNull)
                        .findFirst();
    }

    static Map<String, Supplier<String>> suppliers() {
        return new HashMap<>(SUPPLIERS.get());
    }

    static void suppliers(Map<String, Supplier<String>> suppliers) {
        SUPPLIERS.get().clear();
        SUPPLIERS.get().putAll(suppliers);
    }

    static void clearSuppliers() {
        SUPPLIERS.get().clear();
    }

}

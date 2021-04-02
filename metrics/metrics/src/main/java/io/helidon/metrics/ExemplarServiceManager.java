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
 *
 */
package io.helidon.metrics;

import java.util.Comparator;
import java.util.ServiceLoader;
import java.util.StringJoiner;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Priority;

import io.helidon.common.LazyValue;

/**
 * Loads the {@link ExemplarService} instance (if any) with the most urgent priority.
 */
class ExemplarServiceManager {

    private static final Logger LOGGER = Logger.getLogger(ExemplarServiceManager.class.getName());

    private static final LazyValue<Supplier<String>> EXEMPLAR_SUPPLIER =
            LazyValue.create(ExemplarServiceManager::chooseExemplarSupplier);

    private ExemplarServiceManager() {
    }

    /**
     * Returns the current exemplar string (e.g., trace ID).
     *
     * @return exemplar string provided by the highest-priority service instance
     */
    static String exemplar() {
        return EXEMPLAR_SUPPLIER.get().get();
    }

    private static Supplier<String> chooseExemplarSupplier() {
        StringJoiner joiner = LOGGER.isLoggable(Level.FINE) ? new StringJoiner(",", "[", "]") : null;
        ExemplarService exemplarService =
            ServiceLoader.load(ExemplarService.class)
                .stream()
                .peek(provider -> {
                    if (joiner != null) {
                        joiner.add(String.format("%s(priority %d)", provider.type().getName(), priorityValue(provider)));
                    }
                })
                .min(Comparator.comparing(ExemplarServiceManager::priorityValue))
                .map(ServiceLoader.Provider::get)
                .orElse(new DefaultExemplarService());
        if (joiner != null) {
            LOGGER.log(Level.FINE, String.format("Candidate ExemplarSupports: %s, using %s", joiner.toString(),
                    exemplarService.getClass().getName()));
        }

        return exemplarService.labelSupplier();
    }

    private static int priorityValue(ServiceLoader.Provider<ExemplarService> exemplarSupportProvider) {
        Priority p = exemplarSupportProvider.type().getAnnotation(Priority.class);
        return p == null ? ExemplarService.DEFAULT_PRIORITY : p.value();
    }


}

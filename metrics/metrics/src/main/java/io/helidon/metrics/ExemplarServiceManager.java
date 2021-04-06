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
import java.util.List;
import java.util.ServiceLoader;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.annotation.Priority;

/**
 * Loads the {@link ExemplarService} instance (if any) with the most urgent priority.
 */
class ExemplarServiceManager {

    private static final Logger LOGGER = Logger.getLogger(ExemplarServiceManager.class.getName());

    private static final List<ExemplarService> EXEMPLAR_SERVICES = collectExemplarServices();

    private static final Supplier<String> EXEMPLAR_SUPPLIER = EXEMPLAR_SERVICES.isEmpty()
            ? () -> ""
            : () -> EXEMPLAR_SERVICES.stream()
                        .map(ExemplarService::label)
                        .collect(Collectors.joining(",", "{", "}"));

    private ExemplarServiceManager() {
    }

    /**
     * Returns the current exemplar label (e.g., trace ID).
     *
     * @return exemplar string provided by the highest-priority service instance
     */
    static String exemplarLabel() {
        return EXEMPLAR_SUPPLIER.get();
    }

    private static List<ExemplarService> collectExemplarServices() {
        List<ExemplarService> exemplarServices =
            ServiceLoader.load(ExemplarService.class)
                .stream()
                .sorted(Comparator.comparing(ExemplarServiceManager::priorityValue))
                .map(ServiceLoader.Provider::get)
                .collect(Collectors.toList());

        if (!exemplarServices.isEmpty()) {
            LOGGER.log(Level.INFO, "Using metrics ExemplarServices " + exemplarServices.toString());
        }

        return exemplarServices;
    }

    private static int priorityValue(ServiceLoader.Provider<ExemplarService> exemplarSupportProvider) {
        Priority p = exemplarSupportProvider.type().getAnnotation(Priority.class);
        return p == null ? ExemplarService.DEFAULT_PRIORITY : p.value();
    }
}

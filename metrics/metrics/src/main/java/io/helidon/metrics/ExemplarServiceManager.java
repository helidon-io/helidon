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

import java.util.List;
import java.util.ServiceLoader;
import java.util.StringJoiner;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.serviceloader.HelidonServiceLoader;

/**
 * Loads the {@link ExemplarService} instance (if any) with the most urgent priority.
 */
class ExemplarServiceManager {

    private static final Logger LOGGER = Logger.getLogger(ExemplarServiceManager.class.getName());

    private static final List<ExemplarService> EXEMPLAR_SERVICES = collectExemplarServices();

    private static final boolean IS_ACTIVE = !EXEMPLAR_SERVICES.isEmpty();

    static final String INACTIVE_LABEL = "";

    private static final Supplier<String> EXEMPLAR_SUPPLIER = () -> EXEMPLAR_SERVICES.stream()
                        .map(ExemplarService::label)
                        .filter(Predicate.not(String::isBlank))
                        .collect(ExemplarServiceManager::labelsStringJoiner, StringJoiner::add, StringJoiner::merge)
                        .toString();

    private ExemplarServiceManager() {
    }

    private static StringJoiner labelsStringJoiner() {
        // A StringJoiner that suppresses the prefix and suffix if no strings were added
        return new StringJoiner(",", "{", "}").setEmptyValue("");
    }

    /**
     * Returns the current exemplar label (e.g., trace ID).
     *
     * @return exemplar string provided by the highest-priority service instance
     */
    static String exemplarLabel() {
        return IS_ACTIVE ? EXEMPLAR_SUPPLIER.get() : INACTIVE_LABEL;
    }

    /**
     *
     * @return whether exemplar handling is active or not
     */
    static boolean isActive() {
        return IS_ACTIVE;
    }

    private static List<ExemplarService> collectExemplarServices() {
        List<ExemplarService> exemplarServices =
            HelidonServiceLoader.create(ServiceLoader.load(ExemplarService.class)).asList();
        if (!exemplarServices.isEmpty()) {
            LOGGER.log(Level.INFO, "Using metrics ExemplarServices " + exemplarServices.toString());
        }

        return exemplarServices;
    }
}

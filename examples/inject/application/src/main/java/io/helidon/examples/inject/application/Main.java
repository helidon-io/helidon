/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.examples.inject.application;

import java.util.List;
import java.util.Optional;

import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.Metrics;

/**
 * Application example. Uses the same {@code main()} as {@link io.helidon.examples.inject.basics.Main}.
 */
public class Main {

    /**
     * Executes the example.
     *
     * @param args arguments
     */
    public static void main(String... args) {
        io.helidon.examples.inject.basics.Main.main(args);

        Optional<Counter> counter = Metrics.globalRegistry()
                .counter("io.helidon.inject.lookups", List.of());

        if (counter.isPresent()) {
            System.out.println("Service lookup count: " + counter.get().count());
        } else {
            System.out.println("Service lookup counter is not present");
        }
    }

}

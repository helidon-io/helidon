/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.examples.pico.application;

import io.helidon.pico.api.Metrics;
import io.helidon.pico.api.PicoServices;

/**
 * Application example. Uses the same {@code main()} as {@link io.helidon.examples.pico.basics.Main}.
 */
public class Main extends io.helidon.examples.pico.basics.Main {

    /**
     * Executes the example.
     *
     * @param args arguments
     */
    public static void main(String... args) {
        io.helidon.examples.pico.basics.Main.main(args);

        Metrics metrics = PicoServices.picoServices().orElseThrow().metrics().get();
        System.out.println("Service lookup count: " + metrics.lookupCount().get());
    }

}

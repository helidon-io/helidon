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

import java.util.function.Supplier;

/**
 * Behavior for supporting exemplars in metrics histograms.
 */
public interface ExemplarService {

    /**
     * Defayult priority for an {@code ExemplarService} found by the service loader without an explicit {@code @Priority}
     * annotation.
     */
    int DEFAULT_PRIORITY = 1000;

    /**
     * Returns a label using whatever current context is available.
     *
     * @return the label
     */
    Supplier<String> labelSupplier();
}

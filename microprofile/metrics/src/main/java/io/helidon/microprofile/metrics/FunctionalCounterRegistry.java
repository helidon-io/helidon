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
package io.helidon.microprofile.metrics;

import java.util.function.ToDoubleFunction;

import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.Metadata;
import io.helidon.metrics.api.Tag;

/**
 * Behavior for any registry which permits registration of a functional counter.
 */
public interface FunctionalCounterRegistry {

    /**
     * Finds an existing counter or, if none, creates a new one using a functional interface for providing the value.
     * <p>
     *     If the counter is created, is acts as a read-only wrapper around an object capable of providing a double value.
     *     The implementation rejects operations which would update the counter's value.
     * </p>
     *
     * @param metadata metadata to use for the counter
     * @param origin the object which is the origin for the counter's value
     * @param function function which, when applied to the origin, provides the counter's value
     * @param tags tags for further identifying the meter
     * @return an existing counter (might not be read-only) or a new, read-only counter
     * @param <T> type of the origin of the value
     */
    <T> Counter counter(Metadata metadata, T origin, ToDoubleFunction<T> function, Tag... tags);
}

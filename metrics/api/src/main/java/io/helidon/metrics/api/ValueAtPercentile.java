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
package io.helidon.metrics.api;

import java.util.concurrent.TimeUnit;

/**
 * Percentile and value at that percentile within a distribution.
 */
public interface ValueAtPercentile extends Wrapper {

    /**
     * Returns the percentile.
     *
     * @return the percentile
     */
    double percentile();

    /**
     * Returns the value at this percentile.
     *
     * @return the percentile's value
     */
    double value();

    /**
     * Returns the value of this percentile interpreted as time in nanoseconds converted to the specified
     * {@link java.util.concurrent.TimeUnit}.
     *
     * @param unit time unit in which to express the value
     * @return converted value
     */
    double value(TimeUnit unit);
}

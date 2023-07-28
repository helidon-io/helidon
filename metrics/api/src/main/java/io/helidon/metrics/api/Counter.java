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

/**
 * Records a monotonically increasing value.
 */
public interface Counter extends Meter {

    /**
     * Updates the counter by one.
     */
    void increment();

    /**
     * Updates the counter by the specified amount which should be non-negative.
     *
     * @param amount amount to add to the counter.
     */
    void increment(double amount);

    /**
     * Returns the cumulative count since this counter was registered.
     *
     * @return cumulative count since this counter was registered
     */
    double count();
}

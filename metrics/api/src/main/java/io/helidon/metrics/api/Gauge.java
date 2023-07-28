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
 * Measures a value that can increase or decrease and is updated by external logic, not by explicit invocations
 * of methods on this type.
 */
public interface Gauge extends Meter {

    /**
     * Returns the value of the gauge.
     * <p>
     *     Invoking this method triggers the sampling of the value or invocation of the function provided when the gauge was
     *     registered.
     * </p>
     *
     * @return current value of the gauge
     */
    double value();
}

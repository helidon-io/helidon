/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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
package io.helidon.common.context.spi;

/**
 * This is an SPI provider which helps user to propagate values from one thread to another.
 *
 * Every provider has its method {@link #data()} invoked before thread switch, to obtain
 * value for propagation. After the thread is switched, the new thread executes
 * {@link #propagateData(Object)} to propagate data.
 *
 * @param <T> an actual type of the data which will be propagated
 */
public interface DataPropagationProvider<T> {

    /**
     * Return data that should be propagated.
     *
     * @return data for propagation
     */
    T data();

    /**
     * Propagates the data to be used by the new thread.
     *
     * @param data data for propagation
     */
    void propagateData(T data);

    /**
     * Clears the propagated data from the new thread when it finishes. This
     * method is deprecated in favor of {@link #clearData(Object)}.
     */
    @Deprecated
    default void clearData() {
    }

    /**
     * Clears the propagated data from the new thread when it finishes.
     *
     * @param data data for propagation
     */
    default void clearData(T data) {
        clearData();
    }

}

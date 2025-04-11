/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.data.codegen.query;

/**
 * Projection expression parameter.
 *
 * @param <T> type of the parameter
 */
public interface ProjectionParameter<T> {

    /**
     * Create projection expression parameter with {@link Integer} value.
     *
     * @param value parameter value
     * @return new instance of {@link ProjectionParameter} with {@link Integer} value.
     */
    static ProjectionParameter<Integer> createInteger(int value) {
        return new ProjectionParameterInteger(value);
    }

    /**
     * Projection expression parameter value.
     *
     * @return the parameter value
     */
    T value();

    /**
     * Projection expression parameter type.
     *
     * @return the parameter type
     */
    Class<T> type();

}

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
 * Projection expression operator.
 */
public enum ProjectionOperator {
    /**
     * {@code First} operator.
     */
    First,
    /**
     * {@code Count} operator.
     */
    Count,
    /**
     * {@code Exists} operator.
     */
    Exists,
    /**
     * {@code Min} operator.
     */
    Min,
    /**
     * {@code Max} operator.
     */
    Max,
    /**
     * {@code Sum} operator.
     */
    Sum,
    /**
     * {@code Avg} operator.
     */
    Avg;

    /**
     * Whether expression has parameter.
     *
     * @return value of {@code true} when expression has parameter or {@code false} otherwise.
     */
    public boolean hasParameter() {
        return switch (this) {
            case First -> true;
            case Count,
                 Exists,
                 Max,
                 Min,
                 Sum,
                 Avg -> false;
        };
    }

}

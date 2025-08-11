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
 * Criteria condition operator.
 */
public enum CriteriaOperator {
    /**
     * {@code Equal} operator.
     */
    Equal(1),
    /**
     * {@code After} operator.
     */
    After(1),
    /**
     * {@code Before} operator.
     */
    Before(1),
    /**
     * {@code Contains} operator.
     */
    Contains(1),
    /**
     * {@code EndsWith} operator.
     */
    EndsWith(1),
    /**
     * {@code StartsWith} operator.
     */
    StartsWith(1),
    /**
     * {@code LessThan} operator.
     */
    LessThan(1),
    /**
     * {@code LessThanEqual} operator.
     */
    LessThanEqual(1),
    /**
     * {@code GreaterThan} operator.
     */
    GreaterThan(1),
    /**
     * {@code GreaterThanEqual} operator.
     */
    GreaterThanEqual(1),
    /**
     * {@code Between} operator.
     */
    Between(2),
    /**
     * {@code Like} operator.
     */
    Like(1),
    /**
     * {@code In} operator.
     */
    In(1),
    /**
     * {@code Empty} operator.
     */
    Empty(0),
    /**
     * {@code Null} operator.
     */
    Null(0),
    /**
     * {@code True} operator.
     */
    True(0),
    /**
     * {@code False} operator.
     */
    False(0);

    private final int paramsCount;

    CriteriaOperator(int paramsCount) {
        this.paramsCount = paramsCount;
    }

    /**
     * Number of required criteria parameters.
     *
     * @return the number of required parameters
     */
    public int paramsCount() {
        return paramsCount;
    }

}

/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.dbclient.common;

/**
 * Type of statement parameters.
 */
public enum ParamType {
    /**
     * Indexed values to be passed to the statement in order.
     * In JDBC, this is used for statements that use {@code ?} as a placeholder
     *  for parameters.
     */
    INDEXED,
    /**
     * Named values to be passed to the statement by name.
     * Unless the underlying database directly supports named parameters,
     *  we use {@code :name} in the statement text to represent
     *  a named parameter.
     */
    NAMED,
    /**
     * Statement type is not known.
     */
    UNKNOWN
}

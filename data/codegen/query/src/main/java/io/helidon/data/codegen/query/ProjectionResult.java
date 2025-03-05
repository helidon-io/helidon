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
 * Projection return type limitation.
 */
public enum ProjectionResult {
    /** Only boolean values. */
    Exists,
    /** Only numeric values. */
    Count,
    /** Only single entity or entity property instance, cannot return {@code null}. */
    Get,
    /** {@code Optional} of single entity or entity property instance. */
    Find,
    /** {@code List} of entity or entity property values. */
    List,
    /** {@code Stream} of entity or entity property values. */
    Stream,
    /** DML result. */
    Dml;
}

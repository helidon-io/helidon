/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.inject.codegen.spi;

import io.helidon.codegen.CodegenContext;

/**
 * A {@link java.util.ServiceLoader} provider interface to customize assignments.
 *
 * @see io.helidon.inject.codegen.spi.InjectAssignment
 */
public interface InjectAssignmentProvider {
    /**
     * Create a new provider to customize assignments.
     *
     * @param ctx code generation context
     * @return a new assignment
     */
    InjectAssignment create(CodegenContext ctx);
}

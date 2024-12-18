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

package io.helidon.service.codegen;

import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.types.TypeName;
import io.helidon.service.codegen.spi.InjectAssignment;
import io.helidon.service.codegen.spi.InjectAssignmentProvider;

class Assignments {
    private final List<InjectAssignment> assingments;

    Assignments(RegistryCodegenContext ctx) {
        this.assingments = HelidonServiceLoader.create(
                        ServiceLoader.load(InjectAssignmentProvider.class,
                                           Assignments.class.getClassLoader()))
                .stream()
                .map(it -> it.create(ctx))
                .toList();
    }

    /**
     * This provides support for replacements of types.
     *
     * @param typeName    type name as required by the dependency ("injection point")
     * @param valueSource code with the source of the parameter as Helidon provides it (such as Supplier of type)
     * @return assignment to use for this instance, what type to use in Helidon registry, and code generator to transform to
     *         desired type
     */
    public InjectAssignment.Assignment assignment(TypeName typeName, String valueSource) {
        for (InjectAssignment assignmentProvider : assingments) {
            Optional<InjectAssignment.Assignment> assignment = assignmentProvider.assignment(typeName, valueSource);
            if (assignment.isPresent()) {
                return assignment.get();
            }
        }

        return InjectAssignment.Assignment.create(typeName, it -> it.addContent(valueSource));
    }

}

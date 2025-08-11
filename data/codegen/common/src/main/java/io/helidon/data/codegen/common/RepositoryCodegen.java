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
package io.helidon.data.codegen.common;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.StreamSupport;

import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.CodegenException;
import io.helidon.codegen.RoundContext;
import io.helidon.codegen.spi.CodegenExtension;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.data.codegen.common.spi.PersistenceGenerator;
import io.helidon.data.codegen.common.spi.RepositoryGenerator;

/**
 * Data repository code processing and generation.
 */
class RepositoryCodegen implements CodegenExtension {

    private static final System.Logger LOGGER = System.getLogger(RepositoryCodegen.class.getName());

    private final CodegenContext codegenContext;
    private final List<RepositoryGenerator> repositoryGenerators;
    private final List<PersistenceGenerator> persistenceGenerators;
    private final Set<TypeName> annotations;

    RepositoryCodegen(CodegenContext codegenContext,
                      List<RepositoryGenerator> repositoryGenerators,
                      List<PersistenceGenerator> persistenceGenerators) {
        Objects.requireNonNull(codegenContext, "Codegen context value is null");
        Objects.requireNonNull(repositoryGenerators, "Data repository generators value is null");
        Objects.requireNonNull(persistenceGenerators, "Persistence generators value is null");
        this.codegenContext = codegenContext;
        this.repositoryGenerators = repositoryGenerators;
        this.persistenceGenerators = persistenceGenerators;
        // Build set of all processed annotations
        Set<TypeName> annotations = new HashSet<>();
        repositoryGenerators.forEach(generator -> annotations.addAll(generator.annotations()));
        this.annotations = Set.copyOf(annotations);
    }

    @Override
    public void process(RoundContext roundContext) {
        // Build set of repository interfaces to process in current round
        Set<TypeInfo> repositoryInterfaces = new HashSet<>();
        annotations.forEach(annotation -> roundContext.annotatedTypes(annotation)
                .forEach(repositoryInterfaces::add));

        if (!repositoryInterfaces.isEmpty()) {

            // Data repository extended interfaces mapped to their owners.
            Map<TypeName, RepositoryGenerator> interfaceAssignment = new HashMap<>();
            repositoryGenerators.forEach(
                    generator -> generator.interfaces()
                            .forEach(iface -> interfaceAssignment.put(iface, generator)));

            // Generate all repository interfaces
            repositoryInterfaces.forEach(
                    repositoryInterface -> {
                        RepositoryGenerator repositoryGenerator = analyseInterfaces(repositoryInterface,
                                                                                    interfaceAssignment);
                        persistenceGenerators.forEach(generator -> {
                            generator.generate(codegenContext,
                                               roundContext,
                                               repositoryInterface,
                                               repositoryGenerator);
                        });
                    });
        }
    }

    // Analyse repository interfaces:
    // check whether interface extends just a single data repository (generator)
    private RepositoryGenerator analyseInterfaces(TypeInfo repositoryInterface,
                                                  Map<TypeName, RepositoryGenerator> interfaceAssignment) {
        Set<RepositoryGenerator> assigned = new HashSet<>(repositoryGenerators.size());
        StreamSupport.stream(new TypeInfoSpliterator(repositoryInterface), false)
                .forEach(info -> {
                    if (interfaceAssignment.containsKey(info.typeName())) {
                        assigned.add(interfaceAssignment.get(info.typeName()));
                    }
                });
        if (assigned.size() > 1) {
            throw new CodegenException("Interface extends interfaces from multiple data repository providers",
                                       repositoryInterface.originatingElement()
                                               .orElseGet(repositoryInterface::typeName));
        }
        if (assigned.isEmpty()) {
            throw new CodegenException("Interface extends no data repository provider's interface",
                                       repositoryInterface.originatingElement()
                                               .orElseGet(repositoryInterface::typeName));
        }
        // There is exactly one element in the set
        return assigned.stream()
                .findFirst()
                .get();
    }

}

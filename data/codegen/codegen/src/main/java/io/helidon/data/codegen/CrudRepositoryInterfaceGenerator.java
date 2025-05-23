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
package io.helidon.data.codegen;

import java.util.ArrayList;

import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.Method;
import io.helidon.common.types.Annotations;
import io.helidon.data.codegen.common.BaseRepositoryInterfaceGenerator;
import io.helidon.data.codegen.common.RepositoryInfo;
import io.helidon.data.codegen.common.spi.PersistenceGenerator;

class CrudRepositoryInterfaceGenerator extends BaseRepositoryInterfaceGenerator {

    CrudRepositoryInterfaceGenerator(RepositoryInfo repositoryInfo,
                                     ClassModel.Builder classModel,
                                     CodegenContext codegenContext,
                                     PersistenceGenerator persistenceGenerator) {
        super(repositoryInfo, classModel, codegenContext, persistenceGenerator);
    }

    @Override
    public void generate() {
        classModel()
                .addMethod(this::generateInsertMethod)
                .addMethod(this::generateInsertAllMethod)
                .addMethod(this::generateUpdateMethod)
                .addMethod(this::generateUpdateAllMethod);
    }

    // <T extends E> T insert(T entity)
    private void generateInsertMethod(Method.Builder builder) {
        // Method prototype
        builder.name("insert")
                .addAnnotation(Annotations.OVERRIDE)
                .addParameter(T_ENTITY)
                .returnType(T)
                .addGenericArgument(extendsType(GENERIC_T, repositoryInfo().entity()));
        // Method body: executor.run(em -> em.persist(entity));
        statement(builder,
                  b1 -> run(b1,
                            b2 -> statementGenerator()
                                    .addPersist(b2, ENTITY),
                            EXECUTOR));
        // Method body: return entity;
        returnStatement(builder,
                        b -> identifier(b, ENTITY));
    }

    // <T extends E> Iterable<T> insertAll(Iterable<T> entities)
    private void generateInsertAllMethod(Method.Builder builder) {
        // Method prototype
        builder.name("insertAll")
                .addAnnotation(Annotations.OVERRIDE)
                .addParameter(ITERABLE_T_ENTITIES)
                .addGenericArgument(extendsType(GENERIC_T, repositoryInfo().entity()))
                .returnType(ITERABLE_T);
        // Method body: executor.run(em -> entities.forEach(em::persist));
        statement(builder,
                  b1 -> run(b1,
                            b2 -> statementGenerator()
                                    .addPersistCollection(b2, ENTITIES),
                            EXECUTOR));
        // Method body: return entities;
        returnStatement(builder,
                        b -> identifier(b, ENTITIES));
    }

    // <T extends E> T update(T entity)
    private void generateUpdateMethod(Method.Builder builder) {
        // Method prototype
        builder.name("update")
                .addAnnotation(Annotations.OVERRIDE)
                .addParameter(T_ENTITY)
                .returnType(T)
                .addGenericArgument(extendsType(GENERIC_T, repositoryInfo().entity()));
        // NEXT VERSION: Implement properly
        //       Update requires an entity to already exist in the database, but this check is not trivial:
        //       - verify entity existence with em.find
        //       - or at least verify that entity has ID present
        //       But codegen code does not have an access to ID attribute information
        //       EntityManager merge call should work this way, but there are known reported issues in Hibernate.
        // Method body: return executor.call(em -> em.merge(entity));
        returnStatement(builder,
                        b1 -> call(b1,
                                   b2 -> statementGenerator()
                                           .addMerge(b2, ENTITY),
                                   EXECUTOR));
    }

    // <T extends E> Iterable<T> updateAll(Iterable<T> entities)
    private void generateUpdateAllMethod(Method.Builder builder) {
        // Method prototype
        builder.name("updateAll")
                .addAnnotation(Annotations.OVERRIDE)
                .addParameter(ITERABLE_T_ENTITIES)
                .addGenericArgument(extendsType(GENERIC_T, repositoryInfo().entity()))
                .returnType(ITERABLE_T);
        // NEXT VERSION: Implement properly
        //       Update requires an entity to already exist in the database, but this check is not trivial:
        //       - verify entity existence with em.find
        //       - or at least verify that entity has ID present
        //       But codegen code does not have an access to ID attribute information
        //       EntityManager merge call should work this way, but there are known reported issues in Hibernate.
        // Method body: executor.run(em -> entities.forEach(em::merge));
        // Method body: List<T> mergedEntities = new ArrayList<>();
        //              executor.run(em -> entities.forEach(e -> mergedEntities.add(em.merge(e))));
        //              return mergedEntities;
        statement(builder,
                  b1 -> initializedVariable(b1,
                                            LIST_T,
                                            "mergedEntities",
                                            b2 -> b2.addContent("new ")
                                                    .addContent(ArrayList.class)
                                                    .addContent("<>()")));
        statement(builder,
                  b1 -> run(b1,
                            b2 -> statementGenerator()
                                    .addMergeCollection(b2, ENTITIES, "mergedEntities"),
                            EXECUTOR));
        returnStatement(builder,
                        b -> identifier(b, "mergedEntities"));
    }

}

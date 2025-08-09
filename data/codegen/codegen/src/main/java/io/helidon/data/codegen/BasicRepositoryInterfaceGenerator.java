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
import java.util.Optional;
import java.util.stream.Stream;

import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.Method;
import io.helidon.codegen.classmodel.Parameter;
import io.helidon.common.types.Annotations;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.data.codegen.common.BaseRepositoryInterfaceGenerator;
import io.helidon.data.codegen.common.RepositoryInfo;
import io.helidon.data.codegen.common.spi.PersistenceGenerator;
import io.helidon.data.codegen.query.Criteria;
import io.helidon.data.codegen.query.CriteriaCondition;
import io.helidon.data.codegen.query.DataQuery;
import io.helidon.data.codegen.query.Projection;
import io.helidon.data.codegen.query.Property;

/**
 * {@code io.helidon.data.CrudRepository} interface implementation generator.
 * Only direct interface member methods are added. Super interfaces must have their own generators.
 */
class BasicRepositoryInterfaceGenerator extends BaseRepositoryInterfaceGenerator {

    BasicRepositoryInterfaceGenerator(RepositoryInfo repositoryInfo,
                                      ClassModel.Builder classModel,
                                      CodegenContext codegenContext,
                                      PersistenceGenerator persistenceGenerator) {
        super(repositoryInfo, classModel, codegenContext, persistenceGenerator);
    }

    @Override
    public void generate() {
        classModel()
                .addMethod(this::generateSave)
                .addMethod(this::generateSaveAll)
                .addMethod(this::generateFindById)
                .addMethod(this::generateExistsById)
                .addMethod(this::generateFindAll)
                .addMethod(this::generateCount)
                .addMethod(this::generateDeleteById)
                .addMethod(this::generateDelete)
                .addMethod(this::generateDeleteAllEntities)
                .addMethod(this::generateDeleteAll);
    }

    //  <T extends E> T save(T entity)
    private void generateSave(Method.Builder builder) {
        // Method prototype
        builder.name("save")
                .addAnnotation(Annotations.OVERRIDE)
                .addParameter(T_ENTITY)
                .returnType(T)
                .addGenericArgument(extendsType(GENERIC_T, repositoryInfo().entity()));
        // Method body: return executor.call(em -> em.merge(entity));
        returnStatement(builder,
                        b1 -> call(b1,
                                   b2 -> statementGenerator()
                                           .addMerge(b2, ENTITY),
                                   EXECUTOR));
    }

    // <T extends E> Iterable<T> saveAll(Iterable<T> entities)
    private void generateSaveAll(Method.Builder builder) {
        // Method prototype
        builder.name("saveAll")
                .addAnnotation(Annotations.OVERRIDE)
                .addParameter(ITERABLE_T_ENTITIES)
                .addGenericArgument(extendsType(GENERIC_T, repositoryInfo().entity()))
                .returnType(ITERABLE_T);
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

    // Optional<E> findById(ID id)
    private void generateFindById(Method.Builder builder) {
        Parameter id = Parameter.builder()
                .name(ID)
                .type(repositoryInfo().id())
                .build();
        TypeName returnType = TypeName.builder()
                .type(Optional.class)
                .addTypeArgument(repositoryInfo().entity())
                .build();
        // Method prototype
        builder.name("findById")
                .addAnnotation(Annotations.OVERRIDE)
                .addParameter(id)
                .returnType(returnType);
        // Method body: return Optional.ofNullable(executor.call(em -> em.find(<E>.class, id)));
        returnStatement(builder,
                        b1 -> optionalOfNullable(b1,
                                                 b2 -> call(b2,
                                                            b3 -> statementGenerator()
                                                                    .addFind(b3,
                                                                             ID,
                                                                             repositoryInfo().entity()),
                                                            EXECUTOR
                                                 )));
    }

    // boolean existsById(ID id)
    private void generateExistsById(Method.Builder builder) {
        Parameter id = Parameter.builder()
                .name(ID)
                .type(repositoryInfo().id())
                .build();
        builder.name("existsById")
                .addAnnotation(Annotations.OVERRIDE)
                .addParameter(id)
                .returnType(TypeNames.PRIMITIVE_BOOLEAN);
        // Method body: return executor.call(em -> em.find(Critter.class, id)) != null;
        returnStatement(builder,
                        b1 -> {
                            call(b1,
                                 b2 -> statementGenerator()
                                         .addFind(b2,
                                                  ID,
                                                  repositoryInfo().entity()),
                                 EXECUTOR);
                            b1.addContent(" != null");
                        });
    }

    // Stream<E> findAll()
    private void generateFindAll(Method.Builder builder) {
        TypeName returnType = TypeName.builder()
                .type(Stream.class)
                .addTypeArgument(repositoryInfo().entity())
                .build();
        String query = queryBuilder().buildSimpleQuery(DataQuery.builder()
                                                               .projection(Projection.select())
                                                               .build());
        // Method prototype
        builder.name("findAll")
                .addAnnotation(Annotations.OVERRIDE)
                .returnType(returnType);
        // Method body: return executor.call(em -> em.createQuery("SELECT c FROM Critter c", Critter.class)
        //                      .getResultStream());
        returnStatement(builder,
                        b1 -> call(b1,
                                   b2 -> statementGenerator()
                                           .addExecuteSimpleQueryStream(b2,
                                                                        query,
                                                                        repositoryInfo().entity()),
                                   EXECUTOR));
    }

    // long count()
    private void generateCount(Method.Builder builder) {
        String query = queryBuilder().buildSimpleQuery(DataQuery.builder()
                                                               .projection(Projection.selectCount())
                                                               .build());
        // Method prototype
        builder.name("count")
                .addAnnotation(Annotations.OVERRIDE)
                .returnType(TypeNames.PRIMITIVE_LONG);
        // Method body: return executor.call(em -> em.createQuery("SELECT COUNT(c) FROM Critter c", Long.class)
        //                      .getResultStream());
        returnStatement(builder,
                        b1 -> call(b1,
                                   b2 -> statementGenerator()
                                           .addExecuteSimpleQueryItem(b2,
                                                                      query,
                                                                      TypeNames.BOXED_LONG),
                                   EXECUTOR));
    }

    // long deleteById(ID id)
    private void generateDeleteById(Method.Builder builder) {
        Parameter id = Parameter.builder()
                .name(ID)
                .type(repositoryInfo().id())
                .build();
        PersistenceGenerator.Query query = queryBuilder().buildQuery(
                DataQuery.builder()
                        .projection(Projection.delete())
                        .criteria(Criteria.builder()
                                          .condition(CriteriaCondition.createEqual(
                                                  Property.create(ID),
                                                  ID))
                                          .build())
                        .build());
        // Method prototype
        builder.name("deleteById")
                .addAnnotation(Annotations.OVERRIDE)
                .addParameter(id)
                .returnType(TypeNames.PRIMITIVE_LONG);
        // Method body: return (long) executor.call(em -> em.createQuery("DELETE FROM <E> e WHERE e.id = :id")
        //                      .setParameter("id", id)
        //                      .executeUpdate());
        returnStatement(builder,
                        b1 -> {
                            b1.addContent("(")
                                    .addContent(long.class)
                                    .addContent(") ");
                            call(b1,
                                 b2 -> statementGenerator()
                                         .addExecuteDml(b2, query),
                                 EXECUTOR);
                        });
    }

    // void delete(E entity)
    private void generateDelete(Method.Builder builder) {
        Parameter entity = Parameter.builder()
                .name(ENTITY)
                .type(repositoryInfo().entity())
                .build();
        // Method prototype
        builder.name("delete")
                .addAnnotation(Annotations.OVERRIDE)
                .addParameter(entity)
                .returnType(TypeNames.PRIMITIVE_VOID);
        // Method body: executor.run(em -> em.remove(entity));
        statement(builder,
                  b1 -> run(b1,
                            b2 -> statementGenerator()
                                    .addRemove(b2, ENTITY),
                            EXECUTOR));
    }

    // void deleteAll(Iterable<? extends E> entities)
    private void generateDeleteAllEntities(Method.Builder builder) {
        Parameter entities = Parameter.builder()
                .name(ENTITIES)
                .type(TypeName.builder()
                              .type(Iterable.class)
                              .addTypeArgument(extendsType(GENERIC_WILDCARD,
                                                           repositoryInfo().entity()))
                              .build())
                .build();
        // Method prototype
        builder.name("deleteAll")
                .addAnnotation(Annotations.OVERRIDE)
                .addParameter(entities)
                .returnType(TypeNames.PRIMITIVE_VOID);
        // Method body: executor.run(em -> entities.forEach(em::remove));
        statement(builder,
                  b1 -> run(b1,
                            b2 -> statementGenerator()
                                    .addRemoveCollection(b2, ENTITIES),
                            EXECUTOR));
    }

    // long deleteAll()
    private void generateDeleteAll(Method.Builder builder) {
        String query = queryBuilder().buildSimpleQuery(
                DataQuery.builder()
                        .projection(Projection.delete())
                        .build());
        // Method prototype
        builder.name("deleteAll")
                .addAnnotation(Annotations.OVERRIDE)
                .returnType(TypeNames.PRIMITIVE_LONG);
        // Method body: return (long) executor.call(em -> (long) em .createQuery("DELETE FROM <E> e")
        //                      .executeUpdate());
        returnStatement(builder,
                        b1 -> {
                            b1.addContent("(")
                                    .addContent(long.class)
                                    .addContent(") ");
                            call(b1,
                                 b2 -> statementGenerator()
                                         .addExecuteSimpleDml(b2, query),
                                 EXECUTOR);
                        });
    }

}

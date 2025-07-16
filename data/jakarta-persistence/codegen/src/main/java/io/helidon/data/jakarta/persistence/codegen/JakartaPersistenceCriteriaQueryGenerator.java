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
package io.helidon.data.jakarta.persistence.codegen;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import io.helidon.codegen.classmodel.Method;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.data.codegen.common.MethodParams;
import io.helidon.data.codegen.common.RepositoryInfo;
import io.helidon.data.codegen.common.spi.PersistenceGenerator;
import io.helidon.data.codegen.query.Criteria;
import io.helidon.data.codegen.query.CriteriaCondition;
import io.helidon.data.codegen.query.DataQuery;
import io.helidon.data.codegen.query.Order;
import io.helidon.data.codegen.query.Projection;
import io.helidon.data.codegen.query.ProjectionAction;

import static io.helidon.data.jakarta.persistence.codegen.JakartaPersistenceTypes.CRITERIA_BUILDER;
import static io.helidon.data.jakarta.persistence.codegen.JakartaPersistenceTypes.ORDER;
import static io.helidon.data.jakarta.persistence.codegen.JakartaPersistenceTypes.RAW_CRITERIA_DELETE;
import static io.helidon.data.jakarta.persistence.codegen.JakartaPersistenceTypes.RAW_CRITERIA_QUERY;
import static io.helidon.data.jakarta.persistence.codegen.JakartaPersistenceTypes.RAW_EXPRESSION;
import static io.helidon.data.jakarta.persistence.codegen.JakartaPersistenceTypes.RAW_ROOT;

/**
 * Jakarta Persistence Criteria query code generator.
 */
final class JakartaPersistenceCriteriaQueryGenerator extends JakartaPersistenceBaseBuilder {

    private final MethodParams methodParams;
    private final DataQuery dataQuery;
    private final TypeName returnType;
    private final List<PersistenceGenerator.QuerySettings> settings;

    private JakartaPersistenceCriteriaQueryGenerator(RepositoryInfo repositoryInfo,
                                                     MethodParams methodParams,
                                                     DataQuery dataQuery,
                                                     TypeName returnType) {
        super(repositoryInfo);
        this.methodParams = methodParams;
        this.dataQuery = dataQuery;
        this.returnType = returnType;
        this.settings = new ArrayList<>();
    }

    static JakartaPersistenceCriteriaQueryGenerator create(RepositoryInfo repositoryInfo,
                                                           MethodParams methodParams,
                                                           DataQuery dataQuery,
                                                           TypeName returnType) {
        return new JakartaPersistenceCriteriaQueryGenerator(repositoryInfo, methodParams, dataQuery, returnType);
    }

    List<PersistenceGenerator.QuerySettings> settings() {
        return settings;
    }

    // Create single criteria query with settings applied.
    void criteriaQuery(Method.Builder builder) {
        builder.addContent(CRITERIA_BUILDER)
                .addContentLine(" cb = em.getCriteriaBuilder();");
        buildProjection(builder);
        dataQuery.criteria().ifPresent(
                criteria -> buildCriteria(builder, criteria, "stmt"));
        buildOrder(builder);
        buildCreateQuery(builder);
        settings.addAll(setParameter().values());
    }

    List<PersistenceGenerator.QuerySettings> dynamicSliceQuery(Method.Builder builder,
                                                               String dataQueryStatement) {
        if (dataQuery.projection().action() != ProjectionAction.Select) {
            throw new IllegalArgumentException("Can't build dynamic query for " + dataQuery.projection().action() + " statement");
        }
        builder.addContent(CRITERIA_BUILDER)
                .addContentLine(" cb = em.getCriteriaBuilder();");
        appendCreateQueryInstance(builder, returnType, dataQueryStatement);
        appendCreateRootFromQuery(builder, repositoryInfo(), "root", dataQueryStatement);
        // Projection definition requires projection action to be ProjectionAction.Select (already tested)
        appendSetSelectExpression(builder, dataQuery.projection(), dataQueryStatement, "root");
        dataQuery.criteria().ifPresent(
                criteria -> buildCriteria(builder, criteria, "stmt"));
        appendCreateOrderList(builder, "orderBy");
        dataQuery.order()
                .ifPresent(order -> appendOrderExpression(builder, order, "orderBy"));
        methodParams.order()
                .ifPresent(sort -> appendSortExpression(builder, sort, "orderBy"));
        appendSetOrderBy(builder,
                         dataQuery.order().isEmpty() || dataQuery.order().get().expressions().isEmpty(),
                         "orderBy",
                         dataQueryStatement);
        settings.addAll(setParameter().values());
        return settings();
    }

    List<PersistenceGenerator.QuerySettings> dynamicPageQueries(Method.Builder builder,
                                                                String dataQueryStatement,
                                                                String countQueryStatement) {
        if (dataQuery.projection().action() != ProjectionAction.Select) {
            throw new IllegalArgumentException("Can't build dynamic query for " + dataQuery.projection().action() + " statement");
        }
        builder.addContent(CRITERIA_BUILDER)
                .addContentLine(" cb = em.getCriteriaBuilder();");
        appendCreateQueryInstance(builder, returnType, dataQueryStatement);
        appendCreateQueryInstance(builder, NUMBER, countQueryStatement);
        appendCreateRootFromQuery(builder, repositoryInfo(), "root", dataQueryStatement);
        // Projection definition requires projection action to be ProjectionAction.Select (already tested)
        appendSetSelectExpression(builder, dataQuery.projection(), dataQueryStatement, "root");
        appendSetSelectCountExpression(builder, dataQuery.projection(), countQueryStatement, "root");
        dataQuery.criteria().ifPresent(criteria -> {
            appendCriteriaInstance(builder, criteria, "criteria");
            statement(builder,
                      b1 ->
                              where(b1,
                                    b2 -> identifier(b2, "criteria"),
                                    dataQueryStatement));
            statement(builder,
                      b1 ->
                              where(b1,
                                    b2 -> identifier(b2, "criteria"),
                                    countQueryStatement));
        });
        appendCreateOrderList(builder, "orderBy");
        dataQuery.order()
                .ifPresent(order -> appendOrderExpression(builder, order, "orderBy"));
        methodParams.order()
                .ifPresent(sort -> appendSortExpression(builder, sort, "orderBy"));
        appendSetOrderBy(builder,
                         dataQuery.order().isEmpty() || dataQuery.order().get().expressions().isEmpty(),
                         "orderBy",
                         dataQueryStatement);
        settings.addAll(setParameter().values());
        return settings();
    }

    // Generate projection target based on Projection content:
    // - "root" when no parameter is present
    // - "root.get(<parameter>)" when parameter is present
    private static void projectionTarget(Method.Builder builder, Projection projection, String rootName) {
        projection.property().ifPresentOrElse(
                property -> builder.addContent(rootName)
                        .addContent(".get(\"")
                        .addContent(property.name().toString())
                        .addContent("\")"),
                () -> builder.addContent(rootName)
        );
    }

    private static void appendSetOrderBy(Method.Builder builder, boolean addIf, String orderName, String statementName) {
        if (addIf) {
            builder.addContent("if (!")
                    .addContent(orderName)
                    .addContentLine(".isEmpty()) {");
        }
        builder.addContent(statementName)
                .addContent(".orderBy(")
                .addContent(orderName)
                .addContentLine(");");
        if (addIf) {
            builder.addContentLine("}");
        }
    }

    // Generate dynamic ordering expression for Sort
    private static void appendSortExpression(Method.Builder builder, TypedElementInfo sortInfo, String orderName) {
        builder.addContent(sortInfo.elementName())
                .addContentLine(".orderBy().forEach(order -> ");
        increasePadding(builder, 2);
        builder.addContent(orderName)
                .addContent(".add(");
        builder.addContent("switch (order.direction()) ")
                .addContentLine("{");
        builder.addContentLine("case ASC -> cb.asc(root.get(order.property()));");
        builder.addContentLine("case DESC -> cb.desc(root.get(order.property()));");
        builder.addContent("}")
                .addContent(")");
        decreasePadding(builder, 2);
        builder.addContentLine(");");
    }

    // Generate "<dataQueryStatement>.select(<content>)"
    private static void select(Method.Builder builder, Consumer<Method.Builder> content, String dataQueryStatement) {
        builder.addContent(dataQueryStatement)
                .addContent(".select(");
        content.accept(builder);
        builder.addContent(")");
    }

    // Generate "<dataQueryStatement>.where(<content>)"
    private static void where(Method.Builder builder, Consumer<Method.Builder> content, String dataQueryStatement) {
        builder.addContent(dataQueryStatement)
                .addContent(".where(");
        content.accept(builder);
        builder.addContent(")");
    }

    // Generate "cq.count(<content>)" or "cq.countDistinct(<content>)" depending on projection.distinct()
    private static void count(Method.Builder builder, Consumer<Method.Builder> content, Projection projection) {
        builder.addContent("cb.")
                .addContent(projection.distinct() ? "countDistinct" : "count")
                .addContent("(");
        content.accept(builder);
        builder.addContent(")");
    }

    // CriteriaQuery<returnType> <name> = cb.createQuery(<returnType>.class);
    private static void appendCreateQueryInstance(Method.Builder builder, TypeName returnType, String name) {
        TypeName criteriaQuery = TypeName.builder()
                .from(RAW_CRITERIA_QUERY)
                .addTypeArgument(returnType)
                .build();
        builder.addContent(criteriaQuery)
                .addContent(" ")
                .addContent(name)
                .addContent(" = cb.createQuery(")
                .addContent(returnType)
                .addContentLine(".class);");
    }

    // CriteriaQuery<entityType> <name> = cb.createQuery(<entityType>.class);
    private static void appendCreateDeleteInstance(Method.Builder builder, TypeName entityType, String name) {
        TypeName criteriaDelete = TypeName.builder()
                .from(RAW_CRITERIA_DELETE)
                .addTypeArgument(entityType)
                .build();
        builder.addContent(criteriaDelete)
                .addContent(" ")
                .addContent(name)
                .addContent(" = cb.createCriteriaDelete(")
                .addContent(entityType)
                .addContentLine(".class);");
    }

    // Root<Entity> root = cq.from(Entity.class);
    private static void appendCreateRootFromQuery(Method.Builder builder,
                                                  RepositoryInfo repositoryInfo,
                                                  String name,
                                                  String queryName) {
        TypeName root = TypeName.builder()
                .from(RAW_ROOT)
                .addTypeArgument(repositoryInfo.entity())
                .build();
        builder.addContent(root)
                .addContent(" ")
                .addContent(name)
                .addContent(" = ")
                .addContent(queryName)
                .addContent(".from(")
                .addContent(repositoryInfo.entity())
                .addContentLine(".class);");
    }

    // List<Order> <name> = new ArrayList<>();
    private static void appendCreateOrderList(Method.Builder builder, String name) {
        TypeName listType = TypeName.builder()
                .type(List.class)
                .addTypeArgument(ORDER)
                .build();
        builder.addContent(listType)
                .addContent(" ")
                .addContent(name)
                .addContent(" = new ")
                .addContent(ArrayList.class)
                .addContentLine("<>();");
    }

    // Generate static ordering expression:
    // orderBy.add(cb.asc(root.get(<property>)));
    private static void appendOrderExpression(Method.Builder builder, Order order, String orderName) {
        order.expressions().forEach(expression -> {
            builder.addContent(orderName)
                    .addContent(".add(cb.");
            switch (expression.operator()) {
            case ASC:
                builder.addContent("asc");
                break;
            case DESC:
                builder.addContent("desc");
                break;
            default:
                throw new UnsupportedOperationException(
                        "Unknown order direction operator " + expression.operator());
            }
            builder.addContent("(root.get(\"")
                    .addContent(expression.property().name().toString())
                    .addContentLine("\")));");
        });
    }

    // Generate "cq.max(<content>)"
    private static void max(Method.Builder builder, Consumer<Method.Builder> content) {
        builder.addContent("cb.max(");
        content.accept(builder);
        builder.addContent(")");
    }

    // Generate "cq.min(<content>)"
    private static void min(Method.Builder builder, Consumer<Method.Builder> content) {
        builder.addContent("cb.min(");
        content.accept(builder);
        builder.addContent(")");
    }

    // Generate "cq.sum(<content>)"
    private static void sum(Method.Builder builder, Consumer<Method.Builder> content) {
        builder.addContent("cb.sum(");
        content.accept(builder);
        builder.addContent(")");
    }

    // Generate "cq.avg(<content>)"
    private static void avg(Method.Builder builder, Consumer<Method.Builder> content) {
        builder.addContent("cb.avg(");
        content.accept(builder);
        builder.addContent(")");
    }

    // Generate "cq.gt(<content1>, <content2>)"
    private static void gt(Method.Builder builder, Consumer<Method.Builder> content1, Consumer<Method.Builder> content2) {
        builder.addContent("cb.gt(");
        content1.accept(builder);
        builder.addContent(", ");
        content2.accept(builder);
        builder.addContent(")");
    }

    private void buildProjection(Method.Builder builder) {
        Projection projection = dataQuery.projection();
        switch (projection.action()) {
        case Select:
            appendSelect(builder, projection);
            break;
        // Currently dynamic queries support only Sort, so they do not make sense to be used with Delete or Update.
        // Delete is partially implemented to show future solution, but an attempt to call it will cause an exception.
        case Delete:
            //appendDelete(builder);
            throw new UnsupportedOperationException("Sort parameter used in delete statement");
            //break;
        case Update:
            // Not implemented yet
            throw new UnsupportedOperationException("Sort parameter used in update statement");
        default:
            throw new UnsupportedOperationException("Unknown query action " + projection.action());
        }
    }

    private void buildCriteria(Method.Builder builder, Criteria criteria, String name) {
        CriteriaBuilder criteriaBuilder = new CriteriaBuilder();
        criteriaBuilder.first(criteria.first());
        criteria.next().forEach(
                nextExpression -> {
                    switch (nextExpression.operator()) {
                    case AND:
                        criteriaBuilder.and(nextExpression.criteria());
                        break;
                    case OR:
                        criteriaBuilder.or(nextExpression.criteria());
                        break;
                    default:
                        throw new UnsupportedOperationException(
                                "Unknown criteria logical operator " + nextExpression.operator());
                    }
                });

        Iterator<TypedElementInfo> parameters = methodParams.parameters().iterator();
        statement(builder,
                  b1 -> where(b1,
                              b2 -> criteriaBuilder.build(b2, parameters, setParameter()),
                              name));
    }

    private void buildOrder(Method.Builder builder) {
        // Ordering is valid only for query statement.
        if (dataQuery.projection().action() == ProjectionAction.Select) {
            appendCreateOrderList(builder, "orderBy");
            dataQuery.order().ifPresent(
                    order -> appendOrderExpression(builder, order, "orderBy"));
            methodParams.order().ifPresent(
                    sort -> appendSortExpression(builder, sort, "orderBy"));
            appendSetOrderBy(builder,
                             dataQuery.order().isEmpty() || dataQuery.order().get().expressions().isEmpty(),
                             "orderBy",
                             "stmt");
        }
    }

    private void buildCreateQuery(Method.Builder builder) {
        Projection projection = dataQuery.projection();
        switch (projection.action()) {
        case Select:
        case Delete:
            if (!returnType.equals(TypeNames.PRIMITIVE_VOID) && !returnType.equals(TypeNames.BOXED_VOID)) {
                builder.addContent("return ");
            }
            builder.addContentLine("em.createQuery(stmt)");
            break;
        case Update:
            throw new UnsupportedOperationException("Update statement is not supported");
        default:
            throw new UnsupportedOperationException("Unknown query action " + projection.action());
        }
    }

    // CriteriaDelete<Entity> and Root<Entity> instances
    private void appendDelete(Method.Builder builder) {
        appendCreateDeleteInstance(builder, repositoryInfo().entity(), "stmt");
        // Root instance is required only for criteria
        if (dataQuery.criteria().isPresent()) {
            appendCreateRootFromQuery(builder, repositoryInfo(), "root", "stmt");
        }
    }

    // CriteriaQuery<Result>and Root<Entity> instances with projection setting
    private void appendSelect(Method.Builder builder, Projection projection) {
        appendCreateQueryInstance(builder, returnType, "stmt");
        appendCreateRootFromQuery(builder, repositoryInfo(), "root", "stmt");
        appendSetSelectExpression(builder, projection, "stmt", "root");
    }

    // Generate projection part of the CriteriaQuery
    private void appendSetSelectExpression(Method.Builder builder,
                                           Projection projection,
                                           String queryName,
                                           String rootName) {
        statement(builder,
                  b1 -> select(b1,
                               b2 -> appendSelectExpression(b2, projection, rootName),
                               queryName));
    }

    // Generate projection part of the CriteriaQuery
    private void appendSetSelectCountExpression(Method.Builder builder,
                                                Projection projection,
                                                String queryName,
                                                String rootName) {
        statement(builder,
                  b1 -> select(b1,
                               b2 -> count(b2,
                                           b3 -> appendSelectExpression(b3, projection, rootName),
                                           projection),
                               queryName));
    }

    // Generate projection expression of the CriteriaQuery
    private void appendSelectExpression(Method.Builder builder, Projection projection, String rootName) {
        projection.expression().ifPresentOrElse(
                expression -> {
                    switch (expression.operator()) {
                    case First:
                        expression.parameter().ifPresentOrElse(
                                parameter -> {
                                    if (parameter.type() != Integer.class) {
                                        throw new IllegalArgumentException(
                                                "First projection operator parameter is not Integer");
                                    }
                                    projectionTarget(builder, projection, rootName);
                                    settings.add(new JakartaPersistenceBaseQueryBuilder.Limit((Integer) parameter.value()));
                                },
                                () -> {
                                    throw new IllegalArgumentException(
                                            "Missing First projection operator parameter");
                                }
                        );
                        break;
                    case Count:
                    case Exists:
                        count(builder, b -> projectionTarget(b, projection, rootName), projection);
                        break;
                    case Max:
                        max(builder, b -> projectionTarget(b, projection, rootName));
                        break;
                    case Min:
                        min(builder, b -> projectionTarget(b, projection, rootName));
                        break;
                    case Sum:
                        sum(builder, b -> projectionTarget(b, projection, rootName));
                        break;
                    case Avg:
                        if (returnType.equals(TypeNames.PRIMITIVE_DOUBLE)
                                || returnType.equals(TypeNames.BOXED_DOUBLE)
                                || returnType.equals(NUMBER)) {
                            avg(builder, b -> projectionTarget(b, projection, rootName));
                            // This must be caught in validation stage so hitting this exception means bug in the code
                        } else {
                            throw new UnsupportedOperationException(
                                    "Jakarta Persistence Criteria API avg function does not support " + returnType);
                        }
                        break;
                    default:
                        throw new UnsupportedOperationException(
                                "Unknown projection expression operator " + expression.operator());
                    }
                },
                () -> projectionTarget(builder, projection, rootName)
        );
    }

    private void appendCriteriaInstance(Method.Builder builder, Criteria criteria, String name) {
        CriteriaBuilder criteriaBuilder = new CriteriaBuilder();
        criteriaBuilder.first(criteria.first());
        criteria.next().forEach(
                nextExpression -> {
                    switch (nextExpression.operator()) {
                    case AND:
                        criteriaBuilder.and(nextExpression.criteria());
                        break;
                    case OR:
                        criteriaBuilder.or(nextExpression.criteria());
                        break;
                    default:
                        throw new UnsupportedOperationException(
                                "Unknown criteria logical operator " + nextExpression.operator());
                    }
                });

        TypeName expressionType = TypeName.builder()
                .from(RAW_EXPRESSION)
                .addTypeArgument(TypeNames.BOXED_BOOLEAN)
                .build();
        Iterator<TypedElementInfo> parameters = methodParams.parameters().iterator();
        statement(builder, b -> {
            b.addContent(expressionType)
                    .addContent(" ")
                    .addContent(name)
                    .addContent(" = ");
            criteriaBuilder.build(b, parameters, setParameter());
        });
    }

    // Expression interface
    private interface Expression {

        void build(Method.Builder builder,
                   Iterator<TypedElementInfo> parameters,
                   Map<CharSequence, PersistenceGenerator.QuerySettings> setParameter);

    }

    // Jakarta Persistence CriteriaBuilder does not allow mix of AND/OR operators without brackets
    // Because SQL gives AND higher priority than OR, we can transform such an expression to AND expressions
    // being in brackets, e.g. "<condition> OR (<condition> AND <condition> AND <condition>) OR (<condition> AND <condition>)".
    // This builder extracts groups of conditions joined with AND to generate separate and(...) API call for them.
    private static final class CriteriaBuilder {

        private final List<Expression> expressions;
        // Last expression added
        private Condition last;
        // Whether last expression was joined with OR operator.
        private boolean lastOr;
        // New AND condition being built
        private And and;

        private CriteriaBuilder() {
            expressions = new ArrayList<>();
            last = null;
            and = null;
        }

        void build(Method.Builder builder,
                   Iterator<TypedElementInfo> parameters,
                   Map<CharSequence, PersistenceGenerator.QuerySettings> setParameter) {
            // Finish expression transformation first
            if (last == null) {
                throw new IllegalStateException("Previous condition is missing");
            }
            if (lastOr) {
                expressions.add(last);
            } else {
                and.add(last);
                expressions.add(and);
                and = null;
            }
            // Block subsequent build calls
            last = null;
            // Generate criteria expression
            //builder.addContentLine("stmt.where(");
            increasePadding(builder, 1);
            if (expressions.size() > 1) {
                builder.addContentLine("cb.or(");
                increasePadding(builder, 1);
            }
            boolean firstExpression = true;
            for (Expression expression : expressions) {
                if (firstExpression) {
                    firstExpression = false;
                } else {
                    builder.addContentLine(",");
                }
                expression.build(builder, parameters, setParameter);
            }
            if (expressions.size() > 1) {
                decreasePadding(builder, 1);
                builder.addContent(")");
            }
            decreasePadding(builder, 1);
            //builder.addContentLine(");");
        }

        private void first(CriteriaCondition first) {
            if (last != null) {
                throw new IllegalStateException("First condition was already set");
            }
            last = new Condition(first);
            lastOr = true;
        }

        private void or(CriteriaCondition condition) {
            if (last == null) {
                throw new IllegalStateException("Previous condition is missing");
            }
            if (lastOr) {
                expressions.add(last);
            } else {
                and.add(last);
                expressions.add(and);
                // Just note that AND expression was spent
                and = null;
            }
            lastOr = true;
            last = new Condition(condition);
        }

        private void and(CriteriaCondition condition) {
            if (last == null) {
                throw new IllegalStateException("Previous condition is missing");
            }
            if (lastOr) {
                if (and != null) {
                    throw new IllegalStateException("Found AND expression already in progress");
                }
                and = new And(last);
            } else {
                and.add(last);
            }
            lastOr = false;
            last = new Condition(condition);
        }
    }

    // Condition expression
    private static final class Condition implements Expression {

        private final CriteriaCondition condition;

        private Condition(CriteriaCondition condition) {
            this.condition = condition;
        }

        @Override
        public void build(Method.Builder builder,
                          Iterator<TypedElementInfo> parameters,
                          Map<CharSequence, PersistenceGenerator.QuerySettings> setParameter) {
            switch (condition.operator()) {
            case Equal:
                criteriaEqual(builder, condition, parameters, setParameter);
                break;
            case Contains:
                criteriaContains(builder, condition, parameters, setParameter);
                break;
            case EndsWith:
                criteriaEndsWith(builder, condition, parameters, setParameter);
                break;
            case StartsWith:
                criteriaStartsWith(builder, condition, parameters, setParameter);
                break;
            case Before:
            case LessThan:
                criteriaLessThan(builder, condition, parameters, setParameter);
                break;
            case LessThanEqual:
                criteriaLessThanEqual(builder, condition, parameters, setParameter);
                break;
            case After:
            case GreaterThan:
                criteriaGreaterThan(builder, condition, parameters, setParameter);
                break;
            case GreaterThanEqual:
                criteriaGreaterThanEqual(builder, condition, parameters, setParameter);
                break;
            case Between:
                criteriaBetween(builder, condition, parameters, setParameter);
                break;
            case Like:
                criteriaLike(builder, condition, parameters, setParameter);
                break;
            case In:
                criteriaIn(builder, condition, parameters, setParameter);
                break;
            case Empty:
                criteriaEmpty(builder, condition);
                break;
            case Null:
                criteriaNull(builder, condition);
                break;
            case True:
                criteriaTrue(builder, condition);
                break;
            case False:
                criteriaFalse(builder, condition);
                break;
            default:
                throw new UnsupportedOperationException(
                        "Unknown criteria expression operator " + condition.operator());
            }
        }

        private static void criteriaEqual(Method.Builder builder,
                                          CriteriaCondition condition,
                                          Iterator<TypedElementInfo> parameters,
                                          Map<CharSequence, PersistenceGenerator.QuerySettings> setParameter) {
            not(builder,
                b -> equal(b, condition, parameters, setParameter),
                condition.not());
        }

        private static void criteriaContains(Method.Builder builder,
                                             CriteriaCondition condition,
                                             Iterator<TypedElementInfo> parameters,
                                             Map<CharSequence, PersistenceGenerator.QuerySettings> setParameter) {
            not(builder,
                b1 -> like(b1,
                           b2 -> containsConcat(b2, condition, parameters, setParameter),
                           condition),
                condition.not());
        }

        private static void criteriaEndsWith(Method.Builder builder,
                                             CriteriaCondition condition,
                                             Iterator<TypedElementInfo> parameters,
                                             Map<CharSequence, PersistenceGenerator.QuerySettings> setParameter) {
            not(builder,
                b1 -> like(b1,
                           b2 -> endsWithConcat(b2, condition, parameters, setParameter),
                           condition),
                condition.not());

        }

        private static void criteriaStartsWith(Method.Builder builder,
                                               CriteriaCondition condition,
                                               Iterator<TypedElementInfo> parameters,
                                               Map<CharSequence, PersistenceGenerator.QuerySettings> setParameter) {
            not(builder,
                b1 -> like(b1,
                           b2 -> startsWithConcat(b2, condition, parameters, setParameter),
                           condition),
                condition.not());

        }

        private static void criteriaLessThan(Method.Builder builder,
                                             CriteriaCondition condition,
                                             Iterator<TypedElementInfo> parameters,
                                             Map<CharSequence, PersistenceGenerator.QuerySettings> setParameter) {
            not(builder,
                b -> lessThan(b, condition, parameters, setParameter),
                condition.not());
        }

        private static void criteriaLessThanEqual(Method.Builder builder,
                                                  CriteriaCondition condition,
                                                  Iterator<TypedElementInfo> parameters,
                                                  Map<CharSequence, PersistenceGenerator.QuerySettings> setParameter) {
            not(builder,
                b -> lessThanOrEqualTo(b, condition, parameters, setParameter),
                condition.not());
        }

        private static void criteriaGreaterThan(Method.Builder builder,
                                                CriteriaCondition condition,
                                                Iterator<TypedElementInfo> parameters,
                                                Map<CharSequence, PersistenceGenerator.QuerySettings> setParameter) {
            not(builder,
                b -> greaterThan(b, condition, parameters, setParameter),
                condition.not());
        }

        private static void criteriaGreaterThanEqual(Method.Builder builder,
                                                     CriteriaCondition condition,
                                                     Iterator<TypedElementInfo> parameters,
                                                     Map<CharSequence, PersistenceGenerator.QuerySettings> setParameter) {
            not(builder,
                b -> greaterThanOrEqualTo(b, condition, parameters, setParameter),
                condition.not());
        }

        private static void criteriaBetween(Method.Builder builder,
                                            CriteriaCondition condition,
                                            Iterator<TypedElementInfo> parameters,
                                            Map<CharSequence, PersistenceGenerator.QuerySettings> setParameter) {
            not(builder,
                b -> between(b, condition, parameters, setParameter),
                condition.not());
        }

        private static void criteriaLike(Method.Builder builder,
                                         CriteriaCondition condition,
                                         Iterator<TypedElementInfo> parameters,
                                         Map<CharSequence, PersistenceGenerator.QuerySettings> setParameter) {
            not(builder,
                b1 -> like(b1,
                           b2 -> firstParameter(b2, condition, parameters, setParameter),
                           condition),
                condition.not());
        }

        private static void criteriaIn(Method.Builder builder,
                                       CriteriaCondition condition,
                                       Iterator<TypedElementInfo> parameters,
                                       Map<CharSequence, PersistenceGenerator.QuerySettings> setParameter) {
            not(builder,
                b -> in(b, condition, parameters, setParameter),
                condition.not());
        }

        private static void criteriaEmpty(Method.Builder builder,
                                          CriteriaCondition condition) {
            not(builder,
                b -> isEmpty(b, condition),
                condition.not());
        }

        private static void criteriaNull(Method.Builder builder,
                                         CriteriaCondition condition) {
            not(builder,
                b -> isNull(b, condition),
                condition.not());
        }

        private static void criteriaTrue(Method.Builder builder,
                                         CriteriaCondition condition) {
            not(builder,
                b -> isTrue(b, condition),
                condition.not());
        }

        private static void criteriaFalse(Method.Builder builder,
                                          CriteriaCondition condition) {
            not(builder,
                b -> isFalse(b, condition),
                condition.not());
        }

        // Generate "cb.equal(<property>, <parameter>)"
        //  - <property> is taken from condition
        //  - <parameter> is taken from condition if exists or from method parameters
        private static void equal(Method.Builder builder,
                                  CriteriaCondition condition,
                                  Iterator<TypedElementInfo> parameters,
                                  Map<CharSequence, PersistenceGenerator.QuerySettings> setParameter) {
            builder.addContent("cb.equal(");
            property(builder, condition);
            builder.addContent(", ");
            firstParameter(builder, condition, parameters, setParameter);
            builder.addContent(")");
        }

        // Generate "cb.lessThan(<property>, <parameter>)"
        //  - <property> is taken from condition
        //  - <parameter> is taken from condition if exists or from method parameters
        private static void lessThan(Method.Builder builder,
                                     CriteriaCondition condition,
                                     Iterator<TypedElementInfo> parameters,
                                     Map<CharSequence, PersistenceGenerator.QuerySettings> setParameter) {
            builder.addContent("cb.lessThan(");
            property(builder, condition);
            builder.addContent(", ");
            firstParameter(builder, condition, parameters, setParameter);
            builder.addContent(")");
        }

        // Generate "cb.lessThanOrEqualTo(<property>, <parameter>)"
        //  - <property> is taken from condition
        //  - <parameter> is taken from condition if exists or from method parameters
        private static void lessThanOrEqualTo(Method.Builder builder,
                                              CriteriaCondition condition,
                                              Iterator<TypedElementInfo> parameters,
                                              Map<CharSequence, PersistenceGenerator.QuerySettings> setParameter) {
            builder.addContent("cb.lessThanOrEqualTo(");
            property(builder, condition);
            builder.addContent(", ");
            firstParameter(builder, condition, parameters, setParameter);
            builder.addContent(")");
        }

        // Generate "cb.greaterThan(<property>, <parameter>)"
        //  - <property> is taken from condition
        //  - <parameter> is taken from condition if exists or from method parameters
        private static void greaterThan(Method.Builder builder,
                                        CriteriaCondition condition,
                                        Iterator<TypedElementInfo> parameters,
                                        Map<CharSequence, PersistenceGenerator.QuerySettings> setParameter) {
            builder.addContent("cb.greaterThan(");
            property(builder, condition);
            builder.addContent(", ");
            firstParameter(builder, condition, parameters, setParameter);
            builder.addContent(")");
        }

        // Generate "cb.greaterThanOrEqualTo(<property>, <parameter>)"
        //  - <property> is taken from condition
        //  - <parameter> is taken from condition if exists or from method parameters
        private static void greaterThanOrEqualTo(Method.Builder builder,
                                                 CriteriaCondition condition,
                                                 Iterator<TypedElementInfo> parameters,
                                                 Map<CharSequence, PersistenceGenerator.QuerySettings> setParameter) {
            builder.addContent("cb.greaterThanOrEqualTo(");
            property(builder, condition);
            builder.addContent(", ");
            firstParameter(builder, condition, parameters, setParameter);
            builder.addContent(")");
        }

        // Generate "cb.between(<property>, <parameter1>, <parameter1>)"
        //  - <property> is taken from condition
        //  - <parameter> is taken from condition if exists or from method parameters
        private static void between(Method.Builder builder,
                                    CriteriaCondition condition,
                                    Iterator<TypedElementInfo> parameters,
                                    Map<CharSequence, PersistenceGenerator.QuerySettings> setParameter) {
            builder.addContentLine("cb.between(");
            increasePadding(builder, 1);
            property(builder, condition);
            builder.addContentLine(",");
            firstParameter(builder, condition, parameters, setParameter);
            builder.addContentLine(",");
            secondParameter(builder, condition, parameters, setParameter);
            decreasePadding(builder, 1);
            builder.addContent(")");
        }

        // Generate "cb.in(<property>, <parameter>)"
        //  - <property> is taken from condition
        //  - <parameter> is taken from condition if exists or from method parameters
        private static void in(Method.Builder builder,
                               CriteriaCondition condition,
                               Iterator<TypedElementInfo> parameters,
                               Map<CharSequence, PersistenceGenerator.QuerySettings> setParameter) {
            property(builder, condition);
            builder.addContent(".in(");
            firstParameter(builder, condition, parameters, setParameter);
            builder.addContent(")");
        }

        // Generate "cb.like(<property>, <pattern>)"
        //  - <property> is taken from condition
        //  - <pattern> is build using provided Consumer<Method.Builder>
        private static void like(Method.Builder builder,
                                 Consumer<Method.Builder> pattern,
                                 CriteriaCondition condition) {
            builder.addContentLine("cb.like(");
            increasePadding(builder, 1);
            property(builder, condition);
            builder.addContentLine(",");
            pattern.accept(builder);
            decreasePadding(builder, 1);
            builder.addContent(")");
        }

        // Generate "cb.isEmpty(<property>)"
        //  - <property> is taken from condition
        private static void isEmpty(Method.Builder builder,
                                    CriteriaCondition condition) {
            builder.addContent("cb.isEmpty(");
            property(builder, condition);
            builder.addContent(")");
        }

        // Generate "cb.isNull(<property>)"
        //  - <property> is taken from condition
        private static void isNull(Method.Builder builder,
                                   CriteriaCondition condition) {
            builder.addContent("cb.isNull(");
            property(builder, condition);
            builder.addContent(")");
        }

        // Generate "cb.isTrue(<property>)"
        //  - <property> is taken from condition
        private static void isTrue(Method.Builder builder,
                                   CriteriaCondition condition) {
            builder.addContent("cb.isTrue(");
            property(builder, condition);
            builder.addContent(")");
        }

        // Generate "cb.isFalse(<property>)"
        //  - <property> is taken from condition
        private static void isFalse(Method.Builder builder,
                                    CriteriaCondition condition) {
            builder.addContent("cb.isFalse(");
            property(builder, condition);
            builder.addContent(")");
        }

        // Criteria Contains specific concatenated expressions
        // - Jakarta Persistence 3.2 compliant code
        //   cb.concat(List.of(
        //        cb.literal("%"),
        //        <parameter>,
        //        cb.literal("%"))
        // - Jakarta Persistence 3.1 workaround
        //   cb.concat(
        //        cb.literal("%"),
        //        cb.concat(
        //            <parameter>,
        //            cb.literal("%")))));
        private static void containsConcat(Method.Builder builder,
                                           CriteriaCondition condition,
                                           Iterator<TypedElementInfo> parameters,
                                           Map<CharSequence, PersistenceGenerator.QuerySettings> setParameter) {
            // Jakarta Persistence 3.2 compliant code disabled
            /*
            builder.addContent("cb.concat(")
                    .addContent(List.class)
                    .addContentLine(".of(");
            increasePadding(builder, 1);
            builder.addContentLine("cb.literal(\"%\"), ");
            firstParameter(builder, condition, parameters, setParameter);
            builder.addContentLine(",")
                    .addContent("cb.literal(\"%\")");
            decreasePadding(builder, 1);
            builder.addContent("))");
            */
            // Jakarta Persistence 3.1 workaround
            builder.addContentLine("cb.concat(");
            increasePadding(builder, 1);
            builder.addContentLine("cb.literal(\"%\"), ");
            builder.addContentLine("cb.concat(");
            increasePadding(builder, 1);
            firstParameter(builder, condition, parameters, setParameter);
            builder.addContentLine(",")
                    .addContent("cb.literal(\"%\")");
            decreasePadding(builder, 1);
            builder.addContent("))");
        }

        // Criteria EndsWith specific concatenated expressions
        //   cb.concat(List.of(
        //        cb.literal("%"),
        //        <parameter>)
        private static void endsWithConcat(Method.Builder builder,
                                           CriteriaCondition condition,
                                           Iterator<TypedElementInfo> parameters,
                                           Map<CharSequence, PersistenceGenerator.QuerySettings> setParameter) {
            builder.addContentLine("cb.concat(");
            increasePadding(builder, 1);
            builder.addContentLine("cb.literal(\"%\"), ");
            firstParameter(builder, condition, parameters, setParameter);
            decreasePadding(builder, 1);
            builder.addContent(")");
        }

        // Criteria StartsWith specific concatenated expressions
        //   cb.concat(List.of(
        //        <parameter>,
        //        cb.literal("%"))
        private static void startsWithConcat(Method.Builder builder,
                                             CriteriaCondition condition,
                                             Iterator<TypedElementInfo> parameters,
                                             Map<CharSequence, PersistenceGenerator.QuerySettings> setParameter) {
            builder.addContentLine("cb.concat(");
            increasePadding(builder, 1);
            firstParameter(builder, condition, parameters, setParameter);
            builder.addContentLine(",")
                    .addContent("cb.literal(\"%\")");
            decreasePadding(builder, 1);
            builder.addContent(")");
        }

        // Generate "cb.not(<content>)" when not is true or "<content>" when not is false
        private static void not(Method.Builder builder, Consumer<Method.Builder> content, boolean not) {
            if (not) {
                builder.addContent("cb.not(");
            }
            content.accept(builder);
            if (not) {
                builder.addContent(")");
            }
        }

        // Generate "cb.upper(<content>)" when ignoreCase is true or "<content>" when ignoreCase is false
        private static void ignoreCase(Method.Builder builder, Consumer<Method.Builder> content, boolean ignoreCase) {
            if (ignoreCase) {
                builder.addContent("cb.upper(");
            }
            content.accept(builder);
            if (ignoreCase) {
                builder.addContent(")");
            }
        }

        // Generate "root.get("name")"
        private static void rootGet(Method.Builder builder, CharSequence name) {
            builder.addContent("root.get(\"")
                    .addContent(name.toString())
                    .addContent("\")");
        }

        // Generate condition property code
        private static void property(Method.Builder builder,
                                     CriteriaCondition condition) {
            ignoreCase(builder,
                       b -> rootGet(b, condition.property().name()),
                       condition.ignoreCase());
        }

        // First criteria parameter
        private static void firstParameter(Method.Builder builder,
                                           CriteriaCondition condition,
                                           Iterator<TypedElementInfo> parameters,
                                           Map<CharSequence, PersistenceGenerator.QuerySettings> setParameter) {
            condition.parameters().ifPresentOrElse(
                    criteriaParameters -> parameter(builder, condition, criteriaParameters.get(0), setParameter),
                    () -> parameter(builder, condition, parameters.next(), setParameter));
        }

        // Second criteria parameter
        private static void secondParameter(Method.Builder builder,
                                            CriteriaCondition condition,
                                            Iterator<TypedElementInfo> parameters,
                                            Map<CharSequence, PersistenceGenerator.QuerySettings> setParameter) {
            condition.parameters().ifPresentOrElse(
                    criteriaParameters -> parameter(builder, condition, criteriaParameters.get(1), setParameter),
                    () -> parameter(builder, condition, parameters.next(), setParameter));
        }

        // Query parameters extracted from query method have class information present
        private static void parameter(Method.Builder builder,
                                      CriteriaCondition condition,
                                      TypedElementInfo paramInfo,
                                      Map<CharSequence, PersistenceGenerator.QuerySettings> setParameter) {
            if (condition.ignoreCase() && !paramInfo.typeName().equals(TypeNames.STRING)) {
                throw new IllegalArgumentException(
                        String.format("IgnoreCase requires method %s parameter of type String",
                                      paramInfo.elementName()));
            }
            ignoreCase(builder,
                       b -> b.addContent("cb.parameter(")
                               .addContent(paramInfo.typeName().genericTypeName().boxed())
                               .addContent(".class, \"")
                               .addContent(paramInfo.elementName())
                               .addContent("\")"),
                       condition.ignoreCase());
            setParameter.putIfAbsent(paramInfo.elementName(), new Param(paramInfo.elementName()));
        }

        // Query parameters set directly in the query model do not have class information
        private static void parameter(Method.Builder builder,
                                      CriteriaCondition condition,
                                      CharSequence paramName,
                                      Map<CharSequence, PersistenceGenerator.QuerySettings> setParameter) {
            if (condition.ignoreCase()) {
                builder.addContent("cb.upper(");
            }
            builder.addContent("cb.parameter(")
                    .addContent(Object.class)
                    .addContent(".class, \"")
                    .addContent(paramName.toString())
                    .addContent("\")");
            if (condition.ignoreCase()) {
                builder.addContent(")");
            }
            setParameter.putIfAbsent(paramName, new Param(paramName));
        }
    }

    // Logical AND expression of at least 2 conditions
    private static final class And implements Expression {

        private final List<Condition> conditions;

        private And(Condition first) {
            conditions = new ArrayList<>();
            conditions.add(first);
        }

        @Override
        public void build(Method.Builder builder,
                          Iterator<TypedElementInfo> parameters,
                          Map<CharSequence, PersistenceGenerator.QuerySettings> setParameter) {
            builder.addContentLine("cb.and(");
            increasePadding(builder, 1);
            boolean firstExpression = true;
            for (Condition condition : conditions) {
                if (firstExpression) {
                    firstExpression = false;
                } else {
                    builder.addContentLine(",");
                }
                condition.build(builder, parameters, setParameter);
            }
            decreasePadding(builder, 1);
            builder.addContent(")");
        }

        private void add(Condition condition) {
            conditions.add(condition);
        }

    }

}

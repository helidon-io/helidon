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

import java.util.Collection;
import java.util.Iterator;

import io.helidon.codegen.CodegenException;
import io.helidon.data.codegen.common.RepositoryInfo;
import io.helidon.data.codegen.common.spi.PersistenceGenerator;
import io.helidon.data.codegen.common.spi.PersistenceGenerator.QueryReturnType;
import io.helidon.data.codegen.query.CriteriaCondition;
import io.helidon.data.codegen.query.DataQuery;
import io.helidon.data.codegen.query.LogicalOperator;
import io.helidon.data.codegen.query.OrderExpression;
import io.helidon.data.codegen.query.Projection;

import static io.helidon.data.jakarta.persistence.codegen.JpqlKeywords.AND;
import static io.helidon.data.jakarta.persistence.codegen.JpqlKeywords.APOSTROPHE;
import static io.helidon.data.jakarta.persistence.codegen.JpqlKeywords.BETWEEN;
import static io.helidon.data.jakarta.persistence.codegen.JpqlKeywords.COMMA;
import static io.helidon.data.jakarta.persistence.codegen.JpqlKeywords.CONCAT;
import static io.helidon.data.jakarta.persistence.codegen.JpqlKeywords.DELETE;
import static io.helidon.data.jakarta.persistence.codegen.JpqlKeywords.DESC;
import static io.helidon.data.jakarta.persistence.codegen.JpqlKeywords.DISTINCT;
import static io.helidon.data.jakarta.persistence.codegen.JpqlKeywords.DOT;
import static io.helidon.data.jakarta.persistence.codegen.JpqlKeywords.EMPTY;
import static io.helidon.data.jakarta.persistence.codegen.JpqlKeywords.EQUAL;
import static io.helidon.data.jakarta.persistence.codegen.JpqlKeywords.FALSE;
import static io.helidon.data.jakarta.persistence.codegen.JpqlKeywords.FROM;
import static io.helidon.data.jakarta.persistence.codegen.JpqlKeywords.GREATER_THAN;
import static io.helidon.data.jakarta.persistence.codegen.JpqlKeywords.GREATER_THAN_EQUAL;
import static io.helidon.data.jakarta.persistence.codegen.JpqlKeywords.IN;
import static io.helidon.data.jakarta.persistence.codegen.JpqlKeywords.IS;
import static io.helidon.data.jakarta.persistence.codegen.JpqlKeywords.LEFT_BRACKET;
import static io.helidon.data.jakarta.persistence.codegen.JpqlKeywords.LESS_THAN;
import static io.helidon.data.jakarta.persistence.codegen.JpqlKeywords.LESS_THAN_EQUAL;
import static io.helidon.data.jakarta.persistence.codegen.JpqlKeywords.LIKE;
import static io.helidon.data.jakarta.persistence.codegen.JpqlKeywords.NOT_EQUAL;
import static io.helidon.data.jakarta.persistence.codegen.JpqlKeywords.NULL;
import static io.helidon.data.jakarta.persistence.codegen.JpqlKeywords.OR;
import static io.helidon.data.jakarta.persistence.codegen.JpqlKeywords.ORDER_BY;
import static io.helidon.data.jakarta.persistence.codegen.JpqlKeywords.PERCENT;
import static io.helidon.data.jakarta.persistence.codegen.JpqlKeywords.RIGHT_BRACKET;
import static io.helidon.data.jakarta.persistence.codegen.JpqlKeywords.SELECT;
import static io.helidon.data.jakarta.persistence.codegen.JpqlKeywords.SPACE;
import static io.helidon.data.jakarta.persistence.codegen.JpqlKeywords.TRUE;
import static io.helidon.data.jakarta.persistence.codegen.JpqlKeywords.UPDATE;
import static io.helidon.data.jakarta.persistence.codegen.JpqlKeywords.WHERE;
import static io.helidon.data.jakarta.persistence.codegen.JpqlKeywords.avg;
import static io.helidon.data.jakarta.persistence.codegen.JpqlKeywords.count;
import static io.helidon.data.jakarta.persistence.codegen.JpqlKeywords.max;
import static io.helidon.data.jakarta.persistence.codegen.JpqlKeywords.maybeComma;
import static io.helidon.data.jakarta.persistence.codegen.JpqlKeywords.maybeNot;
import static io.helidon.data.jakarta.persistence.codegen.JpqlKeywords.min;
import static io.helidon.data.jakarta.persistence.codegen.JpqlKeywords.param;
import static io.helidon.data.jakarta.persistence.codegen.JpqlKeywords.property;
import static io.helidon.data.jakarta.persistence.codegen.JpqlKeywords.sum;

/**
 * Data query code generator for Jakarta Persistence.
 * Builds code snippets of JPQL query string and {@code jakarta.persistence.Query} settings
 * from {@link DataQuery} and parameters {@link Collection}.
 */
final class JakartaPersistenceDataQueryBuilder extends JakartaPersistenceBaseQueryBuilder {
    private final String entityAlias;
    private final StringBuilder jpql;

    private JakartaPersistenceDataQueryBuilder(RepositoryInfo repositoryInfo) {
        super(repositoryInfo);
        this.entityAlias = RepositoryInfo.entityAlias(repositoryInfo.entity().className());
        this.jpql = new StringBuilder();
    }

    static JakartaPersistenceDataQueryBuilder create(RepositoryInfo repositoryInfo) {
        return new JakartaPersistenceDataQueryBuilder(repositoryInfo);
    }

    PersistenceGenerator.Query buildQuery(DataQuery query, Collection<CharSequence> parameters) {
        Iterator<CharSequence> parametersIterator = parameters.iterator();
        Query.Builder queryBuilder = Query.builder();
        buildProjection(query, queryBuilder);
        buildCriteria(query, parametersIterator);
        buildOrder(query);
        queryBuilder.query(jpql.toString());
        setParameter().values().forEach(queryBuilder::setting);
        return queryBuilder.build();
    }

    PersistenceGenerator.Query buildCountQuery(DataQuery query, Collection<CharSequence> parameters) {
        Iterator<CharSequence> parametersIterator = parameters.iterator();
        Query.Builder queryBuilder = Query.builder();
        buildCountFromProjection(query, queryBuilder);
        buildCriteria(query, parametersIterator);
        queryBuilder.query(jpql.toString());
        setParameter().values().forEach(queryBuilder::setting);
        return queryBuilder.build();
    }

    private void buildProjection(DataQuery query, BaseQuery.BaseBuilder<?, ?> queryBuilder) {
        Projection projection = query.projection();
        switch (projection.action()) {
        case Select:
            queryBuilder.isDml(false)
                    // Return type defaults to entity for SELECT
                    .returnType(QueryReturnType.ENTITY);
            appendSelect(projection, queryBuilder);
            break;
        case Delete:
            queryBuilder.isDml(true)
                    .returnType(QueryReturnType.DML);
            appendDelete();
            break;
        case Update:
            queryBuilder.isDml(true)
                    .returnType(QueryReturnType.DML);
            appendUpdate();
            // Not implemented yet
            throw new CodegenException("update methods are not supported yet");
            //break;
        default:
            throw new CodegenException("Unknown query action " + projection.action());
        }
    }

    private void buildCountFromProjection(DataQuery query, BaseQuery.BaseBuilder<?, ?> queryBuilder) {
        Projection projection = query.projection();
        switch (projection.action()) {
        case Select:
            appendSelectCount(projection, queryBuilder);
            break;
        case Delete:
        case Update:
            throw new CodegenException("Cannot build COUNT query from " + projection.action() + " statement");
        default:
            throw new CodegenException("Unknown query action " + projection.action());
        }
    }

    private void appendSelect(Projection projection, BaseQuery.BaseBuilder<?, ?> queryBuilder) {
        jpql.append(SELECT);
        appendSelectExpression(projection, queryBuilder);
        jpql.append(SPACE)
                .append(FROM)
                .append(SPACE)
                .append(repositoryInfo().entity().className())
                .append(SPACE)
                .append(entityAlias);
    }

    private void appendSelectExpression(Projection projection, BaseQuery.BaseBuilder<?, ?> queryBuilder) {
        jpql.append(SPACE);
        projection.expression().ifPresentOrElse(
                expression -> {
                    switch (expression.operator()) {
                    case First:
                        expression.parameter().ifPresentOrElse(
                                parameter -> {
                                    if (parameter.type() != Integer.class) {
                                        throw new CodegenException("First projection operator parameter is not Integer");
                                    }
                                    jpql.append(distinctProperty(projection.distinct(), entityAlias));
                                    queryBuilder.setting(new Limit((Integer) parameter.value()))
                                            .returnType(QueryReturnType.ENTITY);
                                },
                                () -> {
                                    throw new CodegenException("Missing First projection operator parameter");
                                }
                        );
                        break;
                    case Count:
                        projection.property().ifPresentOrElse(
                                property -> count(jpql, distinctProperty(projection.distinct(),
                                                                         entityProperty(property.toString()))),
                                () -> count(jpql, distinctProperty(projection.distinct(), entityAlias)));
                        queryBuilder.returnType(QueryReturnType.NUMBER);
                        break;
                    case Exists:
                        projection.property().ifPresentOrElse(
                                property -> count(jpql, distinctProperty(projection.distinct(),
                                                                         entityProperty(property.toString()))),
                                () -> count(jpql, distinctProperty(projection.distinct(), entityAlias)));
                        queryBuilder.returnType(QueryReturnType.BOOLEAN);
                        break;
                    case Min:
                        projection.property().ifPresentOrElse(
                                property -> min(jpql, distinctProperty(projection.distinct(),
                                                                       entityProperty(property.toString()))),
                                () -> min(jpql, distinctProperty(projection.distinct(), entityAlias)));
                        queryBuilder.returnType(QueryReturnType.NUMBER);
                        break;
                    case Max:
                        projection.property().ifPresentOrElse(
                                property -> max(jpql, distinctProperty(projection.distinct(),
                                                                       entityProperty(property.toString()))),
                                () -> max(jpql, distinctProperty(projection.distinct(), entityAlias)));
                        queryBuilder.returnType(QueryReturnType.NUMBER);
                        break;
                    case Sum:
                        projection.property().ifPresentOrElse(
                                property -> sum(jpql, distinctProperty(projection.distinct(),
                                                                       entityProperty(property.toString()))),
                                () -> sum(jpql, distinctProperty(projection.distinct(), entityAlias)));
                        queryBuilder.returnType(QueryReturnType.NUMBER);
                        break;
                    case Avg:
                        projection.property().ifPresentOrElse(
                                property -> avg(jpql, distinctProperty(projection.distinct(),
                                                                       entityProperty(property.toString()))),
                                () -> avg(jpql, distinctProperty(projection.distinct(), entityAlias)));
                        queryBuilder.returnType(QueryReturnType.NUMBER);
                        break;
                    default:
                        throw new CodegenException("Unknown projection expression operator " + expression.operator());
                    }
                },
                () -> projection.property().ifPresentOrElse(
                        property -> jpql.append(distinctProperty(projection.distinct(),
                                                                 entityProperty(property.toString()))),
                        () -> jpql.append(distinctProperty(projection.distinct(), entityAlias))
                ));
    }

    private void appendSelectCount(Projection projection, BaseQuery.BaseBuilder<?, ?> queryBuilder) {
        if (projection.expression().isEmpty()) {
            jpql.append(SELECT)
                    .append(SPACE);
            projection.property().ifPresentOrElse(
                    property -> count(jpql, distinctProperty(projection.distinct(),
                                                             entityProperty(property.toString()))),
                    () -> count(jpql, distinctProperty(projection.distinct(), entityAlias)));
            queryBuilder.isDml(false);
            queryBuilder.returnType(QueryReturnType.NUMBER);
            jpql.append(SPACE)
                    .append(FROM)
                    .append(SPACE)
                    .append(repositoryInfo().entity().className())
                    .append(SPACE)
                    .append(entityAlias);
        } else {
            throw new CodegenException("Cannot build COUNT query from statement with "
                                               + projection.expression().get().operator() + " projection expression");
        }

    }

    // Identifier is appended as is
    private CharSequence distinctProperty(boolean distinct, CharSequence identifier) {
        StringBuilder sb = new StringBuilder(entityAlias.length() + DISTINCT.length() + 1);
        if (distinct) {
            sb.append(DISTINCT)
                    .append(SPACE);
        }
        sb.append(identifier);
        return sb;
    }

    // Property is prepended with entity alias
    private CharSequence entityProperty(CharSequence property) {
        StringBuilder sb = new StringBuilder(entityAlias.length() + property.length() + 1);
        sb.append(entityAlias)
                .append(DOT)
                .append(property);
        return sb;
    }

    private void appendDelete() {
        jpql.append(DELETE)
                .append(SPACE)
                .append(FROM)
                .append(SPACE)
                .append(repositoryInfo().entity().className())
                .append(SPACE)
                .append(entityAlias);
    }

    private void appendUpdate() {
        jpql.append(UPDATE)
                .append(SPACE)
                .append(repositoryInfo().entity().className())
                .append(SPACE)
                .append(entityAlias);
    }

    private void buildCriteria(DataQuery query, Iterator<CharSequence> parameters) {
        query.criteria().ifPresent(
                criteria -> {
                    jpql.append(SPACE)
                            .append(WHERE);
                    // First criteria must exist when criteria is present
                    appendCriteriaCondition(criteria.first(), parameters);
                    criteria.next().forEach(
                            nextExpression -> {
                                appendLogicalOperator(nextExpression.operator());
                                appendCriteriaCondition(nextExpression.criteria(), parameters);
                            });
                });
    }

    private void appendLogicalOperator(LogicalOperator operatopr) {
        jpql.append(SPACE);
        switch (operatopr) {
        case AND:
            jpql.append(AND);
            break;
        case OR:
            jpql.append(OR);
            break;
        default:
            throw new CodegenException("Unknown criteria logical operator " + operatopr);
        }
    }

    private void appendCriteriaCondition(CriteriaCondition expression, Iterator<CharSequence> parameters) {
        switch (expression.operator()) {
        case Equal:
            jpqlEqual(expression, parameters);
            break;
        case Contains:
            jpqlContains(expression, parameters);
            break;
        case EndsWith:
            jpqlEndsWith(expression, parameters);
            break;
        case StartsWith:
            jpqlStartsWith(expression, parameters);
            break;
        case Before:
        case LessThan:
            jpqlLessThan(expression, parameters);
            break;
        case LessThanEqual:
            jpqlLessThanEqual(expression, parameters);
            break;
        case After:
        case GreaterThan:
            jpqlGreaterThan(expression, parameters);
            break;
        case GreaterThanEqual:
            jpqlGreaterThanEqual(expression, parameters);
            break;
        case Between:
            jpqlBetween(expression, parameters);
            break;
        case Like:
            jpqlLike(expression, parameters);
            break;
        case In:
            jpqlIn(expression, parameters);
            break;
        case Empty:
            jpqlEmpty(expression);
            break;
        case Null:
            jpqlNull(expression);
            break;
        case True:
            jpqlTrue(expression);
            break;
        case False:
            jpqlFalse(expression);
            break;
        default:
            throw new CodegenException("Unknown criteria expression operator " + expression.operator());
        }
    }

    // property = :param
    // property <> :param (NOT)
    private void jpqlEqual(CriteriaCondition expression, Iterator<CharSequence> parameters) {
        jpql.append(SPACE);
        property(jpql, entityAlias, expression.property().name(), expression.ignoreCase());
        jpql.append(SPACE)
                .append(expression.not() ? NOT_EQUAL : EQUAL)
                .append(SPACE);
        expression.parameters().ifPresentOrElse(
                params -> jpqlParam(params.get(0), expression.ignoreCase()),
                () -> jpqlParam(parameters.next(), expression.ignoreCase()));
    }

    // property [NOT] LIKE CONCAT('%', :param, '%')
    private void jpqlContains(CriteriaCondition expression, Iterator<CharSequence> parameters) {
        jpql.append(SPACE);
        property(jpql, entityAlias, expression.property().name(), expression.ignoreCase());
        maybeNot(jpql, expression.not());
        jpql.append(SPACE)
                .append(LIKE)
                .append(SPACE)
                .append(CONCAT)
                .append(LEFT_BRACKET)
                .append(APOSTROPHE)
                .append(PERCENT)
                .append(APOSTROPHE)
                .append(COMMA)
                .append(SPACE);
        expression.parameters().ifPresentOrElse(
                params -> jpqlParam(params.get(0), expression.ignoreCase()),
                () -> jpqlParam(parameters.next(), expression.ignoreCase()));
        jpql.append(COMMA)
                .append(SPACE)
                .append(APOSTROPHE)
                .append(PERCENT)
                .append(APOSTROPHE)
                .append(RIGHT_BRACKET);
    }

    // property [NOT] LIKE CONCAT('%', :param)
    private void jpqlEndsWith(CriteriaCondition expression, Iterator<CharSequence> parameters) {
        jpql.append(SPACE);
        property(jpql, entityAlias, expression.property().name(), expression.ignoreCase());
        maybeNot(jpql, expression.not());
        jpql.append(SPACE)
                .append(LIKE)
                .append(SPACE)
                .append(CONCAT)
                .append(LEFT_BRACKET)
                .append(APOSTROPHE)
                .append(PERCENT)
                .append(APOSTROPHE)
                .append(COMMA)
                .append(SPACE);
        expression.parameters().ifPresentOrElse(
                params -> jpqlParam(params.get(0), expression.ignoreCase()),
                () -> jpqlParam(parameters.next(), expression.ignoreCase()));
        jpql.append(RIGHT_BRACKET);
    }

    // property [NOT] LIKE CONCAT(:param, '%')
    private void jpqlStartsWith(CriteriaCondition expression, Iterator<CharSequence> parameters) {
        jpql.append(SPACE);
        property(jpql, entityAlias, expression.property().name(), expression.ignoreCase());
        maybeNot(jpql, expression.not());
        jpql.append(SPACE)
                .append(LIKE)
                .append(SPACE)
                .append(CONCAT)
                .append(LEFT_BRACKET);
        expression.parameters().ifPresentOrElse(
                params -> jpqlParam(params.get(0), expression.ignoreCase()),
                () -> jpqlParam(parameters.next(), expression.ignoreCase()));
        jpql.append(COMMA)
                .append(SPACE)
                .append(APOSTROPHE)
                .append(PERCENT)
                .append(APOSTROPHE)
                .append(RIGHT_BRACKET);
    }

    // property < :param
    // property >= :param (NOT)
    private void jpqlLessThan(CriteriaCondition expression, Iterator<CharSequence> parameters) {
        jpql.append(SPACE);
        property(jpql, entityAlias, expression.property().name(), expression.ignoreCase());
        jpql.append(SPACE)
                .append(expression.not() ? GREATER_THAN_EQUAL : LESS_THAN)
                .append(SPACE);
        expression.parameters().ifPresentOrElse(
                params -> jpqlParam(params.get(0), expression.ignoreCase()),
                () -> jpqlParam(parameters.next(), expression.ignoreCase()));
    }

    // property <= :param
    // property > :param (NOT)
    private void jpqlLessThanEqual(CriteriaCondition expression, Iterator<CharSequence> parameters) {
        jpql.append(SPACE);
        property(jpql, entityAlias, expression.property().name(), expression.ignoreCase());
        jpql.append(SPACE)
                .append(expression.not() ? GREATER_THAN : LESS_THAN_EQUAL)
                .append(SPACE);
        expression.parameters().ifPresentOrElse(
                params -> jpqlParam(params.get(0), expression.ignoreCase()),
                () -> jpqlParam(parameters.next(), expression.ignoreCase()));
    }

    // property > :param
    // property <= :param (NOT)
    private void jpqlGreaterThan(CriteriaCondition expression, Iterator<CharSequence> parameters) {
        jpql.append(SPACE);
        property(jpql, entityAlias, expression.property().name(), expression.ignoreCase());
        jpql.append(SPACE)
                .append(expression.not() ? LESS_THAN_EQUAL : GREATER_THAN)
                .append(SPACE);
        expression.parameters().ifPresentOrElse(
                params -> jpqlParam(params.get(0), expression.ignoreCase()),
                () -> jpqlParam(parameters.next(), expression.ignoreCase()));
    }

    // property >= :param
    // property < :param (NOT)
    private void jpqlGreaterThanEqual(CriteriaCondition expression, Iterator<CharSequence> parameters) {
        jpql.append(SPACE);
        property(jpql, entityAlias, expression.property().name(), expression.ignoreCase());
        jpql.append(SPACE)
                .append(expression.not() ? LESS_THAN : GREATER_THAN_EQUAL)
                .append(SPACE);
        expression.parameters().ifPresentOrElse(
                params -> jpqlParam(params.get(0), expression.ignoreCase()),
                () -> jpqlParam(parameters.next(), expression.ignoreCase()));
    }

    // property [NOT] BETWEEN :param1 AND :param2
    private void jpqlBetween(CriteriaCondition expression, Iterator<CharSequence> parameters) {
        jpql.append(SPACE);
        property(jpql, entityAlias, expression.property().name(), expression.ignoreCase());
        maybeNot(jpql, expression.not());
        jpql.append(SPACE)
                .append(BETWEEN)
                .append(SPACE);
        expression.parameters().ifPresentOrElse(
                params -> jpqlParam(params.get(0), expression.ignoreCase()),
                () -> jpqlParam(parameters.next(), expression.ignoreCase()));
        jpql.append(SPACE)
                .append(AND)
                .append(SPACE);
        expression.parameters().ifPresentOrElse(
                params -> jpqlParam(params.get(1), expression.ignoreCase()),
                () -> jpqlParam(parameters.next(), expression.ignoreCase()));
    }

    // property [NOT] LIKE :param
    private void jpqlLike(CriteriaCondition expression, Iterator<CharSequence> parameters) {
        jpql.append(SPACE);
        property(jpql, entityAlias, expression.property().name(), expression.ignoreCase());
        maybeNot(jpql, expression.not());
        jpql.append(SPACE)
                .append(LIKE)
                .append(SPACE);
        expression.parameters().ifPresentOrElse(
                params -> jpqlParam(params.get(0), expression.ignoreCase()),
                () -> jpqlParam(parameters.next(), expression.ignoreCase()));
    }

    // property [NOT] IN (:param)
    // param must be Collection for multiple values
    private void jpqlIn(CriteriaCondition expression, Iterator<CharSequence> parameters) {
        jpql.append(SPACE);
        property(jpql, entityAlias, expression.property().name(), expression.ignoreCase());
        maybeNot(jpql, expression.not());
        jpql.append(SPACE)
                .append(IN)
                .append(SPACE);
        if (expression.ignoreCase()) {
            jpql.append(LEFT_BRACKET);
        }
        expression.parameters().ifPresentOrElse(
                params -> jpqlParam(params.get(0), expression.ignoreCase()),
                () -> jpqlParam(parameters.next(), expression.ignoreCase()));
        if (expression.ignoreCase()) {
            jpql.append(RIGHT_BRACKET);
        }
    }

    // property IS [NOT] EMPTY
    private void jpqlEmpty(CriteriaCondition expression) {
        jpql.append(SPACE);
        property(jpql, entityAlias, expression.property().name());
        jpql.append(SPACE)
                .append(IS);
        maybeNot(jpql, expression.not());
        jpql.append(SPACE)
                .append(EMPTY);
    }

    // property IS [NOT] NULL
    private void jpqlNull(CriteriaCondition expression) {
        jpql.append(SPACE);
        property(jpql, entityAlias, expression.property().name());
        jpql.append(SPACE)
                .append(IS);
        maybeNot(jpql, expression.not());
        jpql.append(SPACE)
                .append(NULL);
    }

    // property IS [NOT] TRUE
    private void jpqlTrue(CriteriaCondition expression) {
        jpql.append(SPACE);
        property(jpql, entityAlias, expression.property().name());
        jpql.append(SPACE)
                .append(expression.not() ? NOT_EQUAL : EQUAL)
                .append(SPACE)
                .append(TRUE);
    }

    // property IS [NOT] FALSE
    private void jpqlFalse(CriteriaCondition expression) {
        jpql.append(SPACE);
        property(jpql, entityAlias, expression.property().name());
        jpql.append(SPACE)
                .append(expression.not() ? NOT_EQUAL : EQUAL)
                .append(SPACE)
                .append(FALSE);
    }

    private void jpqlParam(CharSequence param, boolean ignoreCase) {
        param(jpql, param, ignoreCase);
        setParameter().putIfAbsent(param, new Param(param));
    }

    private void buildOrder(DataQuery query) {
        query.order().ifPresent(
                order -> {
                    jpql.append(SPACE)
                            .append(ORDER_BY)
                            .append(SPACE);
                    // Expressions shall not be empty when query ordering is present
                    Iterator<OrderExpression> expressions = order.expressions().iterator();
                    appendOrderExpression(expressions.next(), false);
                    while (expressions.hasNext()) {
                        appendOrderExpression(expressions.next(), true);
                    }
                });
    }

    private void appendOrderExpression(OrderExpression expression, boolean comma) {
        maybeComma(jpql, comma);
        property(jpql, entityAlias, expression.property().name());
        switch (expression.operator()) {
        case ASC:
            // ASC is default value so it may be skipped
            break;
        case DESC:
            jpql.append(SPACE)
                    .append(DESC);
            break;
        default:
            throw new CodegenException("Unknown order expression operator " + expression.operator());
        }
    }

}

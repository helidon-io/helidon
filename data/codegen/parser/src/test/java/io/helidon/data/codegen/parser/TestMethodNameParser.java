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
package io.helidon.data.codegen.parser;

import java.util.Optional;

import io.helidon.data.codegen.query.Criteria;
import io.helidon.data.codegen.query.CriteriaCondition;
import io.helidon.data.codegen.query.CriteriaOperator;
import io.helidon.data.codegen.query.DataQuery;
import io.helidon.data.codegen.query.Order;
import io.helidon.data.codegen.query.OrderExpression;
import io.helidon.data.codegen.query.OrderOperator;
import io.helidon.data.codegen.query.Projection;
import io.helidon.data.codegen.query.ProjectionAction;
import io.helidon.data.codegen.query.ProjectionExpression;
import io.helidon.data.codegen.query.ProjectionResult;
import io.helidon.data.codegen.query.Property;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

public class TestMethodNameParser {

    @Test
    void testExists() {
        TestProjection.test("exists",
                            ProjectionResult.Exists,
                            Optional.of(ProjectionExpression.createExists()),
                            Optional.empty());
    }

    @Test
    void testCount() {
        TestProjection.test("count",
                            ProjectionResult.Count,
                            Optional.of(ProjectionExpression.createCount()),
                            Optional.empty());
    }

    @Test
    void testFind() {
        TestProjection.test("find",
                            ProjectionResult.Find,
                            Optional.empty(),
                            Optional.empty());
    }

    @Test
    void testList() {
        TestProjection.test("list",
                            ProjectionResult.List,
                            Optional.empty(),
                            Optional.empty());
    }

    @Test
    void testStream() {
        TestProjection.test("stream",
                            ProjectionResult.Stream,
                            Optional.empty(),
                            Optional.empty());
    }

    @Test
    void testFindAll() {
        TestProjection.test("findAll",
                            ProjectionResult.Find,
                            Optional.empty(),
                            Optional.empty());
    }

    @Test
    void testListAll() {
        TestProjection.test("listAll",
                            ProjectionResult.List,
                            Optional.empty(),
                            Optional.empty());
    }

    @Test
    void testStreamAll() {
        TestProjection.test("streamAll",
                            ProjectionResult.Stream,
                            Optional.empty(),
                            Optional.empty());
    }

    // Update is not supported yet so parser result shall be false
    @Test
    void testUpdateAll() {
        boolean result = MethodNameParserImpl.create()
                .parse("updateAll");
        assertThat(result, is(false));
    }

    @Test
    void testDeleteAll() {
        MethodNameParser parser = MethodNameParserImpl.create();
        assertThat(parser.parse("deleteAll"), is(true));
        DataQuery query = parser.dataQuery();
        validateProjection(query.projection(),
                           ProjectionAction.Delete,
                           Optional.of(ProjectionResult.Dml),
                           Optional.empty(),
                           Optional.empty(),
                           false);
        validateCriteria(query.criteria(), Optional.empty());
        validateOrder(query.order(), Optional.empty());
    }

    @Test
    void testFindFirst123() {
        TestProjection.test("findFirst123",
                            ProjectionResult.Find,
                            Optional.of(ProjectionExpression.createFirst(123)),
                            Optional.empty());
    }

    @Test
    void testFindFirst123Distinct() {
        TestProjection.test("findFirst123Distinct",
                            ProjectionResult.Find,
                            Optional.of(ProjectionExpression.createFirst(123)),
                            Optional.empty(),
                            true);
    }

    // Validate "findFirst" to parse "First" as property.
    // Expression operator First<number> must contain valid number suffix,
    // otherwise property rule shall apply
    @Test
    void testFindFirst() {
        TestProjection.test("findFirst",
                            ProjectionResult.Find,
                            Optional.empty(),
                            Optional.of(Property.create("first")));
    }

    // Validate "findFirstName" to parse "FirstName" as property.
    // Expression operator First<number> must contain valid number suffix,
    // otherwise property rule shall apply
    @Test
    void testFindFirstName() {
        TestProjection.test("findFirstName",
                            ProjectionResult.Find,
                            Optional.empty(),
                            Optional.of(Property.create("firstName")));
    }

    // Validate "getMax" without property to indicate failure
    @Test
    void testGetMax() {
        boolean result = MethodNameParserImpl.create()
                .parse("getMax");
        assertThat(result, is(false));
    }

    // Validate "getMin" without property to indicate failure
    @Test
    void testGetMin() {
        boolean result = MethodNameParserImpl.create()
                .parse("getMin");
        assertThat(result, is(false));
    }

    // Validate "getSum" without property to indicate failure
    @Test
    void testGetSum() {
        boolean result = MethodNameParserImpl.create()
                .parse("getSum");
        assertThat(result, is(false));
    }

    // Validate "getAvg" without property to indicate failure
    @Test
    void testGetAvg() {
        boolean result = MethodNameParserImpl.create()
                .parse("getAvg");
        assertThat(result, is(false));
    }

    // "getMaxX" with single character property (PropFirst is valid last token)
    @Test
    void testGetMaxX() {
        TestProjection.test("getMaxX",
                            ProjectionResult.Get,
                            Optional.of(ProjectionExpression.createMax()),
                            Optional.of(Property.create("x")));
    }

    // "getMaxAge" with multiple characters property (PropNext is valid last token)
    @Test
    void testGetMaxAge() {
        TestProjection.test("getMaxAge",
                            ProjectionResult.Get,
                            Optional.of(ProjectionExpression.createMax()),
                            Optional.of(Property.create("age")));
    }

    // "getMinX" with single character property (PropFirst is valid last token)
    @Test
    void testGetMinX() {
        TestProjection.test("getMinX",
                            ProjectionResult.Get,
                            Optional.of(ProjectionExpression.createMin()),
                            Optional.of(Property.create("x")));
    }

    // "getMinAge" with multiple characters property (PropNext is valid last token)
    @Test
    void testGetMinAge() {
        TestProjection.test("getMinAge",
                            ProjectionResult.Get,
                            Optional.of(ProjectionExpression.createMin()),
                            Optional.of(Property.create("age")));
    }

    // "getSumX" with single character property (PropFirst is valid last token)
    @Test
    void testSumMinX() {
        TestProjection.test("getSumX",
                            ProjectionResult.Get,
                            Optional.of(ProjectionExpression.createSum()),
                            Optional.of(Property.create("x")));
    }

    // "getSumAge" with multiple characters property (PropNext is valid last token)
    @Test
    void testSumMinAge() {
        TestProjection.test("getSumAge",
                            ProjectionResult.Get,
                            Optional.of(ProjectionExpression.createSum()),
                            Optional.of(Property.create("age")));
    }

    // "getAvgX" with single character property (PropFirst is valid last token)
    @Test
    void testGetAvgX() {
        TestProjection.test("getAvgX",
                            ProjectionResult.Get,
                            Optional.of(ProjectionExpression.createAvg()),
                            Optional.of(Property.create("x")));
    }

    // "getAvgAge" with multiple characters property (PropNext is valid last token)
    @Test
    void testGetAvgAge() {
        TestProjection.test("getAvgAge",
                            ProjectionResult.Get,
                            Optional.of(ProjectionExpression.createAvg()),
                            Optional.of(Property.create("age")));
    }

    // "getX" with single character property (PropFirst is valid last token)
    @Test
    void testGetX() {
        TestProjection.test("getX",
                            ProjectionResult.Get,
                            Optional.empty(),
                            Optional.of(Property.create("x")));
    }

    // "getName" with multiple characters property (PropNext is valid last token)
    @Test
    void testGetName() {
        TestProjection.test("getName",
                            ProjectionResult.Get,
                            Optional.empty(),
                            Optional.of(Property.create("name")));
    }

    // "getUser_name" with property of multiple components
    @Test
    void testGetUser_name() {
        TestProjection.test("getUser_name",
                            ProjectionResult.Get,
                            Optional.empty(),
                            Optional.of(Property.create(new String[] {"user", "name"})));
    }

    @Test
    void testExistsByX() {
        TestCriteriaProperty.testByX(ProjectionResult.Exists, ProjectionExpression.createExists());
    }

    @Test
    void testExistsByName() {
        TestCriteriaProperty.testByName(ProjectionResult.Exists, ProjectionExpression.createExists());
    }

    @Test
    void testCountByX() {
        TestCriteriaProperty.testByX(ProjectionResult.Count, ProjectionExpression.createCount());
    }

    @Test
    void testCountByName() {
        TestCriteriaProperty.testByName(ProjectionResult.Count, ProjectionExpression.createCount());
    }

    @Test
    void testGetByX() {
        TestCriteriaProperty.testByX(ProjectionResult.Get, null);
    }

    @Test
    void testGetByName() {
        TestCriteriaProperty.testByName(ProjectionResult.Get, null);
    }

    @Test
    void testFindByX() {
        TestCriteriaProperty.testByX(ProjectionResult.Find, null);
    }

    @Test
    void testFindByName() {
        TestCriteriaProperty.testByName(ProjectionResult.Find, null);
    }

    @Test
    void testListByX() {
        TestCriteriaProperty.testByX(ProjectionResult.List, null);
    }

    @Test
    void testListByName() {
        TestCriteriaProperty.testByName(ProjectionResult.List, null);
    }

    @Test
    void testStreamByX() {
        TestCriteriaProperty.testByName(ProjectionResult.Stream, null);
    }

    @Test
    void testStreamByName() {
        TestCriteriaProperty.testByX(ProjectionResult.Stream, null);
    }

    @Test
    void testGetByNameAfter() {
        TestCriteriaOperator.testGetByName(CriteriaOperator.After);
    }

    @Test
    void testGetByNameNotAfter() {
        TestCriteriaOperator.testGetByNameNot(CriteriaOperator.After);
    }

    @Test
    void testGetByNameIgnoreCaseAfter() {
        TestCriteriaOperator.testGetByNameIgnoreCase(CriteriaOperator.After);
    }

    @Test
    void testGetByNameNotIgnoreCaseAfter() {
        TestCriteriaOperator.testGetByNameNotIgnoreCase(CriteriaOperator.After);
    }

    @Test
    void testGetByNameIgnoreCaseNotAfter() {
        TestCriteriaOperator.testGetByNameIgnoreCaseNot(CriteriaOperator.After);
    }

    @Test
    void testGetByNameBefore() {
        TestCriteriaOperator.testGetByName(CriteriaOperator.Before);
    }

    @Test
    void testGetByNameNotBefore() {
        TestCriteriaOperator.testGetByNameNot(CriteriaOperator.Before);
    }

    @Test
    void testGetByNameIgnoreCaseBefore() {
        TestCriteriaOperator.testGetByNameIgnoreCase(CriteriaOperator.Before);
    }

    @Test
    void testGetByNameNotIgnoreCaseBefore() {
        TestCriteriaOperator.testGetByNameNotIgnoreCase(CriteriaOperator.Before);
    }

    @Test
    void testGetByNameIgnoreCaseNotBefore() {
        TestCriteriaOperator.testGetByNameIgnoreCaseNot(CriteriaOperator.Before);
    }

    @Test
    void testGetByNameContains() {
        TestCriteriaOperator.testGetByName(CriteriaOperator.Contains);
    }

    @Test
    void testGetByNameNotContains() {
        TestCriteriaOperator.testGetByNameNot(CriteriaOperator.Contains);
    }

    @Test
    void testGetByNameIgnoreCaseContains() {
        TestCriteriaOperator.testGetByNameIgnoreCase(CriteriaOperator.Contains);
    }

    @Test
    void testGetByNameNotIgnoreCaseContains() {
        TestCriteriaOperator.testGetByNameNotIgnoreCase(CriteriaOperator.Contains);
    }

    @Test
    void testGetByNameIgnoreCaseNotContains() {
        TestCriteriaOperator.testGetByNameIgnoreCaseNot(CriteriaOperator.Contains);
    }

    @Test
    void testGetByNameEndsWith() {
        TestCriteriaOperator.testGetByName(CriteriaOperator.EndsWith);
    }

    @Test
    void testGetByNameNotEndsWith() {
        TestCriteriaOperator.testGetByNameNot(CriteriaOperator.EndsWith);
    }

    @Test
    void testGetByNameIgnoreCaseEndsWith() {
        TestCriteriaOperator.testGetByNameIgnoreCase(CriteriaOperator.EndsWith);
    }

    @Test
    void testGetByNameNotIgnoreCaseEndsWith() {
        TestCriteriaOperator.testGetByNameNotIgnoreCase(CriteriaOperator.EndsWith);
    }

    @Test
    void testGetByNameIgnoreCaseNotEndsWith() {
        TestCriteriaOperator.testGetByNameIgnoreCaseNot(CriteriaOperator.EndsWith);
    }

    @Test
    void testGetByNameStartsWith() {
        TestCriteriaOperator.testGetByName(CriteriaOperator.StartsWith);
    }

    @Test
    void testGetByNameNotStartsWith() {
        TestCriteriaOperator.testGetByNameNot(CriteriaOperator.StartsWith);
    }

    @Test
    void testGetByNameIgnoreCaseStartsWith() {
        TestCriteriaOperator.testGetByNameIgnoreCase(CriteriaOperator.StartsWith);
    }

    @Test
    void testGetByNameNotIgnoreCaseStartsWith() {
        TestCriteriaOperator.testGetByNameNotIgnoreCase(CriteriaOperator.StartsWith);
    }

    @Test
    void testGetByNameIgnoreCaseNotStartsWith() {
        TestCriteriaOperator.testGetByNameIgnoreCaseNot(CriteriaOperator.StartsWith);
    }

    @Test
    void testGetByNameEqual() {
        TestCriteriaOperator.testGetByName(CriteriaOperator.Equal);
    }

    @Test
    void testGetByNameNotEqual() {
        TestCriteriaOperator.testGetByNameNot(CriteriaOperator.Equal);
    }

    @Test
    void testGetByNameIgnoreCaseEqual() {
        TestCriteriaOperator.testGetByNameIgnoreCase(CriteriaOperator.Equal);
    }

    @Test
    void testGetByNameNotIgnoreCaseEqual() {
        TestCriteriaOperator.testGetByNameNotIgnoreCase(CriteriaOperator.Equal);
    }

    @Test
    void testGetByNameIgnoreCaseNotEqual() {
        TestCriteriaOperator.testGetByNameIgnoreCaseNot(CriteriaOperator.Equal);
    }

    @Test
    void testGetByNameLessThan() {
        TestCriteriaOperator.testGetByName(CriteriaOperator.LessThan);
    }

    @Test
    void testGetByNameNotLessThan() {
        TestCriteriaOperator.testGetByNameNot(CriteriaOperator.LessThan);
    }

    @Test
    void testGetByNameIgnoreCaseLessThan() {
        TestCriteriaOperator.testGetByNameIgnoreCase(CriteriaOperator.LessThan);
    }

    @Test
    void testGetByNameNotIgnoreCaseLessThan() {
        TestCriteriaOperator.testGetByNameNotIgnoreCase(CriteriaOperator.LessThan);
    }

    @Test
    void testGetByNameIgnoreCaseNotLessThan() {
        TestCriteriaOperator.testGetByNameIgnoreCaseNot(CriteriaOperator.LessThan);
    }

    @Test
    void testGetByNameLessThanEqual() {
        TestCriteriaOperator.testGetByName(CriteriaOperator.LessThanEqual);
    }

    @Test
    void testGetByNameNotLessThanEqual() {
        TestCriteriaOperator.testGetByNameNot(CriteriaOperator.LessThanEqual);
    }

    @Test
    void testGetByNameIgnoreCaseLessThanEqual() {
        TestCriteriaOperator.testGetByNameIgnoreCase(CriteriaOperator.LessThanEqual);
    }

    @Test
    void testGetByNameNotIgnoreCaseLessThanEqual() {
        TestCriteriaOperator.testGetByNameNotIgnoreCase(CriteriaOperator.LessThanEqual);
    }

    @Test
    void testGetByNameIgnoreCaseNotLessThanEqual() {
        TestCriteriaOperator.testGetByNameIgnoreCaseNot(CriteriaOperator.LessThanEqual);
    }

    @Test
    void testGetByNameGreaterThan() {
        TestCriteriaOperator.testGetByName(CriteriaOperator.GreaterThan);
    }

    @Test
    void testGetByNameNotGreaterThan() {
        TestCriteriaOperator.testGetByNameNot(CriteriaOperator.GreaterThan);
    }

    @Test
    void testGetByNameIgnoreCaseGreaterThan() {
        TestCriteriaOperator.testGetByNameIgnoreCase(CriteriaOperator.GreaterThan);
    }

    @Test
    void testGetByNameNotIgnoreCaseGreaterThan() {
        TestCriteriaOperator.testGetByNameNotIgnoreCase(CriteriaOperator.GreaterThan);
    }

    @Test
    void testGetByNameIgnoreCaseNotGreaterThan() {
        TestCriteriaOperator.testGetByNameIgnoreCaseNot(CriteriaOperator.GreaterThan);
    }

    @Test
    void testGetByNameGreaterThanEqual() {
        TestCriteriaOperator.testGetByName(CriteriaOperator.GreaterThanEqual);
    }

    @Test
    void testGetByNameNotGreaterThanEqual() {
        TestCriteriaOperator.testGetByNameNot(CriteriaOperator.GreaterThanEqual);
    }

    @Test
    void testGetByNameIgnoreCaseGreaterThanEqual() {
        TestCriteriaOperator.testGetByNameIgnoreCase(CriteriaOperator.GreaterThanEqual);
    }

    @Test
    void testGetByNameNotIgnoreCaseGreaterThanEqual() {
        TestCriteriaOperator.testGetByNameNotIgnoreCase(CriteriaOperator.GreaterThanEqual);
    }

    @Test
    void testGetByNameIgnoreCaseNotGreaterThanEqual() {
        TestCriteriaOperator.testGetByNameIgnoreCaseNot(CriteriaOperator.GreaterThanEqual);
    }

    @Test
    void testGetByNameBetween() {
        TestCriteriaOperator.testGetByName(CriteriaOperator.Between);
    }

    @Test
    void testGetByNameNotBetween() {
        TestCriteriaOperator.testGetByNameNot(CriteriaOperator.Between);
    }

    @Test
    void testGetByNameIgnoreCaseBetween() {
        TestCriteriaOperator.testGetByNameIgnoreCase(CriteriaOperator.Between);
    }

    @Test
    void testGetByNameNotIgnoreCaseBetween() {
        TestCriteriaOperator.testGetByNameNotIgnoreCase(CriteriaOperator.Between);
    }

    @Test
    void testGetByNameIgnoreCaseNotBetween() {
        TestCriteriaOperator.testGetByNameIgnoreCaseNot(CriteriaOperator.Between);
    }

    @Test
    void testGetByNameLike() {
        TestCriteriaOperator.testGetByName(CriteriaOperator.Like);
    }

    @Test
    void testGetByNameNotLike() {
        TestCriteriaOperator.testGetByNameNot(CriteriaOperator.Like);
    }

    @Test
    void testGetByNameIgnoreCaseLike() {
        TestCriteriaOperator.testGetByNameIgnoreCase(CriteriaOperator.Like);
    }

    @Test
    void testGetByNameNotIgnoreCaseLike() {
        TestCriteriaOperator.testGetByNameNotIgnoreCase(CriteriaOperator.Like);
    }

    @Test
    void testGetByNameIgnoreCaseNotLike() {
        TestCriteriaOperator.testGetByNameIgnoreCaseNot(CriteriaOperator.Like);
    }

    @Test
    void testGetByNameIn() {
        TestCriteriaOperator.testGetByName(CriteriaOperator.In);
    }

    @Test
    void testGetByNameNotIn() {
        TestCriteriaOperator.testGetByNameNot(CriteriaOperator.In);
    }

    @Test
    void testGetByNameIgnoreCaseIn() {
        TestCriteriaOperator.testGetByNameIgnoreCase(CriteriaOperator.In);
    }

    @Test
    void testGetByNameNotIgnoreCaseIn() {
        TestCriteriaOperator.testGetByNameNotIgnoreCase(CriteriaOperator.In);
    }

    @Test
    void testGetByNameIgnoreCaseNotIn() {
        TestCriteriaOperator.testGetByNameIgnoreCaseNot(CriteriaOperator.In);
    }

    @Test
    void testGetByNameEmpty() {
        TestCriteriaOperator.testGetByName(CriteriaOperator.Empty);
    }

    @Test
    void testGetByNameNotEmpty() {
        TestCriteriaOperator.testGetByNameNot(CriteriaOperator.Empty);
    }

    @Test
    void testGetByNameIgnoreCaseEmpty() {
        TestCriteriaOperator.testGetByNameIgnoreCase(CriteriaOperator.Empty);
    }

    @Test
    void testGetByNameNotIgnoreCaseEmpty() {
        TestCriteriaOperator.testGetByNameNotIgnoreCase(CriteriaOperator.Empty);
    }

    @Test
    void testGetByNameIgnoreCaseNotEmpty() {
        TestCriteriaOperator.testGetByNameIgnoreCaseNot(CriteriaOperator.Empty);
    }

    @Test
    void testGetByNameNull() {
        TestCriteriaOperator.testGetByName(CriteriaOperator.Null);
    }

    @Test
    void testGetByNameNotNull() {
        TestCriteriaOperator.testGetByNameNot(CriteriaOperator.Null);
    }

    @Test
    void testGetByNameIgnoreCaseNull() {
        TestCriteriaOperator.testGetByNameIgnoreCase(CriteriaOperator.Null);
    }

    @Test
    void testGetByNameNotIgnoreCaseNull() {
        TestCriteriaOperator.testGetByNameNotIgnoreCase(CriteriaOperator.Null);
    }

    @Test
    void testGetByNameIgnoreCaseNotNull() {
        TestCriteriaOperator.testGetByNameIgnoreCaseNot(CriteriaOperator.Null);
    }

    @Test
    void testGetByNameTrue() {
        TestCriteriaOperator.testGetByName(CriteriaOperator.True);
    }

    @Test
    void testGetByNameNotTrue() {
        TestCriteriaOperator.testGetByNameNot(CriteriaOperator.True);
    }

    @Test
    void testGetByNameIgnoreCaseTrue() {
        TestCriteriaOperator.testGetByNameIgnoreCase(CriteriaOperator.True);
    }

    @Test
    void testGetByNameNotIgnoreCaseTrue() {
        TestCriteriaOperator.testGetByNameNotIgnoreCase(CriteriaOperator.True);
    }

    @Test
    void testGetByNameIgnoreCaseNotTrue() {
        TestCriteriaOperator.testGetByNameIgnoreCaseNot(CriteriaOperator.True);
    }

    @Test
    void testGetByNameFalse() {
        TestCriteriaOperator.testGetByName(CriteriaOperator.False);
    }

    @Test
    void testGetByNameNotFalse() {
        TestCriteriaOperator.testGetByNameNot(CriteriaOperator.False);
    }

    @Test
    void testGetByNameIgnoreCaseFalse() {
        TestCriteriaOperator.testGetByNameIgnoreCase(CriteriaOperator.False);
    }

    @Test
    void testGetByNameNotIgnoreCaseFalse() {
        TestCriteriaOperator.testGetByNameNotIgnoreCase(CriteriaOperator.False);
    }

    @Test
    void testGetByNameIgnoreCaseNotFalse() {
        TestCriteriaOperator.testGetByNameIgnoreCaseNot(CriteriaOperator.False);
    }

    @Test
    void testFindByNameIgnoreCaseAndAgeNotGreaterThan() {
        MethodNameParser parser = MethodNameParserImpl.create();
        assertThat(parser.parse("findByPerson_nameIgnoreCaseAndAgeNotGreaterThan"), is(true));
        DataQuery query = parser.dataQuery();
        validateProjection(query.projection(),
                           ProjectionAction.Select,
                           Optional.of(ProjectionResult.Find),
                           Optional.empty(),
                           Optional.empty(),
                           false);
        validateCriteria(query.criteria(),
                         Optional.of(Criteria.builder()
                                             .condition(CriteriaCondition.builder()
                                                                .property(Property.create(new String[] {"person", "name"}))
                                                                .operator(CriteriaOperator.Equal)
                                                                .ignoreCase(true)
                                                                .build())
                                             .and()
                                             .condition(CriteriaCondition.builder()
                                                                .property(Property.create("age"))
                                                                .operator(CriteriaOperator.GreaterThan)
                                                                .not(true)
                                                                .build())
                                             .build()));
        validateOrder(query.order(), Optional.empty());
    }

    // Validate "findByNameOrderBy" parsing to indicate failure in regular mode
    @Test
    void testFindByNameOrderBy() {
        boolean result = MethodNameParserImpl.create()
                .parse("findByNameOrderBy");
        assertThat(result, is(false));
    }

    @Test
    void testFindOrderByName() {
        MethodNameParser parser = MethodNameParserImpl.create();
        assertThat(parser.parse("findAllOrderByName"), is(true));
        DataQuery query = parser.dataQuery();
        validateProjection(query.projection(),
                           ProjectionAction.Select,
                           Optional.of(ProjectionResult.Find),
                           Optional.empty(),
                           Optional.empty(),
                           false);
        validateOrder(query.order(),
                      Optional.of(Order.builder()
                                          .addExpression(
                                                  OrderExpression.builder()
                                                          .property(Property.create("name"))
                                                          .operator(OrderOperator.ASC)
                                                          .build())
                                          .build()));
    }

    @Test
    void testListOrderByName() {
        MethodNameParser parser = MethodNameParserImpl.create();
        assertThat(parser.parse("listAllOrderByName"), is(true));
        DataQuery query = parser.dataQuery();
        validateProjection(query.projection(),
                           ProjectionAction.Select,
                           Optional.of(ProjectionResult.List),
                           Optional.empty(),
                           Optional.empty(),
                           false);
        validateOrder(query.order(),
                      Optional.of(Order.builder()
                                          .addExpression(
                                                  OrderExpression.builder()
                                                          .property(Property.create("name"))
                                                          .operator(OrderOperator.ASC)
                                                          .build())
                                          .build()));
    }

    @Test
    void testStreamOrderByName() {
        MethodNameParser parser = MethodNameParserImpl.create();
        assertThat(parser.parse("streamAllOrderByName"), is(true));
        DataQuery query = parser.dataQuery();
        validateProjection(query.projection(),
                           ProjectionAction.Select,
                           Optional.of(ProjectionResult.Stream),
                           Optional.empty(),
                           Optional.empty(),
                           false);
        validateOrder(query.order(),
                      Optional.of(Order.builder()
                                          .addExpression(
                                                  OrderExpression.builder()
                                                          .property(Property.create("name"))
                                                          .operator(OrderOperator.ASC)
                                                          .build())
                                          .build()));
    }

    @Test
    void testFindAllOrderByName() {
        MethodNameParser parser = MethodNameParserImpl.create();
        assertThat(parser.parse("findAllOrderByName"), is(true));
        DataQuery query = parser.dataQuery();
        validateProjection(query.projection(),
                           ProjectionAction.Select,
                           Optional.of(ProjectionResult.Find),
                           Optional.empty(),
                           Optional.empty(),
                           false);
        validateOrder(query.order(),
                      Optional.of(Order.builder()
                                          .addExpression(
                                                  OrderExpression.builder()
                                                          .property(Property.create("name"))
                                                          .operator(OrderOperator.ASC)
                                                          .build())
                                          .build()));
    }

    @Test
    void testListAllOrderByName() {
        MethodNameParser parser = MethodNameParserImpl.create();
        assertThat(parser.parse("listAllOrderByName"), is(true));
        DataQuery query = parser.dataQuery();
        validateProjection(query.projection(),
                           ProjectionAction.Select,
                           Optional.of(ProjectionResult.List),
                           Optional.empty(),
                           Optional.empty(),
                           false);
        validateOrder(query.order(),
                      Optional.of(Order.builder()
                                          .addExpression(
                                                  OrderExpression.builder()
                                                          .property(Property.create("name"))
                                                          .operator(OrderOperator.ASC)
                                                          .build())
                                          .build()));
    }

    @Test
    void testStreamAllOrderByName() {
        MethodNameParser parser = MethodNameParserImpl.create();
        assertThat(parser.parse("streamAllOrderByName"), is(true));
        DataQuery query = parser.dataQuery();
        validateProjection(query.projection(),
                           ProjectionAction.Select,
                           Optional.of(ProjectionResult.Stream),
                           Optional.empty(),
                           Optional.empty(),
                           false);
        validateOrder(query.order(),
                      Optional.of(Order.builder()
                                          .addExpression(
                                                  OrderExpression.builder()
                                                          .property(Property.create("name"))
                                                          .operator(OrderOperator.ASC)
                                                          .build())
                                          .build()));
    }

    @Test
    void testFindByNameOrderByAge() {
        MethodNameParser parser = MethodNameParserImpl.create();
        assertThat(parser.parse("findByNameOrderByAge"), is(true));
        DataQuery query = parser.dataQuery();
        validateProjection(query.projection(),
                           ProjectionAction.Select,
                           Optional.of(ProjectionResult.Find),
                           Optional.empty(),
                           Optional.empty(),
                           false);
        validateCriteria(query.criteria(),
                         Optional.of(Criteria.create(
                                 CriteriaCondition.createEqual(Property.create("name")))));
        validateOrder(query.order(),
                      Optional.of(Order.builder()
                                          .addExpression(
                                                  OrderExpression.builder()
                                                          .property(Property.create("age"))
                                                          .operator(OrderOperator.ASC)
                                                          .build())
                                          .build()));
    }

    @Test
    void testFindByNameNotOrderByAge() {
        MethodNameParser parser = MethodNameParserImpl.create();
        assertThat(parser.parse("findByNameNotOrderByAge"), is(true));
        DataQuery query = parser.dataQuery();
        validateProjection(query.projection(),
                           ProjectionAction.Select,
                           Optional.of(ProjectionResult.Find),
                           Optional.empty(),
                           Optional.empty(),
                           false);
        validateCriteria(query.criteria(),
                         Optional.of(Criteria.create(
                                 CriteriaCondition.builder()
                                         .property(Property.create("name"))
                                         .operator(CriteriaOperator.Equal)
                                         .not(true)
                                         .build())));
        validateOrder(query.order(),
                      Optional.of(Order.builder()
                                          .addExpression(
                                                  OrderExpression.builder()
                                                          .property(Property.create("age"))
                                                          .operator(OrderOperator.ASC)
                                                          .build())
                                          .build()));
    }

    @Test
    void testFindByNameIgnoreCaseOrderByAge() {
        MethodNameParser parser = MethodNameParserImpl.create();
        assertThat(parser.parse("findByNameIgnoreCaseOrderByAge"), is(true));
        DataQuery query = parser.dataQuery();
        validateProjection(query.projection(),
                           ProjectionAction.Select,
                           Optional.of(ProjectionResult.Find),
                           Optional.empty(),
                           Optional.empty(),
                           false);
        validateCriteria(query.criteria(),
                         Optional.of(Criteria.create(
                                 CriteriaCondition.builder()
                                         .property(Property.create("name"))
                                         .operator(CriteriaOperator.Equal)
                                         .ignoreCase(true)
                                         .build())));
        validateOrder(query.order(),
                      Optional.of(Order.builder()
                                          .addExpression(
                                                  OrderExpression.builder()
                                                          .property(Property.create("age"))
                                                          .operator(OrderOperator.ASC)
                                                          .build())
                                          .build()));
    }

    @Test
    void testFindByNameNotIgnoreCaseOrderByAge() {
        MethodNameParser parser = MethodNameParserImpl.create();
        assertThat(parser.parse("findByNameNotIgnoreCaseOrderByAge"), is(true));
        DataQuery query = parser.dataQuery();
        validateProjection(query.projection(),
                           ProjectionAction.Select,
                           Optional.of(ProjectionResult.Find),
                           Optional.empty(),
                           Optional.empty(),
                           false);
        validateCriteria(query.criteria(),
                         Optional.of(Criteria.create(
                                 CriteriaCondition.builder()
                                         .property(Property.create("name"))
                                         .operator(CriteriaOperator.Equal)
                                         .not(true)
                                         .ignoreCase(true)
                                         .build())));
        validateOrder(query.order(),
                      Optional.of(Order.builder()
                                          .addExpression(
                                                  OrderExpression.builder()
                                                          .property(Property.create("age"))
                                                          .operator(OrderOperator.ASC)
                                                          .build())
                                          .build()));
    }

    @Test
    void testFindByNameIgnoreCaseNotOrderByAge() {
        MethodNameParser parser = MethodNameParserImpl.create();
        assertThat(parser.parse("findByNameIgnoreCaseNotOrderByAge"), is(true));
        DataQuery query = parser.dataQuery();
        validateProjection(query.projection(),
                           ProjectionAction.Select,
                           Optional.of(ProjectionResult.Find),
                           Optional.empty(),
                           Optional.empty(),
                           false);
        validateCriteria(query.criteria(),
                         Optional.of(Criteria.create(
                                 CriteriaCondition.builder()
                                         .property(Property.create("name"))
                                         .operator(CriteriaOperator.Equal)
                                         .not(true)
                                         .ignoreCase(true)
                                         .build())));
        validateOrder(query.order(),
                      Optional.of(Order.builder()
                                          .addExpression(
                                                  OrderExpression.builder()
                                                          .property(Property.create("age"))
                                                          .operator(OrderOperator.ASC)
                                                          .build())
                                          .build()));
    }

    @Test
    void testFindIdByAgeOrderByName() {
        MethodNameParser parser = MethodNameParserImpl.create();
        assertThat(parser.parse("findIdByAgeOrderByName"), is(true));
        DataQuery query = parser.dataQuery();
        validateProjection(query.projection(),
                           ProjectionAction.Select,
                           Optional.of(ProjectionResult.Find),
                           Optional.empty(),
                           Optional.of(Property.create("id")),
                           false);
        validateCriteria(query.criteria(),
                         Optional.of(Criteria.create(
                                 CriteriaCondition.createEqual(Property.create("age")))));
        validateOrder(query.order(),
                      Optional.of(Order.builder()
                                          .addExpression(
                                                  OrderExpression.builder()
                                                          .property(Property.create("name"))
                                                          .operator(OrderOperator.ASC)
                                                          .build())
                                          .build()));
    }

    @Test
    void testFindByNameOrderByAgeAsc() {
        MethodNameParser parser = MethodNameParserImpl.create();
        assertThat(parser.parse("findByNameOrderByAgeAsc"), is(true));
        DataQuery query = parser.dataQuery();
        validateProjection(query.projection(),
                           ProjectionAction.Select,
                           Optional.of(ProjectionResult.Find),
                           Optional.empty(),
                           Optional.empty(),
                           false);
        validateCriteria(query.criteria(),
                         Optional.of(Criteria.create(
                                 CriteriaCondition.createEqual(Property.create("name")))));
        validateOrder(query.order(),
                      Optional.of(Order.builder()
                                          .addExpression(
                                                  OrderExpression.builder()
                                                          .property(Property.create("age"))
                                                          .operator(OrderOperator.ASC)
                                                          .build())
                                          .build()));
    }

    @Test
    void testFindByNameOrderByAgeDesc() {
        MethodNameParser parser = MethodNameParserImpl.create();
        assertThat(parser.parse("findByNameOrderByAgeDesc"), is(true));
        DataQuery query = parser.dataQuery();
        validateProjection(query.projection(),
                           ProjectionAction.Select,
                           Optional.of(ProjectionResult.Find),
                           Optional.empty(),
                           Optional.empty(),
                           false);
        validateCriteria(query.criteria(),
                         Optional.of(Criteria.create(
                                 CriteriaCondition.createEqual(Property.create("name")))));
        validateOrder(query.order(),
                      Optional.of(Order.builder()
                                          .addExpression(
                                                  OrderExpression.builder()
                                                          .property(Property.create("age"))
                                                          .operator(OrderOperator.DESC)
                                                          .build())
                                          .build()));
    }

    @Test
    void testFindByNameOrderByNameAscAgeDesc() {
        MethodNameParser parser = MethodNameParserImpl.create();
        assertThat(parser.parse("findByNameOrderByNameAscAgeDesc"), is(true));
        DataQuery query = parser.dataQuery();
        validateProjection(query.projection(),
                           ProjectionAction.Select,
                           Optional.of(ProjectionResult.Find),
                           Optional.empty(),
                           Optional.empty(),
                           false);
        validateCriteria(query.criteria(),
                         Optional.of(Criteria.create(
                                 CriteriaCondition.createEqual(Property.create("name")))));
        validateOrder(query.order(),
                      Optional.of(Order.builder()
                                          .addExpression(
                                                  OrderExpression.builder()
                                                          .property(Property.create("name"))
                                                          .operator(OrderOperator.ASC)
                                                          .build())
                                          .addExpression(
                                                  OrderExpression.builder()
                                                          .property(Property.create("age"))
                                                          .operator(OrderOperator.DESC)
                                                          .build())
                                          .build()));
    }

    @Test
    void testFindByNameOrderByNameDescAgeAsc() {
        MethodNameParser parser = MethodNameParserImpl.create();
        assertThat(parser.parse("findByNameOrderByNameDescAgeAsc"), is(true));
        DataQuery query = parser.dataQuery();
        validateProjection(query.projection(),
                           ProjectionAction.Select,
                           Optional.of(ProjectionResult.Find),
                           Optional.empty(),
                           Optional.empty(),
                           false);
        validateCriteria(query.criteria(),
                         Optional.of(Criteria.create(
                                 CriteriaCondition.createEqual(Property.create("name")))));
        validateOrder(query.order(),
                      Optional.of(Order.builder()
                                          .addExpression(
                                                  OrderExpression.builder()
                                                          .property(Property.create("name"))
                                                          .operator(OrderOperator.DESC)
                                                          .build())
                                          .addExpression(
                                                  OrderExpression.builder()
                                                          .property(Property.create("age"))
                                                          .operator(OrderOperator.ASC)
                                                          .build())
                                          .build()));
    }

    @Test
    void testGetIdOrderByFirstNameAscSecondNameDescAge() {
        MethodNameParser parser = MethodNameParserImpl.create();
        assertThat(parser.parse("getIdOrderByFirstNameAscSecondNameDescAge"), is(true));
        DataQuery query = parser.dataQuery();
        validateProjection(query.projection(),
                           ProjectionAction.Select,
                           Optional.of(ProjectionResult.Get),
                           Optional.empty(),
                           Optional.of(Property.create("id")),
                           false);
        validateCriteria(query.criteria(), Optional.empty());
        validateOrder(query.order(),
                      Optional.of(Order.builder()
                                          .addExpression(
                                                  OrderExpression.builder()
                                                          .property(Property.create("firstName"))
                                                          .operator(OrderOperator.ASC)
                                                          .build())
                                          .addExpression(
                                                  OrderExpression.builder()
                                                          .property(Property.create("secondName"))
                                                          .operator(OrderOperator.DESC)
                                                          .build())
                                          .addExpression(
                                                  OrderExpression.builder()
                                                          .property(Property.create("age"))
                                                          .operator(OrderOperator.ASC)
                                                          .build())
                                          .build()));

    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private static void validateProjection(Projection projection,
                                           ProjectionAction expectedAction,
                                           Optional<ProjectionResult> expectedResult,
                                           Optional<ProjectionExpression> expectedExpression,
                                           Optional<Property> expectedProperty,
                                           boolean expectedDistinct) {
        assertThat(projection.action(), is(expectedAction));
        validateProjectionResult(projection.result(), expectedResult);
        validateProjectionExpression(projection.expression(), expectedExpression);
        validateProjectionProperty(projection.property(), expectedProperty);
        assertThat("Projection distinct value is not " + expectedDistinct,
                   projection.distinct(),
                   is(expectedDistinct));
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private static void validateProjectionResult(Optional<ProjectionResult> maybeResult, Optional<ProjectionResult> expected) {
        expected.ifPresentOrElse(
                expectedResult -> {
                    assertThat("Projection result is empty",
                               maybeResult.isEmpty(),
                               is(false));
                    assertThat("Projection result is not " + expectedResult,
                               maybeResult.get(),
                               is(expectedResult));
                },
                () -> assertThat("Projection result is not empty", maybeResult.isEmpty(), is(true))
        );
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private static void validateProjectionExpression(Optional<ProjectionExpression> maybeExpression,
                                                     Optional<ProjectionExpression> expected) {
        expected.ifPresentOrElse(
                expression -> {
                    assertThat("Projection expression is empty",
                               maybeExpression.isEmpty(),
                               is(false));
                    ProjectionExpression validatedExpression = maybeExpression.get();
                    assertThat("Projection expression is not " + expression.operator(),
                               validatedExpression.operator(),
                               is(expression.operator()));
                    expression.parameter().ifPresentOrElse(
                            parameter -> {
                                assertThat("Projection expression parameter is empty",
                                           expression.parameter().isEmpty(),
                                           is(false));
                                assertThat("Projection expression parameter value is not " + parameter.value(),
                                           expression.parameter().get().value(),
                                           is(parameter.value()));
                            },
                            () -> assertThat("Projection expression parameter is not empty",
                                             expression.parameter().isEmpty(),
                                             is(true))
                    );
                },
                () -> assertThat("Projection expression is not empty", maybeExpression.isEmpty(), is(true))
        );
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private static void validateProjectionProperty(Optional<Property> maybeProperty, Optional<Property> expected) {
        expected.ifPresentOrElse(
                property -> {
                    assertThat("Projection property is empty",
                               maybeProperty.isEmpty(),
                               is(false));
                    assertThat("Projection property is not " + maybeProperty.get(),
                               property,
                               equalTo(maybeProperty.get()));
                },
                () -> assertThat("Projection property is not empty",
                                 maybeProperty.isEmpty(),
                                 is(true))
        );
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private static void validateCriteria(Optional<Criteria> maybeCriteria, Optional<Criteria> expected) {
        expected.ifPresentOrElse(
                expectedCriteria -> {
                    assertThat("Criteria is empty", maybeCriteria.isEmpty(), is(false));
                    validateCriteriaExpression(maybeCriteria.get().first(),
                                               expectedCriteria.first());
                    // Message size must add expectedCriteria.first() too
                    assertThat("Count of criteria conditions is not " + (expectedCriteria.next().size() + 1),
                               maybeCriteria.get().next().size(),
                               is(expectedCriteria.next().size()));
                    for (int i = 0; i < expectedCriteria.next().size(); i++) {
                        assertThat("Criteria logical operator between " + ordStr(i + 1)
                                           + " and " + ordStr(i + 2) + " condition is not "
                                           + expectedCriteria.next(i).operator(),
                                   maybeCriteria.get().next(i).operator(),
                                   is(expectedCriteria.next(i).operator()));
                        validateCriteriaExpression(maybeCriteria.get().next(i).criteria(),
                                                   expectedCriteria.next(i).criteria());
                    }
                },
                () -> assertThat("Criteria is not empty", maybeCriteria.isEmpty(), is(true))
        );
    }

    private static void validateCriteriaExpression(CriteriaCondition expression, CriteriaCondition expected) {
        assertThat("Criteria expression property value is not " + expected.property(),
                   expression.property(),
                   equalTo(expected.property()));
        assertThat("Criteria expression operator value is not " + expected.operator(),
                   expression.operator(),
                   is(expected.operator()));
        assertThat("Criteria expression Not value is not " + expected.not(),
                   expression.not(),
                   is(expected.not()));
        assertThat("Criteria expression IgnoreCase value is not " + expected.ignoreCase(),
                   expression.ignoreCase(),
                   is(expected.ignoreCase()));
        expected.parameters().ifPresentOrElse(
                parameters -> {
                    assertThat("Criteria expression parameters is empty",
                               expression.parameters().isEmpty(),
                               is(false));
                    assertThat("Criteria expression parameters count is not " + parameters.count(),
                               expression.parameters().get().count(),
                               is(parameters.count()));
                    for (int i = 0; i < parameters.count(); i++) {
                        assertThat("Criteria expression parameter[" + i + "] value is not " + parameters.get(i),
                                   expression.parameters().get().get(i),
                                   is(parameters.get(i)));
                    }
                },
                () -> assertThat("Criteria expression parameters is not empty",
                                 expression.parameters().isEmpty(),
                                 is(true))
        );
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private static void validateOrder(Optional<Order> maybeOrder, Optional<Order> expected) {
        expected.ifPresentOrElse(
                expectedOrder -> {
                    assertThat("Order is empty", maybeOrder.isEmpty(), is(false));
                    assertThat("Count of order expressions is not " + (expectedOrder.expressions().size()),
                               maybeOrder.get().expressions().size(),
                               is(expectedOrder.expressions().size()));
                    for (int i = 0; i < expectedOrder.expressions().size(); i++) {
                        validateOrderExpression(maybeOrder.get().expression(i), expectedOrder.expression(i));
                    }
                },
                () -> assertThat("Order is not empty", maybeOrder.isEmpty(), is(true))
        );
    }

    private static void validateOrderExpression(OrderExpression expression, OrderExpression expected) {
        assertThat("Order expression property value is not " + expected.property(),
                   expression.property(),
                   equalTo(expected.property()));
        assertThat("Order expression operator is not " + expected.operator(),
                   expression.operator(),
                   is(expected.operator()));
    }

    // Let's make exception output nice.
    private static String ordStr(int i) {
        return switch (i) {
            case 1 -> i + "st";
            case 2 -> i + "nd";
            case 3 -> i + "rd";
            default -> i + "th";
        };
    }

    private static final class TestProjection {

        private TestProjection() {
            throw new UnsupportedOperationException("No instances of TestProjection are allowed");
        }

        @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
        private static void test(String methodName,
                                 ProjectionResult resultType,
                                 Optional<ProjectionExpression> maybeExpression,
                                 Optional<Property> maybeProperty) {
            test(methodName,
                 resultType,
                 maybeExpression,
                 maybeProperty,
                 false);
        }

        @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
        private static void test(String methodName,
                                 ProjectionResult resultType,
                                 Optional<ProjectionExpression> maybeExpression,
                                 Optional<Property> maybeProperty,
                                 boolean distinct) {
            MethodNameParser parser = MethodNameParserImpl.create();
            assertThat(parser.parse(methodName), is(true));
            DataQuery query = parser.dataQuery();
            validateProjection(query.projection(),
                               ProjectionAction.Select,
                               Optional.of(resultType),
                               maybeExpression,
                               maybeProperty,
                               distinct);
            validateCriteria(query.criteria(), Optional.empty());
            validateOrder(query.order(), Optional.empty());
        }

    }

    private static final class TestCriteriaProperty {

        private TestCriteriaProperty() {
            throw new UnsupportedOperationException("No instances of TestCriteriaProperty are allowed");
        }

        static void testByX(ProjectionResult resultType, ProjectionExpression expectedExpression) {
            String methodNamePrefix = resultType.name().toLowerCase();
            MethodNameParser parser = MethodNameParserImpl.create();
            assertThat(parser.parse(methodNamePrefix + "ByX"), is(true));
            DataQuery query = parser.dataQuery();
            validateProjection(query.projection(),
                               ProjectionAction.Select,
                               Optional.of(resultType),
                               Optional.ofNullable(expectedExpression),
                               Optional.empty(),
                               false);
            validateCriteria(query.criteria(),
                             Optional.of(Criteria.create(CriteriaCondition.createEqual(Property.create("x")))));
            validateOrder(query.order(), Optional.empty());
        }

        static void testByName(ProjectionResult resultType, ProjectionExpression expectedExpression) {
            String methodNamePrefix = resultType.name().toLowerCase();
            MethodNameParser parser = MethodNameParserImpl.create();
            assertThat(parser.parse(methodNamePrefix + "ByName"), is(true));
            DataQuery query = parser.dataQuery();
            validateProjection(query.projection(),
                               ProjectionAction.Select,
                               Optional.of(resultType),
                               Optional.ofNullable(expectedExpression),
                               Optional.empty(),
                               false);
            validateCriteria(query.criteria(),
                             Optional.of(Criteria.create(CriteriaCondition.createEqual(Property.create("name")))));
            validateOrder(query.order(), Optional.empty());
        }

    }

    private static final class TestCriteriaOperator {

        private TestCriteriaOperator() {
            throw new UnsupportedOperationException("No instances of TestCriteriaOperator are allowed");
        }

        static void testGetByName(CriteriaOperator operator) {
            MethodNameParser parser = MethodNameParserImpl.create();
            assertThat(parser.parse("getByName" + operator), is(true));
            DataQuery query = parser.dataQuery();
            validateProjection(query.projection(),
                               ProjectionAction.Select,
                               Optional.of(ProjectionResult.Get),
                               Optional.empty(),
                               Optional.empty(),
                               false);
            validateCriteria(query.criteria(),
                             Optional.of(Criteria.create(
                                     CriteriaCondition.builder()
                                             .property(Property.create("name"))
                                             .operator(operator)
                                             .build())));
            validateOrder(query.order(), Optional.empty());
        }

        static void testGetByNameNot(CriteriaOperator operator) {
            MethodNameParser parser = MethodNameParserImpl.create();
            assertThat(parser.parse("getByNameNot" + operator), is(true));
            DataQuery query = parser.dataQuery();
            validateProjection(query.projection(),
                               ProjectionAction.Select,
                               Optional.of(ProjectionResult.Get),
                               Optional.empty(),
                               Optional.empty(),
                               false);
            validateCriteria(query.criteria(),
                             Optional.of(Criteria.create(
                                     CriteriaCondition.builder()
                                             .property(Property.create("name"))
                                             .operator(operator)
                                             .not(true)
                                             .build())));
            validateOrder(query.order(), Optional.empty());
        }

        static void testGetByNameIgnoreCase(CriteriaOperator operator) {
            MethodNameParser parser = MethodNameParserImpl.create();
            assertThat(parser.parse("getByNameIgnoreCase" + operator), is(true));
            DataQuery query = parser.dataQuery();
            validateProjection(query.projection(),
                               ProjectionAction.Select,
                               Optional.of(ProjectionResult.Get),
                               Optional.empty(),
                               Optional.empty(),
                               false);
            validateCriteria(query.criteria(),
                             Optional.of(Criteria.create(
                                     CriteriaCondition.builder()
                                             .property(Property.create("name"))
                                             .operator(operator)
                                             .ignoreCase(true)
                                             .build())));
            validateOrder(query.order(), Optional.empty());
        }

        static void testGetByNameNotIgnoreCase(CriteriaOperator operator) {
            MethodNameParser parser = MethodNameParserImpl.create();
            assertThat(parser.parse("getByNameNotIgnoreCase" + operator), is(true));
            DataQuery query = parser.dataQuery();
            validateProjection(query.projection(),
                               ProjectionAction.Select,
                               Optional.of(ProjectionResult.Get),
                               Optional.empty(),
                               Optional.empty(),
                               false);
            validateCriteria(query.criteria(),
                             Optional.of(Criteria.create(
                                     CriteriaCondition.builder()
                                             .property(Property.create("name"))
                                             .operator(operator)
                                             .not(true)
                                             .ignoreCase(true)
                                             .build())));
            validateOrder(query.order(), Optional.empty());
        }

        static void testGetByNameIgnoreCaseNot(CriteriaOperator operator) {
            MethodNameParser parser = MethodNameParserImpl.create();
            assertThat(parser.parse("getByNameIgnoreCaseNot" + operator), is(true));
            DataQuery query = parser.dataQuery();
            validateProjection(query.projection(),
                               ProjectionAction.Select,
                               Optional.of(ProjectionResult.Get),
                               Optional.empty(),
                               Optional.empty(),
                               false);
            validateCriteria(query.criteria(),
                             Optional.of(Criteria.create(
                                     CriteriaCondition.builder()
                                             .property(Property.create("name"))
                                             .operator(operator)
                                             .not(true)
                                             .ignoreCase(true)
                                             .build())));
            validateOrder(query.order(), Optional.empty());
        }

    }

}

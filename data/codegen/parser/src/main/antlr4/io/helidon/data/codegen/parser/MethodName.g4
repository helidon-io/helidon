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

lexer grammar MethodName;

@header {/*
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
}

@members {
private boolean isDml = false;

public void reset(CharStream input) {
    reset();
    isDml = false;
    setInputStream(input);
}

/*
    Report error when "OrderBy" is used with update or delete
    Adding update/delete specific set of rules will make parser complexity too high.
    This syntax check is implemented programmatically.
*/
private void checkDmlOrderBy(RuleContext ctx) {
    if (isDml) {
        throw new RecognitionException("OrderBy cannot be used with delete or update",
                                       this,
                                       _input,
                                       (ParserRuleContext) ctx);
    }
}
}

// Vocabulary
fragment COUNT: 'count';
fragment EXISTS: 'exists';
fragment GET: 'get';
fragment FIND: 'find';
fragment LIST: 'list';
fragment STREAM: 'stream';
fragment UPDATE: 'update';
fragment DELETE: 'delete';

fragment U_COUNT: 'Count';
fragment U_EXISTS: 'Exists';
fragment U_GET: 'Get';
fragment U_FIND: 'Find';
fragment U_LIST: 'List';
fragment U_STREAM: 'Stream';
fragment U_UPDATE: 'Update';
fragment U_DELETE: 'Delete';

fragment FIRST: 'First';
fragment DISTINCT: 'Distinct';
fragment MAX: 'Max';
fragment MIN: 'Min';
fragment SUM: 'Sum';
fragment AVG: 'Avg';

fragment ALL: 'All';

fragment AFTER: 'After';
fragment BEFORE: 'Before';
fragment CONTAINS: 'Contains';
fragment ENDS_WITH: 'EndsWith';
fragment STARTS_WITH: 'StartsWith';
fragment EQUAL: 'Equal';
fragment LESS_THAN: 'LessThan';
fragment LESS_THAN_EQUAL: 'LessThanEqual';
fragment GREATER_THAN: 'GreaterThan';
fragment GREATER_THAN_EQUAL: 'GreaterThanEqual';
fragment BETWEEN: 'Between';
fragment LIKE: 'Like';
fragment IN: 'In';
fragment EMPTY: 'Empty';
fragment NULL: 'Null';
fragment TRUE: 'True';
fragment FALSE: 'False';

fragment NOT: 'Not';
fragment IGNORE_CASE: 'IgnoreCase';

fragment AND: 'And';
fragment OR: 'Or';

fragment ASC: 'Asc';
fragment DESC: 'Desc';

fragment BY: 'By';
fragment ORDER_BY: 'OrderBy';

fragment UNDERSCORE: '_';
fragment POS_INT: [0-9]+;

//        method-name  ::= <query> | <update> | <delete>
//        query        ::= [ <prefix> ] <query-action> [ <projection> ] [ "By" <criteria>  [ "OrderBy" <order> ] ]
//                         | [ <prefix> ] <all-action> All [ "OrderBy" <order> ]
//        update       ::= [ <prefix> ] "update" "By" <criteria> ] | [ <prefix> ] "update" "All"
//        delete       ::= [ <prefix> ] "delete" "By" <criteria> ] | [ <prefix> ] "delete" "All"
//        query-action ::= "count" |  "exists" | "get" | <all-action>
//        all-action   ::= "find" | "list" | "stream"
//        prefix       ::= [a-zA-Z0-9]*

// Starting mode: expecting action (mandatory initial token)
Exists: EXISTS -> mode(Projection);
Count: COUNT -> mode(Projection);
Get: GET -> mode(Projection);
Find: FIND -> mode(AllProjection);
List: LIST -> mode(AllProjection);
Stream: STREAM -> mode(AllProjection);
Update: UPDATE -> mode(ModeBy);
Delete: DELETE -> mode(ModeBy);
PrefixExists: [a-zA-Z0-9]*? U_EXISTS -> mode(Projection),
                                        type(Exists);
PrefixCount: [a-zA-Z0-9]*? U_COUNT -> mode(Projection),
                                      type(Count);
PrefixGet: [a-zA-Z0-9]*? U_GET -> mode(Projection),
                                  type(Get);
PrefixFind: [a-zA-Z0-9]*? U_FIND -> mode(AllProjection),
                                    type(Find);
PrefixList: [a-zA-Z0-9]*? U_LIST -> mode(AllProjection),
                                    type(List);
PrefixStream: [a-zA-Z0-9]*? U_STREAM -> mode(AllProjection),
                                        type(Stream);
PrefixUpdate: [a-zA-Z0-9]*? U_UPDATE -> mode(ModeBy),
                                        type(Update);
PrefixDelete: [a-zA-Z0-9]*? U_DELETE -> mode(ModeBy),
                                        type(Delete);

//        projection ::= [ <expression> ] [ <property> ]
//        expression ::= "First" <number> [ "Distinct" ] | "Distinct"
//                       | "Max" | "Min" | "Sum" | "Avg"

mode Projection;
    First: FIRST POS_INT -> mode(FirstDistinct);
    Distinct: DISTINCT -> mode(ProjectionPropertyFirst);
    Max: MAX -> mode(ProjectionPropertyFirst);
    Min: MIN -> mode(ProjectionPropertyFirst);
    Sum: SUM -> mode(ProjectionPropertyFirst);
    Avg: AVG -> mode(ProjectionPropertyFirst);
    // Skip to property when projection expression is missing
    Property: [a-zA-Z] -> mode(ProjectionPropertyNext),
                          type(ProjPropFirst);
    // Skip directly to criteria when projection is missing
    DirectBy: BY -> mode(Criteria),
                    type(By);
    // Skip directly to order when projection and criteria are missing
    OrderBy: ORDER_BY -> mode(Order);

// Handle sub-rule for query:
//        query ::= [ <prefix> ] <all-action> All [ "OrderBy" <order> ]
mode AllProjection;
    AllFirst: FIRST POS_INT -> mode(FirstDistinct),
                               type(First);
    AllDistinct: DISTINCT -> mode(ProjectionPropertyFirst),
                             type(Distinct);
    AllMax: MAX -> mode(ProjectionPropertyFirst),
                   type(Max);
    AllMin: MIN -> mode(ProjectionPropertyFirst),
                   type(Min);
    AllSum: SUM -> mode(ProjectionPropertyFirst),
                   type(Sum);
    AllAvg: AVG -> mode(ProjectionPropertyFirst),
                   type(Avg);
    // Skip to property when projection expression is missing
    AllProperty: [a-zA-Z] -> mode(ProjectionPropertyNext),
                             type(ProjPropFirst);
    // Skip directly to criteria when projection is missing
    AllDirectBy: BY -> mode(Criteria),
                       type(By);
    // Skip directly to order when projection and criteria are missing
    AllProjectionOrderBy: ORDER_BY -> mode(Order),
                                      type(OrderBy);
    // Handle 'All' suffix after <all-action>
    // Transition may be skipped because it adds nothing to the target query
    All: ALL -> mode(AllQuery),
                skip;

// Supply query with empty projection and criteria
mode AllQuery;
    // Skip to order
    AllOrderBy: ORDER_BY -> mode(Order),
                            type(OrderBy);

// Optional "Distinct" in "First" <number> [ "Distinct" ]
mode FirstDistinct;
    FirstDistinct: DISTINCT -> mode(ProjectionPropertyFirst),
                               type(Distinct);
    FirstProp: [a-zA-Z] -> mode(ProjectionPropertyNext),
                           type(ProjPropFirst);
    // Skip to criteria
    FirstBy: BY -> mode(Criteria),
                   type(By);
    // Skip to order
    FirstOrderBy: ORDER_BY -> mode(Order),
                              type(OrderBy);

// Projection property parsing: expecting 1st character of the property
mode ProjectionPropertyFirst;
    ProjPropFirst: [a-zA-Z] -> mode(ProjectionPropertyNext);
    // Skip to criteria
    PropFirstBy: BY -> mode(Criteria),
                       type(By);
    // Skip to order
    PropFirstOrderBy: ORDER_BY -> mode(Order),
                                  type(OrderBy);

// Projection property parsing: expecting next character of the property
//                              or property elements separator
mode ProjectionPropertyNext;
    ProjPropNext: [a-zA-Z0-9];
    ProjPropSep: UNDERSCORE -> mode(ProjectionPropertySeparator);
    // Skip to criteria
    PropNextBy: BY -> mode(Criteria),
                      type(By);
    // Skip to order
    PropNextOrderBy: ORDER_BY -> mode(Order),
                                  type(OrderBy);

// Projection property after elements separator: expecting 1st character of
//                                               next property element
mode ProjectionPropertySeparator;
    ProjPropSepFirst: [a-zA-Z] -> mode(ProjectionPropertyNext),
                                  type(ProjPropFirst);

// Mandatory "By" or "All" after "update" and "delete"
mode ModeBy;
    By: BY {isDml = true;}
           -> mode(Criteria);
    ModeAll: ALL END {isDml = true;}
                 -> mode(DmlEnd),
                 skip;

// Terminal state after "updateAll" or "deleteAll"
mode DmlEnd;
    END: ;

//        criteria         :: <condition> { <logical-operator> <condition> }
//        condition        :: <property> [ [ "Not" ] [ "IgnoreCase" ] <operator>  ]
//        operator         :: "After" | "Before" | Contains" | "EndsWith" | "StartsWith" | "Equal"
//                            | "LessThan" | "LessThanEqual" | "GreaterThan" | "GreaterThanEqual"
//                            | "Between" | "Like" | "In" | "Empty" | "Null" | "True" | "False"
//        logical-operator :: "And" | "Or"

// Property is mandatory in criteria
// Criteria property parsing: expecting 1st character of the property
mode Criteria;
    CritPropFirst: [a-zA-Z] -> mode(CriteriaProperty);

// Criteria property parsing: expecting next character of the property
//                            or property elements separator
mode CriteriaProperty;
    CritPropNext: [a-zA-Z0-9];
    CritPropSep: UNDERSCORE -> mode(Criteria);
    // Those transitions have to handle property identifier too
    PropNot: NOT -> mode(CriteriaNot);
    PropIgnoreCase: IGNORE_CASE -> mode(CriteriaIgnoreCase);
    PropAfter: AFTER -> mode(CriteriaLogicalOperator);
    PropBefore: BEFORE -> mode(CriteriaLogicalOperator);
    PropContains: CONTAINS -> mode(CriteriaLogicalOperator);
    PropEndsWith: ENDS_WITH -> mode(CriteriaLogicalOperator);
    PropStartsWith: STARTS_WITH -> mode(CriteriaLogicalOperator);
    PropEqual: EQUAL -> mode(CriteriaLogicalOperator);
    PropLessThan: LESS_THAN -> mode(CriteriaLogicalOperator);
    PropLessThanEqual: LESS_THAN_EQUAL -> mode(CriteriaLogicalOperator);
    PropGreaterThan: GREATER_THAN -> mode(CriteriaLogicalOperator);
    PropGreaterThanEqual: GREATER_THAN_EQUAL -> mode(CriteriaLogicalOperator);
    PropBetween: BETWEEN -> mode(CriteriaLogicalOperator);
    PropLike: LIKE -> mode(CriteriaLogicalOperator);
    PropIn: IN -> mode(CriteriaLogicalOperator);
    PropEmpty: EMPTY -> mode(CriteriaLogicalOperator);
    PropNull: NULL -> mode(CriteriaLogicalOperator);
    PropTrue: TRUE -> mode(CriteriaLogicalOperator);
    PropFalse: FALSE -> mode(CriteriaLogicalOperator);
    PropAnd: AND -> mode(Criteria);
    PropOr: OR -> mode(Criteria);
    // Skip to order (handle property identifier too)
    PropOrderBy: ORDER_BY {checkDmlOrderBy(_localctx);}
                          -> mode(Order);

// Got "Not" keyword, expectig "IgnoreCase" or criteria operator
mode CriteriaNot;
    IgnoreCase: IGNORE_CASE -> mode(CriteriaNotIgnoreCase);
    After: AFTER -> mode(CriteriaLogicalOperator);
    Before: BEFORE -> mode(CriteriaLogicalOperator);
    Contains: CONTAINS -> mode(CriteriaLogicalOperator);
    EndsWith: ENDS_WITH -> mode(CriteriaLogicalOperator);
    StartsWith: STARTS_WITH -> mode(CriteriaLogicalOperator);
    Equal: EQUAL -> mode(CriteriaLogicalOperator);
    LessThan: LESS_THAN -> mode(CriteriaLogicalOperator);
    LessThanEqual: LESS_THAN_EQUAL -> mode(CriteriaLogicalOperator);
    GreaterThan: GREATER_THAN -> mode(CriteriaLogicalOperator);
    GreaterThanEqual: GREATER_THAN_EQUAL -> mode(CriteriaLogicalOperator);
    Between: BETWEEN -> mode(CriteriaLogicalOperator);
    Like: LIKE -> mode(CriteriaLogicalOperator);
    In: IN -> mode(CriteriaLogicalOperator);
    Empty: EMPTY -> mode(CriteriaLogicalOperator);
    Null: NULL -> mode(CriteriaLogicalOperator);
    True: TRUE -> mode(CriteriaLogicalOperator);
    False: FALSE -> mode(CriteriaLogicalOperator);
    NotAnd: AND -> mode(Criteria),
                   type(And);
    NotOr: OR -> mode(Criteria),
                 type(Or);
    // Skip to order
    CritOrderBy: ORDER_BY {checkDmlOrderBy(_localctx);}
                          -> mode(Order);

// Got "IgnoreCase" keyword, expectig "Not" or criteria operator
mode CriteriaIgnoreCase;
    Not: NOT -> mode(CriteriaNotIgnoreCase);
    IcAfter: AFTER -> mode(CriteriaLogicalOperator),
                      type(After);
    IcBefore: BEFORE -> mode(CriteriaLogicalOperator),
                        type(Before);
    IcContains: CONTAINS -> mode(CriteriaLogicalOperator),
                            type(Contains);
    IcEndsWith: ENDS_WITH -> mode(CriteriaLogicalOperator),
                             type(EndsWith);
    IcStartsWith: STARTS_WITH -> mode(CriteriaLogicalOperator),
                                 type(StartsWith);
    IcEqual: EQUAL -> mode(CriteriaLogicalOperator),
                      type(Equal);
    IcLessThan: LESS_THAN -> mode(CriteriaLogicalOperator),
                             type(LessThan);
    IcLessThanEqual: LESS_THAN_EQUAL -> mode(CriteriaLogicalOperator),
                                        type(LessThanEqual);
    IcGreaterThan: GREATER_THAN -> mode(CriteriaLogicalOperator),
                                   type(GreaterThan);
    IcGreaterThanEqual: GREATER_THAN_EQUAL -> mode(CriteriaLogicalOperator),
                                              type(GreaterThanEqual);
    IcBetween: BETWEEN -> mode(CriteriaLogicalOperator),
                          type(Between);
    IcLike: LIKE -> mode(CriteriaLogicalOperator),
                    type(Like);
    IcIn: IN -> mode(CriteriaLogicalOperator),
                type(In);
    IcEmpty: EMPTY -> mode(CriteriaLogicalOperator),
                      type(Empty);
    IcNull: NULL -> mode(CriteriaLogicalOperator),
                    type(Null);
    IcTrue: TRUE -> mode(CriteriaLogicalOperator),
                    type(True);
    IcFalse: FALSE -> mode(CriteriaLogicalOperator),
                      type(False);
    IcAnd: AND -> mode(Criteria),
                  type(And);
    IcOr: OR -> mode(Criteria),
                type(Or);
    // Skip to order
    IcOrderBy: ORDER_BY {checkDmlOrderBy(_localctx);}
                        -> mode(Order),
                           type(CritOrderBy);

// Got "Not" and "IgnoreCase" keywords, expectig criteria operator
mode CriteriaNotIgnoreCase;
    NotIcAfter: AFTER -> mode(CriteriaLogicalOperator),
                         type(After);
    NotIcBefore: BEFORE -> mode(CriteriaLogicalOperator),
                           type(Before);
    NotIcContains: CONTAINS -> mode(CriteriaLogicalOperator),
                               type(Contains);
    NotIcEndsWith: ENDS_WITH -> mode(CriteriaLogicalOperator),
                                type(EndsWith);
    NotIcStartsWith: STARTS_WITH -> mode(CriteriaLogicalOperator),
                                    type(StartsWith);
    NotIcEqual: EQUAL -> mode(CriteriaLogicalOperator),
                         type(Equal);
    NotIcLessThan: LESS_THAN -> mode(CriteriaLogicalOperator),
                                type(LessThan);
    NotIcLessThanEqual: LESS_THAN_EQUAL -> mode(CriteriaLogicalOperator),
                                           type(LessThanEqual);
    NotIcGreaterThan: GREATER_THAN -> mode(CriteriaLogicalOperator),
                                      type(GreaterThan);
    NotIcGreaterThanEqual: GREATER_THAN_EQUAL -> mode(CriteriaLogicalOperator),
                                                 type(GreaterThanEqual);
    NotIcBetween: BETWEEN -> mode(CriteriaLogicalOperator),
                             type(Between);
    NotIcLike: LIKE -> mode(CriteriaLogicalOperator),
                       type(Like);
    NotIcIn: IN -> mode(CriteriaLogicalOperator),
                   type(In);
    NotIcEmpty: EMPTY -> mode(CriteriaLogicalOperator),
                         type(Empty);
    NotIcNull: NULL -> mode(CriteriaLogicalOperator),
                       type(Null);
    NotIcTrue: TRUE -> mode(CriteriaLogicalOperator),
                       type(True);
    NotIcFalse: FALSE -> mode(CriteriaLogicalOperator),
                         type(False);
    NotIcAnd: AND -> mode(Criteria),
                     type(And);
    NotIcOr: OR -> mode(Criteria),
                   type(Or);
    // Skip to order
    NotIcOrderBy: ORDER_BY {checkDmlOrderBy(_localctx);}
                           -> mode(Order),
                              type(CritOrderBy);

// Got criteria operator and finished condition,
// expecting logical operator ("And", "Or") to join next condition
mode CriteriaLogicalOperator;
    And: AND -> mode(Criteria);
    Or: OR -> mode(Criteria);
    // Skip to order
    LogOpOrderBy: ORDER_BY {checkDmlOrderBy(_localctx);}
                           -> mode(Order),
                              type(CritOrderBy);

//        order     ::= <property> [ <direction> [ <order> ] ]
//        direction ::= "Asc" | "Desc"

mode Order;
    OrdPropFirst: [a-zA-Z] -> mode(OrderProperty);

mode OrderProperty;
    OrdPropNext: [a-zA-Z0-9];
    OrdPropSep: UNDERSCORE -> mode(Order);
    Asc: ASC -> mode(Order);
    Desc: DESC -> mode(Order);

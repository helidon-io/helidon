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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

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

import org.antlr.v4.runtime.ANTLRErrorListener;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;

// Helidon specific method name parser
class MethodNameParserImpl implements MethodNameParser {

    private static final Set<Integer> FINAL_TOKENS = Set.of(
            MethodName.Exists,
            MethodName.Count,
            MethodName.Get,
            MethodName.Find,
            MethodName.List,
            MethodName.Stream,
            MethodName.Delete,
            MethodName.First,
            MethodName.Distinct,
            MethodName.ProjPropFirst, MethodName.ProjPropNext,
            MethodName.CritPropFirst, MethodName.CritPropNext,
            MethodName.Not, MethodName.PropNot,
            MethodName.IgnoreCase, MethodName.PropIgnoreCase,
            MethodName.After, MethodName.PropAfter,
            MethodName.Before, MethodName.PropBefore,
            MethodName.Contains, MethodName.PropContains,
            MethodName.EndsWith, MethodName.PropEndsWith,
            MethodName.StartsWith, MethodName.PropStartsWith,
            MethodName.Equal, MethodName.PropEqual,
            MethodName.LessThan, MethodName.PropLessThan,
            MethodName.LessThanEqual, MethodName.PropLessThanEqual,
            MethodName.GreaterThan, MethodName.PropGreaterThan,
            MethodName.GreaterThanEqual, MethodName.PropGreaterThanEqual,
            MethodName.Between, MethodName.PropBetween,
            MethodName.Like, MethodName.PropLike,
            MethodName.In, MethodName.PropIn,
            MethodName.Empty, MethodName.PropEmpty,
            MethodName.Null, MethodName.PropNull,
            MethodName.True, MethodName.PropTrue,
            MethodName.False, MethodName.PropFalse,
            MethodName.OrdPropFirst, MethodName.OrdPropNext,
            MethodName.Asc, MethodName.Desc
    );

    private final MethodName lexer;
    private final ErrorListener errorListener;
    private String methodName;
    private List<? extends Token> lexerTokens;

    private MethodNameParserImpl() {
        lexer = new MethodName(null);
        lexer.removeErrorListeners();
        errorListener = new ErrorListener();
        lexer.addErrorListener(errorListener);
        methodName = null;
        lexerTokens = null;
    }

    // Factory method needs pkg access only
    static MethodNameParserImpl create() {
        return new MethodNameParserImpl();
    }

    @Override
    public boolean parse(String methodName) {
        Objects.requireNonNull(methodName, "Name of the method is null");
        this.methodName = methodName;
        lexer.reset(CharStreams.fromString(methodName));
        errorListener.reset();
        lexerTokens = lexer.getAllTokens();
        if (lexerTokens.isEmpty()) {
            return false;
        }
        Token lastToken = lexerTokens.getLast();
        return FINAL_TOKENS.contains(lastToken.getType()) && errorListener.isEmpty();
    }

    // FIXME: this can be split into at least two methods, if one of the switch statements exceeds, just add suppress
    // the fall through of switch is intentional
    @SuppressWarnings({"checkstyle:MethodLength", "checkstyle:FallThrough"})
    @Override
    public DataQuery dataQuery() {
        DataQuery.Builder queryBuilder = DataQuery.builder();
        StringBuilder identifier = new StringBuilder(32);
        Property.Builder propertyBuilder = Property.builder();
        Projection.Builder projectionBuilder = Projection.builder();
        CriteriaCondition.Builder criteriaExpressionBuilder = CriteriaCondition.builder();
        Criteria.Builder criteriaBuilder = Criteria.builder();
        OrderExpression.Builder orderExpressionBuilder = OrderExpression.builder();
        Order.Builder orderBuilder = Order.builder();

        for (Token token : lexerTokens) {
            switch (token.getType()) {
            // Projection tokens
            case MethodName.Exists:
                projectionBuilder.action(ProjectionAction.Select)
                        .result(ProjectionResult.Exists)
                        .expression(ProjectionExpression.createExists());
                break;
            case MethodName.Count:
                projectionBuilder.action(ProjectionAction.Select)
                        .result(ProjectionResult.Count)
                        .expression(ProjectionExpression.createCount());
                break;
            case MethodName.Get:
                projectionBuilder.action(ProjectionAction.Select)
                        .result(ProjectionResult.Get);
                break;
            case MethodName.Find:
                projectionBuilder.action(ProjectionAction.Select)
                        .result(ProjectionResult.Find);
                break;
            case MethodName.List:
                projectionBuilder.action(ProjectionAction.Select)
                        .result(ProjectionResult.List);
                break;
            case MethodName.Stream:
                projectionBuilder.action(ProjectionAction.Select)
                        .result(ProjectionResult.Stream);
                break;
            case MethodName.Update:
                throw new UnsupportedOperationException("Update is not implemented yet");
                // FIXME: Implement update
                    /*
                    projectionBuilder.action(Projection.Action.Update)
                            .result(Projection.Result.Dml);
                    break;
                    */
            case MethodName.Delete:
                projectionBuilder.action(ProjectionAction.Delete)
                        .result(ProjectionResult.Dml);
                break;
            case MethodName.First:
                String tokenString = token.getText();
                try {
                    projectionBuilder.expression(
                            ProjectionExpression.createFirst(
                                    Integer.parseInt(tokenString.substring(5))));
                    // This exception shall not happen until grammar definition is broken
                } catch (NumberFormatException ex) {
                    throw new MethodNameParserException("Error parsing First<count> token",
                                                        ex,
                                                        methodName,
                                                        tokenString);
                }
                break;
            case MethodName.Distinct:
                projectionBuilder.distinct(true);
                break;
            case MethodName.Max:
                validateProjectionOperatorReturnType(projectionBuilder.result(), token.getText(), "Max");
                projectionBuilder.expression(ProjectionExpression.createMax());
                break;
            case MethodName.Min:
                validateProjectionOperatorReturnType(projectionBuilder.result(), token.getText(), "Min");
                projectionBuilder.expression(ProjectionExpression.createMin());
                break;
            case MethodName.Sum:
                validateProjectionOperatorReturnType(projectionBuilder.result(), token.getText(), "Sum");
                projectionBuilder.expression(ProjectionExpression.createSum());
                break;
            case MethodName.Avg:
                validateProjectionOperatorReturnType(projectionBuilder.result(), token.getText(), "Avg");
                projectionBuilder.expression(ProjectionExpression.createAvg());
                break;
            case MethodName.ProjPropFirst:
                identifier.setLength(0);
                identifier.append(token.getText().toLowerCase());
                break;
            case MethodName.ProjPropNext:
                identifier.append(token.getText());
                break;
            case MethodName.ProjPropSep:
                propertyBuilder.addNamePart(identifier.toString());
                break;
            // Criteria/Order tokens identifier: finish projection parsing
            // Last token handlers must finish projection parsing when By or OrderBy is not present
            //case MethodName.PropBy:
            case MethodName.By:
            case MethodName.OrderBy: // Transition directly from projection parsing
                if (!identifier.isEmpty()) {
                    propertyBuilder.addNamePart(identifier.toString());
                }
                if (!propertyBuilder.nameParts().isEmpty()) {
                    projectionBuilder.property(propertyBuilder.build());
                }
                queryBuilder.projection(projectionBuilder.build());
                propertyBuilder = Property.builder();
                break;
            // Criteria tokens
            case MethodName.CritPropFirst: // Intentional duplicate
                identifier.setLength(0);
                identifier.append(token.getText().toLowerCase());
                break;
            case MethodName.CritPropNext: // Intentional duplicate
                identifier.append(token.getText());
                break;
            case MethodName.CritPropSep: // Intentional duplicate
                propertyBuilder.addNamePart(identifier.toString());
                break;
            case MethodName.PropNot:
                propertyBuilder.addNamePart(identifier.toString());
                // Intentional fall-trough to the next case (need to set Not value too)
            case MethodName.Not:
                criteriaExpressionBuilder.not(true);
                break;
            case MethodName.PropIgnoreCase:
                propertyBuilder.addNamePart(identifier.toString());
                // Intentional fall-trough to the next case (need to set IgnoreCase value too)
            case MethodName.IgnoreCase:
                criteriaExpressionBuilder.ignoreCase(true);
                break;
            case MethodName.PropAfter:
                propertyBuilder.addNamePart(identifier.toString());
                // Intentional fall-trough to the next case (need to set After operator too)
            case MethodName.After:
                criteriaExpressionBuilder.operator(CriteriaOperator.After);
                break;
            case MethodName.PropBefore:
                propertyBuilder.addNamePart(identifier.toString());
                // Intentional fall-trough to the next case (need to set Before operator too)
            case MethodName.Before:
                criteriaExpressionBuilder.operator(CriteriaOperator.Before);
                break;
            case MethodName.PropContains:
                propertyBuilder.addNamePart(identifier.toString());
                // Intentional fall-trough to the next case (need to set Contains operator too)
            case MethodName.Contains:
                criteriaExpressionBuilder.operator(CriteriaOperator.Contains);
                break;
            case MethodName.PropEndsWith:
                propertyBuilder.addNamePart(identifier.toString());
                // Intentional fall-trough to the next case (need to set EndsWith operator too)
            case MethodName.EndsWith:
                criteriaExpressionBuilder.operator(CriteriaOperator.EndsWith);
                break;
            case MethodName.PropStartsWith:
                propertyBuilder.addNamePart(identifier.toString());
                // Intentional fall-trough to the next case (need to set StartsWith operator too)
            case MethodName.StartsWith:
                criteriaExpressionBuilder.operator(CriteriaOperator.StartsWith);
                break;
            case MethodName.PropEqual:
                propertyBuilder.addNamePart(identifier.toString());
                // Intentional fall-trough to the next case (need to set Equal operator too)
            case MethodName.Equal:
                criteriaExpressionBuilder.operator(CriteriaOperator.Equal);
                break;
            case MethodName.PropLessThan:
                propertyBuilder.addNamePart(identifier.toString());
                // Intentional fall-trough to the next case (need to set LessThan operator too)
            case MethodName.LessThan:
                criteriaExpressionBuilder.operator(CriteriaOperator.LessThan);
                break;
            case MethodName.PropLessThanEqual:
                propertyBuilder.addNamePart(identifier.toString());
                // Intentional fall-trough to the next case (need to set LessThanEqual operator too)
            case MethodName.LessThanEqual:
                criteriaExpressionBuilder.operator(CriteriaOperator.LessThanEqual);
                break;
            case MethodName.PropGreaterThan:
                propertyBuilder.addNamePart(identifier.toString());
                // Intentional fall-trough to the next case (need to set GreaterThan operator too)
            case MethodName.GreaterThan:
                criteriaExpressionBuilder.operator(CriteriaOperator.GreaterThan);
                break;
            case MethodName.PropGreaterThanEqual:
                propertyBuilder.addNamePart(identifier.toString());
                // Intentional fall-trough to the next case (need to set GreaterThanEqual operator too)
            case MethodName.GreaterThanEqual:
                criteriaExpressionBuilder.operator(CriteriaOperator.GreaterThanEqual);
                break;
            case MethodName.PropBetween:
                propertyBuilder.addNamePart(identifier.toString());
                // Intentional fall-trough to the next case (need to set Between operator too)
            case MethodName.Between:
                criteriaExpressionBuilder.operator(CriteriaOperator.Between);
                break;
            case MethodName.PropLike:
                propertyBuilder.addNamePart(identifier.toString());
                // Intentional fall-trough to the next case (need to set Like operator too)
            case MethodName.Like:
                criteriaExpressionBuilder.operator(CriteriaOperator.Like);
                break;
            case MethodName.PropIn:
                propertyBuilder.addNamePart(identifier.toString());
                // Intentional fall-trough to the next case (need to set In operator too)
            case MethodName.In:
                criteriaExpressionBuilder.operator(CriteriaOperator.In);
                break;
            case MethodName.PropEmpty:
                propertyBuilder.addNamePart(identifier.toString());
                // Intentional fall-trough to the next case (need to set Empty operator too)
            case MethodName.Empty:
                criteriaExpressionBuilder.operator(CriteriaOperator.Empty);
                break;
            case MethodName.PropNull:
                propertyBuilder.addNamePart(identifier.toString());
                // Intentional fall-trough to the next case (need to set Null operator too)
            case MethodName.Null:
                criteriaExpressionBuilder.operator(CriteriaOperator.Null);
                break;
            case MethodName.PropTrue:
                propertyBuilder.addNamePart(identifier.toString());
                // Intentional fall-trough to the next case (need to set True operator too)
            case MethodName.True:
                criteriaExpressionBuilder.operator(CriteriaOperator.True);
                break;
            case MethodName.PropFalse:
                propertyBuilder.addNamePart(identifier.toString());
                // Intentional fall-trough to the next case (need to set False operator too)
            case MethodName.False:
                criteriaExpressionBuilder.operator(CriteriaOperator.False);
                break;
            case MethodName.PropAnd:
                propertyBuilder.addNamePart(identifier.toString());
                // Intentional fall-trough to the next case (need to finish condition too)
            case MethodName.And:
                criteriaExpressionBuilder.property(propertyBuilder.build());
                criteriaBuilder.condition(criteriaExpressionBuilder.build());
                criteriaBuilder.and();
                propertyBuilder = Property.builder();
                criteriaExpressionBuilder = CriteriaCondition.builder();
                break;
            case MethodName.PropOr:
                propertyBuilder.addNamePart(identifier.toString());
                // Intentional fall-trough to the next case (need to finish condition too)
            case MethodName.Or:
                criteriaExpressionBuilder.property(propertyBuilder.build());
                criteriaBuilder.condition(criteriaExpressionBuilder.build());
                criteriaBuilder.or();
                propertyBuilder = Property.builder();
                criteriaExpressionBuilder = CriteriaCondition.builder();
                break;
            // Order tokens identifier: finish criteria parsing
            // Last token handlers must finish criteria parsing when OrderBy is not present
            case MethodName.PropOrderBy:
                propertyBuilder.addNamePart(identifier.toString());
                // Intentional fall-trough to the next case (need to finish criteria too)
            case MethodName.CritOrderBy:
                if (!propertyBuilder.nameParts().isEmpty()) {
                    criteriaExpressionBuilder.property(propertyBuilder.build());
                }
                criteriaBuilder.condition(criteriaExpressionBuilder.build());
                queryBuilder.criteria(criteriaBuilder.build());
                propertyBuilder = Property.builder();
                break;
            // Order tokens
            case MethodName.OrdPropFirst: // Intentional duplicate
                identifier.setLength(0);
                identifier.append(token.getText().toLowerCase());
                break;
            case MethodName.OrdPropNext: // Intentional duplicate
                identifier.append(token.getText());
                break;
            case MethodName.OrdPropSep: // Intentional duplicate
                propertyBuilder.addNamePart(identifier.toString());
                break;
            case MethodName.Asc:
                propertyBuilder.addNamePart(identifier.toString());
                orderBuilder.addExpression(
                        orderExpressionBuilder.property(propertyBuilder.build())
                                .operator(OrderOperator.ASC)
                                .build());
                propertyBuilder = Property.builder();
                orderExpressionBuilder = OrderExpression.builder();
                break;
            case MethodName.Desc:
                propertyBuilder.addNamePart(identifier.toString());
                orderBuilder.addExpression(
                        orderExpressionBuilder.property(propertyBuilder.build())
                                .operator(OrderOperator.DESC)
                                .build());
                propertyBuilder = Property.builder();
                orderExpressionBuilder = OrderExpression.builder();
                break;
            // Default handler means some token is missing in this code.
            default:
                String literalName = MethodName.VOCABULARY.getLiteralName(token.getType());
                if (literalName != null) {
                    throw new ParserException(String.format("Unknown parser token %s", literalName), methodName);
                }
                throw new ParserException(String.format("Unknown parser token type %d", token.getType()), methodName);
            }
        }
        // Finish DataQuery depending on last token
        switch (lexerTokens.getLast().getType()) {
        // Valid projection parsing last tokens
        case MethodName.Exists:
        case MethodName.Count:
        case MethodName.Get:
        case MethodName.Find:
        case MethodName.List:
        case MethodName.Stream:
        case MethodName.Delete:
        case MethodName.First:
        case MethodName.Distinct:
            queryBuilder.projection(projectionBuilder.build());
            break;
        case MethodName.ProjPropFirst:
        case MethodName.ProjPropNext:
            propertyBuilder.addNamePart(identifier.toString());
            queryBuilder.projection(projectionBuilder.property(propertyBuilder.build())
                                            .build());
            break;
        // Valid criteria parsing last tokens
        case MethodName.CritPropFirst:
        case MethodName.CritPropNext:
            propertyBuilder.addNamePart(identifier.toString());
            // Intentional fall-trough to the next case (property needs to finish criteria builders too)
        case MethodName.Not:
        case MethodName.PropNot:
        case MethodName.IgnoreCase:
        case MethodName.PropIgnoreCase:
        case MethodName.After:
        case MethodName.PropAfter:
        case MethodName.Before:
        case MethodName.PropBefore:
        case MethodName.Contains:
        case MethodName.PropContains:
        case MethodName.EndsWith:
        case MethodName.PropEndsWith:
        case MethodName.StartsWith:
        case MethodName.PropStartsWith:
        case MethodName.Equal:
        case MethodName.PropEqual:
        case MethodName.LessThan:
        case MethodName.PropLessThan:
        case MethodName.LessThanEqual:
        case MethodName.PropLessThanEqual:
        case MethodName.GreaterThan:
        case MethodName.PropGreaterThan:
        case MethodName.GreaterThanEqual:
        case MethodName.PropGreaterThanEqual:
        case MethodName.Between:
        case MethodName.PropBetween:
        case MethodName.Like:
        case MethodName.PropLike:
        case MethodName.In:
        case MethodName.PropIn:
        case MethodName.Empty:
        case MethodName.PropEmpty:
        case MethodName.Null:
        case MethodName.PropNull:
        case MethodName.True:
        case MethodName.PropTrue:
        case MethodName.False:
        case MethodName.PropFalse:
            criteriaExpressionBuilder.property(propertyBuilder.build());
            criteriaBuilder.condition(criteriaExpressionBuilder.build());
            queryBuilder.criteria(criteriaBuilder.build());
            break;
        // Valid order parsing last tokens
        case MethodName.OrdPropFirst:
        case MethodName.OrdPropNext:
            propertyBuilder.addNamePart(identifier.toString());
            orderBuilder.addExpression(
                    orderExpressionBuilder.property(propertyBuilder.build())
                            .build());
            // Intentional fall-trough to the next case (property needs to finish order builders too)
        case MethodName.Asc:
        case MethodName.Desc:
            queryBuilder.order(orderBuilder.build());
        default:
            // Do nothing
        }
        return queryBuilder.build();
    }

    @Override
    public List<MethodParserError> errors() {
        return List.of();
    }

    // Only get resultType is allowed for Min/Max/sum/Avg
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private void validateProjectionOperatorReturnType(Optional<ProjectionResult> resultType,
                                                      String token,
                                                      String operator) {
        resultType.ifPresentOrElse(
                result -> {
                    switch (result) {
                    case Exists,
                         Count,
                         Find,
                         List,
                         Stream -> throw new MethodNameParserException("Invalid return type " + result
                                                                               + " for projection operator " + operator,
                                                                       methodName,
                                                                       token);
                    default -> {
                        // Do nothing
                    }
                    }
                },
                // This should not happen unless the parser code is broken
                () -> {
                    throw new MethodNameParserException("Error parsing Max token: missing return type limitation",
                                                        methodName,
                                                        token);
                });
    }

    private static final class ErrorListener extends BaseErrorListener implements ANTLRErrorListener {

        private final List<MethodParserError> errors;

        private ErrorListener() {
            errors = new ArrayList<>();
        }

        @Override
        public void syntaxError(Recognizer<?, ?> recognizer,
                                Object offendingSymbol,
                                int line,
                                int charPositionInLine,
                                String msg,
                                RecognitionException e) {
            errors.add(new MethodParserError(offendingSymbol, line, charPositionInLine));
        }

        private void reset() {
            errors.clear();
        }

        private List<MethodParserError> errors() {
            return errors;
        }

        private boolean isEmpty() {
            return errors.isEmpty();
        }

    }

}

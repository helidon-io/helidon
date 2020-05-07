/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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

package io.helidon.microprofile.graphql.server;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.NumberFormat;
import java.text.ParseException;

import static graphql.Scalars.GraphQLBigInteger;
import static graphql.Scalars.GraphQLFloat;
import static graphql.Scalars.GraphQLInt;

import graphql.Scalars;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.GraphQLScalarType;

/**
 * Custom scalars.
 */
public class CustomScalars {

    /**
     * Private no-args constructor.
     */
    private CustomScalars() {
    }

    public static final GraphQLScalarType CUSTOM_BIGDECIMAL_SCALAR = newCustomBigDecimalScalar();
    public static final GraphQLScalarType CUSTOM_INT_SCALAR = newCustomGraphQLInt();
    public static final GraphQLScalarType CUSTOM_FLOAT_SCALAR = newCustomGraphQLFloat();
    public static final GraphQLScalarType CUSTOM_BIGINTEGER_SCALAR = newCustomGraphQLBigInteger();

    /**
     * Creates a custom BigDecimalScalar which will parse a formatted value which was originally formatted using a {@link
     * NumberFormat}.
     *
     * @return a custom BigDecimalScalar
     */
    @SuppressWarnings("unchecked")
    private static GraphQLScalarType newCustomBigDecimalScalar() {
        GraphQLScalarType originalScalar = Scalars.GraphQLBigDecimal;
        Coercing<BigDecimal, BigDecimal> originalCoercing = originalScalar.getCoercing();

        Coercing<BigDecimal, Object> formattingCoercing = new Coercing<>() {
            @Override
            public Object serialize(Object dataFetcherResult) throws CoercingSerializeException {
                Object finalDataFetcherResult = dataFetcherResult;
                if (dataFetcherResult instanceof FormattedNumber) {
                    return ((FormattedNumber) finalDataFetcherResult).getFormattedValue();
                }
                return originalCoercing.serialize(finalDataFetcherResult);
            }

            @Override
            public BigDecimal parseValue(Object input) throws CoercingParseValueException {
                return originalCoercing.parseValue(input);
            }

            @Override
            public BigDecimal parseLiteral(Object input) throws CoercingParseLiteralException {
                return originalCoercing.parseLiteral(input);
            }
        };

        return GraphQLScalarType.newScalar()
                .description("Custom: " + originalScalar.getDescription())
                .definition(originalScalar.getDefinition())
                .name(originalScalar.getName())
                .coercing(formattingCoercing)
                .build();
    }

    private static GraphQLScalarType newCustomGraphQLInt() {
        GraphQLScalarType intScalar = GraphQLInt;
        Coercing<Integer, Integer> originalCoercing = intScalar.getCoercing();
        Coercing<Integer, Object> formattingCoercing = new Coercing<>() {
            @Override
            public Object serialize(Object dataFetcherResult) throws CoercingSerializeException {
                Object finalDataFetcherResult = dataFetcherResult;
                if (dataFetcherResult instanceof FormattedNumber) {
                    return ((FormattedNumber) finalDataFetcherResult).getFormattedValue();
                }
                return originalCoercing.serialize(finalDataFetcherResult);
            }

            @Override
            public Integer parseValue(Object input) throws CoercingParseValueException {
                return originalCoercing.parseValue(input);
            }

            @Override
            public Integer parseLiteral(Object input) throws CoercingParseLiteralException {
                return originalCoercing.parseLiteral(input);
            }
        };

        return GraphQLScalarType.newScalar()
                .description("Custom: " + intScalar.getDescription())
                .definition(intScalar.getDefinition())
                .name(intScalar.getName())
                .coercing(formattingCoercing)
                .build();
    }

    private static GraphQLScalarType newCustomGraphQLFloat() {
        GraphQLScalarType floatScalar = GraphQLFloat;
        Coercing<Double, Double> originalCoercing = floatScalar.getCoercing();
        Coercing<Double, Object> formattingCoercing = new Coercing<>() {
            @Override
            public Object serialize(Object dataFetcherResult) throws CoercingSerializeException {
                Object finalDataFetcherResult = dataFetcherResult;
                if (dataFetcherResult instanceof FormattedNumber) {
                    return ((FormattedNumber) finalDataFetcherResult).getFormattedValue();
                }
                return originalCoercing.serialize(finalDataFetcherResult);
            }

            @Override
            public Double parseValue(Object input) throws CoercingParseValueException {
                return originalCoercing.parseValue(input);
            }

            @Override
            public Double parseLiteral(Object input) throws CoercingParseLiteralException {
                return originalCoercing.parseLiteral(input);
            }
        };

        return GraphQLScalarType.newScalar()
                .description("Custom: " + floatScalar.getDescription())
                .definition(floatScalar.getDefinition())
                .name(floatScalar.getName())
                .coercing(formattingCoercing)
                .build();
    }

    private static GraphQLScalarType newCustomGraphQLBigInteger() {
        GraphQLScalarType bigIntegerScalar = GraphQLBigInteger;
        Coercing<BigInteger, BigInteger> originalCoercing = bigIntegerScalar.getCoercing();
        Coercing<BigInteger, Object> formattingCoercing = new Coercing<>() {
            @Override
            public Object serialize(Object dataFetcherResult) throws CoercingSerializeException {
                Object finalDataFetcherResult = dataFetcherResult;
                if (dataFetcherResult instanceof FormattedNumber) {
                    return ((FormattedNumber) finalDataFetcherResult).getFormattedValue();
                }
                return originalCoercing.serialize(finalDataFetcherResult);
            }

            @Override
            public BigInteger parseValue(Object input) throws CoercingParseValueException {
                return originalCoercing.parseValue(input);
            }

            @Override
            public BigInteger parseLiteral(Object input) throws CoercingParseLiteralException {
                return originalCoercing.parseLiteral(input);
            }
        };

        return GraphQLScalarType.newScalar()
                .description("Custom: " + bigIntegerScalar.getDescription())
                .definition(bigIntegerScalar.getDefinition())
                .name(bigIntegerScalar.getName())
                .coercing(formattingCoercing)
                .build();
    }

    private static Object parserNumberFormat(Object dataFetcherResult) {
        FormattedNumber formattedNumber = (FormattedNumber) dataFetcherResult;
        NumberFormat format = formattedNumber.getFormat();
        try {
            return format.parseObject(formattedNumber.getFormattedValue());
        } catch (ParseException e) {
            throw new CoercingParseValueException(
                    "Unable to serialize value '" + dataFetcherResult + "' using format '"
                            + format + "'.");
        }
    }

}

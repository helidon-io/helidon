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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZonedDateTime;

import graphql.Scalars;
import graphql.language.StringValue;
import graphql.scalars.ExtendedScalars;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.GraphQLScalarType;

import static graphql.Scalars.GraphQLBigInteger;
import static graphql.Scalars.GraphQLFloat;
import static graphql.Scalars.GraphQLInt;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.DATETIME_SCALAR;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.DATE_SCALAR;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.FORMATTED_DATETIME_SCALAR;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.FORMATTED_DATE_SCALAR;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.FORMATTED_OFFSET_DATETIME_SCALAR;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.FORMATTED_TIME_SCALAR;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.FORMATTED_ZONED_DATETIME_SCALAR;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.TIME_SCALAR;

/**
 * Custom scalars.
 */
public class CustomScalars {

    /**
     * Private no-args constructor.
     */
    private CustomScalars() {
    }

    /**
     * An instance of a custome BigDecimal Scalar.
     */
    public static final GraphQLScalarType CUSTOM_BIGDECIMAL_SCALAR = newCustomBigDecimalScalar();

    /**
     * An instance of a custom Int scalar.
     */
    public static final GraphQLScalarType CUSTOM_INT_SCALAR = newCustomGraphQLInt();

    /**
     * An instance of a custom Float scalar.
     */
    public static final GraphQLScalarType CUSTOM_FLOAT_SCALAR = newCustomGraphQLFloat();

    /**
     * An instance of a custom BigInteger scalar.
     */
    public static final GraphQLScalarType CUSTOM_BIGINTEGER_SCALAR = newCustomGraphQLBigInteger();

    /**
     * An instance of a custom formatted date/time scalar.
     */
    public static final GraphQLScalarType FORMATTED_CUSTOM_DATE_TIME_SCALAR = newDateTimeScalar(FORMATTED_DATETIME_SCALAR);

    /**
     * An instance of a custom formatted time scalar.
     */
    public static final GraphQLScalarType FORMATTED_CUSTOM_TIME_SCALAR = newTimeScalar(FORMATTED_TIME_SCALAR);

    /**
     * An instance of a custom formatted date scalar.
     */
    public static final GraphQLScalarType FORMATTED_CUSTOM_DATE_SCALAR = newDateScalar(FORMATTED_DATE_SCALAR);

    /**
     * An instance of a custom date/time scalar (with default formatting).
     */
    public static final GraphQLScalarType CUSTOM_DATE_TIME_SCALAR = newDateTimeScalar(DATETIME_SCALAR);

    /**
     * An instance of a custom offset date/time scalar (with default formatting).
     */
    public static final GraphQLScalarType CUSTOM_OFFSET_DATE_TIME_SCALAR = newOffsetDateTimeScalar(FORMATTED_OFFSET_DATETIME_SCALAR);

    /**
     * An instance of a custom offset date/time scalar (with default formatting).
     */
    public static final GraphQLScalarType CUSTOM_ZONED_DATE_TIME_SCALAR = newZonedDateTimeScalar(FORMATTED_ZONED_DATETIME_SCALAR);

    /**
     * An instance of a custom time scalar (with default formatting).
     */
    public static final GraphQLScalarType CUSTOM_TIME_SCALAR = newTimeScalar(TIME_SCALAR);

    /**
     * An instance of a custom date scalar (with default formatting).
     */
    public static final GraphQLScalarType CUSTOM_DATE_SCALAR = newDateScalar(DATE_SCALAR);

    /**
     * Return a new custom date/time scalar.
     *
     * @param name the name of the scalar
     *
     * @return a new custom date/time scalar
     */
    @SuppressWarnings("unchecked")
    public static GraphQLScalarType newDateTimeScalar(String name) {
        GraphQLScalarType originalScalar = ExtendedScalars.DateTime;
        Coercing<LocalDateTime, String> originalCoercing = originalScalar.getCoercing();
        return GraphQLScalarType.newScalar().coercing(new Coercing<Object, String>() {
            public String serialize(Object dataFetcherResult) throws CoercingSerializeException {
                if (dataFetcherResult instanceof String) {
                    return (String) dataFetcherResult;
                } else {
                    return originalCoercing.serialize(dataFetcherResult);
                }
            }

            @Override
            public Object parseValue(Object input) throws CoercingParseValueException {
                return input instanceof StringValue ? ((StringValue) input).getValue()
                        : originalCoercing.parseValue(input);
            }

            @Override
            public Object parseLiteral(Object input) throws CoercingParseLiteralException {
                validateLiteral(input);
                try {
                    return ((StringValue) input).getValue();
//                    return LocalDateTime.parse(((StringValue) input).getValue());
                } catch (Exception e) {
                    throw new CoercingParseLiteralException(e);
                }
            }
        })
        .name(name)
        .description("Custom: " + originalScalar.getDescription())
        .build();
    }

    /**
     * Return a new custom offset date/time scalar.
     *
     * @param name the name of the scalar
     *
     * @return a new custom date/time scalar
     */
    @SuppressWarnings("unchecked")
    public static GraphQLScalarType newOffsetDateTimeScalar(String name) {
        GraphQLScalarType originalScalar = ExtendedScalars.DateTime;
        Coercing<OffsetDateTime, String> originalCoercing = originalScalar.getCoercing();
        return GraphQLScalarType.newScalar().coercing(new Coercing<Object, String>() {
            public String serialize(Object dataFetcherResult) throws CoercingSerializeException {
                if (dataFetcherResult instanceof String) {
                    return (String) dataFetcherResult;
                } else {
                    return originalCoercing.serialize(dataFetcherResult);
                }
            }

            @Override
            public Object parseValue(Object input) throws CoercingParseValueException {
                return input instanceof StringValue ? ((StringValue) input).getValue()
                        : originalCoercing.parseValue(input);
            }

            @Override
            public Object parseLiteral(Object input) throws CoercingParseLiteralException {
                if (!(input instanceof StringValue)) {
                    throw new CoercingParseLiteralException("Expected AST type 'StringValue' but was '"
                              + (input == null ? "null" : input.getClass().getSimpleName()) + "'.");
                }
                try {
                    return ((StringValue) input).getValue();
                } catch (Exception e) {
                    throw new CoercingParseLiteralException(e);
                }
            }
        })
        .name(name)
        .description("Custom: " + originalScalar.getDescription())
        .build();
    }
    /**
     * Return a new custom zoned date/time scalar.
     *
     * @param name the name of the scalar
     *
     * @return a new custom date/time scalar
     */
    @SuppressWarnings("unchecked")
    public static GraphQLScalarType newZonedDateTimeScalar(String name) {
        GraphQLScalarType originalScalar = ExtendedScalars.DateTime;
        Coercing<ZonedDateTime, String> originalCoercing = originalScalar.getCoercing();
        return GraphQLScalarType.newScalar().coercing(new Coercing<Object, String>() {
            public String serialize(Object dataFetcherResult) throws CoercingSerializeException {
                if (dataFetcherResult instanceof String) {
                    return (String) dataFetcherResult;
                } else {
                    return originalCoercing.serialize(dataFetcherResult);
                }
            }

            @Override
            public Object parseValue(Object input) throws CoercingParseValueException {
                return input instanceof StringValue ? ((StringValue) input).getValue()
                        : originalCoercing.parseValue(input);
            }

            @Override
            public Object parseLiteral(Object input) throws CoercingParseLiteralException {
                if (!(input instanceof StringValue)) {
                    throw new CoercingParseLiteralException("Expected AST type 'StringValue' but was '"
                              + (input == null ? "null" : input.getClass().getSimpleName()) + "'.");
                }
                try {
                    return (((StringValue) input).getValue());
//                    return ZonedDateTime.parse(((StringValue) input).getValue());
                } catch (Exception e) {
                    throw new CoercingParseLiteralException(e);
                }
            }
        })
        .name(name)
        .description("Custom: " + originalScalar.getDescription())
        .build();
    }

    /**
     * Return a new custom time scalar.
     *
     * @param name the name of the scalar
     * @return a new custom time scalar
     */
    @SuppressWarnings("unchecked")
    public static GraphQLScalarType newTimeScalar(String name) {
        GraphQLScalarType originalScalar = ExtendedScalars.Time;
        Coercing<OffsetTime, String> originalCoercing = originalScalar.getCoercing();
        return GraphQLScalarType.newScalar().coercing(new Coercing<Object, String>() {
            public String serialize(Object dataFetcherResult) throws CoercingSerializeException {
                return dataFetcherResult instanceof String
                        ? (String) dataFetcherResult
                        : originalCoercing.serialize(dataFetcherResult);
            }

            @Override
            public Object parseValue(Object input) throws CoercingParseValueException {
                return input instanceof StringValue ? ((StringValue) input).getValue()
                        : originalCoercing.parseValue(input);
            }


            @Override
            public Object parseLiteral(Object input) throws CoercingParseLiteralException {
                validateLiteral(input);
                try {
                    return LocalTime.parse(((StringValue) input).getValue());
                } catch (Exception e) {
                    throw new CoercingParseLiteralException(e);
                }
            }
        })
        .name(name)
        .description("Custom: " + originalScalar.getDescription())
        .build();
    }

    /**
     * Return a new custom date scalar.
     *
     * @param name the name of the scalar
     *
     * @return a new custom date scalar
     */
    @SuppressWarnings("unchecked")
    public static GraphQLScalarType newDateScalar(String name) {
        GraphQLScalarType originalScalar = ExtendedScalars.Date;
        Coercing<LocalDate, String> originalCoercing = originalScalar.getCoercing();
        return GraphQLScalarType.newScalar().coercing(new Coercing<Object, String>() {
            public String serialize(Object dataFetcherResult) throws CoercingSerializeException {
                return dataFetcherResult instanceof String
                        ? (String) dataFetcherResult
                        : originalCoercing.serialize(dataFetcherResult);
            }

            @Override
            public Object parseValue(Object input) throws CoercingParseValueException {
                return input instanceof StringValue ? ((StringValue) input).getValue()
                        : originalCoercing.parseValue(input);
            }

            @Override
            public Object parseLiteral(Object input) throws CoercingParseLiteralException {
                   return LocalDate.parse(((StringValue) input).getValue());
            }
        })
        .name(name)
        .description("Custom: " + originalScalar.getDescription())
        .build();
    }

    /**
     * Return a new custom BigDecimal scalar.
     *
     * @return a new custom BigDecimal scalar
     */
    @SuppressWarnings("unchecked")
    private static GraphQLScalarType newCustomBigDecimalScalar() {
        GraphQLScalarType originalScalar = Scalars.GraphQLBigDecimal;
        Coercing<BigDecimal, BigDecimal> originalCoercing = originalScalar.getCoercing();

        return GraphQLScalarType.newScalar().coercing(new Coercing<>() {
            @Override
            public Object serialize(Object dataFetcherResult) throws CoercingSerializeException {
                return dataFetcherResult instanceof String
                        ? (String) dataFetcherResult
                        : originalCoercing.serialize(dataFetcherResult);
            }

            @Override
            public BigDecimal parseValue(Object input) throws CoercingParseValueException {
                return originalCoercing.parseValue(input);
            }

            @Override
            public BigDecimal parseLiteral(Object input) throws CoercingParseLiteralException {
                return originalCoercing.parseLiteral(input);
            }
        })
        .name(originalScalar.getName())
        .description("Custom: " + originalScalar.getDescription())
        .build();
    }

    /**
     * Return a new custom Int scalar.
     *
     * @return a new custom Int scalar
     */
    @SuppressWarnings("unchecked")
    private static GraphQLScalarType newCustomGraphQLInt() {
        GraphQLScalarType originalScalar = GraphQLInt;
        Coercing<Integer, Integer> originalCoercing = originalScalar.getCoercing();
        return GraphQLScalarType.newScalar().coercing(new Coercing<>() {
            @Override
            public Object serialize(Object dataFetcherResult) throws CoercingSerializeException {
                return dataFetcherResult instanceof String
                        ? (String) dataFetcherResult
                        : originalCoercing.serialize(dataFetcherResult);
            }

            @Override
            public Integer parseValue(Object input) throws CoercingParseValueException {
                return originalCoercing.parseValue(input);
            }

            @Override
            public Integer parseLiteral(Object input) throws CoercingParseLiteralException {
                return originalCoercing.parseLiteral(input);
            }
        })
        .name(originalScalar.getName())
        .description("Custom: " + originalScalar.getDescription())
        .build();
    }

    /**
     * Return a new custom Float scalar.
     *
     * @return a new custom Float scalar
     */
    @SuppressWarnings("unchecked")
    private static GraphQLScalarType newCustomGraphQLFloat() {
        GraphQLScalarType originalScalar = GraphQLFloat;
        Coercing<Double, Double> originalCoercing = originalScalar.getCoercing();
        return GraphQLScalarType.newScalar().coercing(new Coercing<>() {
            @Override
            public Object serialize(Object dataFetcherResult) throws CoercingSerializeException {
                return dataFetcherResult instanceof String
                        ? (String) dataFetcherResult
                        : originalCoercing.serialize(dataFetcherResult);
            }

            @Override
            public Double parseValue(Object input) throws CoercingParseValueException {
                return originalCoercing.parseValue(input);
            }

            @Override
            public Double parseLiteral(Object input) throws CoercingParseLiteralException {
                return originalCoercing.parseLiteral(input);
            }
        })
        .name(originalScalar.getName())
        .description("Custom: " + originalScalar.getDescription())
        .build();
    }

    /**
     * Return a new custom BigInteger scalar.
     *
     * @return a new custom BigInteger scalar
     */
    @SuppressWarnings("unchecked")
    private static GraphQLScalarType newCustomGraphQLBigInteger() {
        GraphQLScalarType originalScalar = GraphQLBigInteger;
        Coercing<BigInteger, BigInteger> originalCoercing = originalScalar.getCoercing();
        return GraphQLScalarType.newScalar().coercing(new Coercing<>() {
            @Override
            public Object serialize(Object dataFetcherResult) throws CoercingSerializeException {
                return dataFetcherResult instanceof String
                        ? (String) dataFetcherResult
                        : originalCoercing.serialize(dataFetcherResult);
            }

            @Override
            public BigInteger parseValue(Object input) throws CoercingParseValueException {
                return originalCoercing.parseValue(input);
            }

            @Override
            public BigInteger parseLiteral(Object input) throws CoercingParseLiteralException {
                return originalCoercing.parseLiteral(input);
            }
        })
        .name(originalScalar.getName())
        .description("Custom: " + originalScalar.getDescription())
        .build();
    }

    /**
     * Validate that an input is an instance of {@link StringValue}.
     *
     * @param input input to validate
     * @throws CoercingParseLiteralException if it is not a {@link StringValue}
     */
    private static void validateLiteral(Object input) throws CoercingParseLiteralException {
        if (!(input instanceof StringValue)) {
            throw new CoercingParseLiteralException("Expected AST type 'StringValue' but was '"
                                                            + (input == null ? "null" : input.getClass().getSimpleName()) + "'.");
        }
    }
}

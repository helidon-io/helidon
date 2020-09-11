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
import java.time.OffsetDateTime;
import java.time.OffsetTime;

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
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.FORMATTED_TIME_SCALAR;
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
        Coercing<OffsetDateTime, String> originalCoercing = originalScalar.getCoercing();
        return GraphQLScalarType.newScalar().coercing(new Coercing<OffsetDateTime, String>() {
            public String serialize(Object dataFetcherResult) throws CoercingSerializeException {
                if (dataFetcherResult instanceof String) {
                    return (String) dataFetcherResult;
                } else {
                    return originalCoercing.serialize(dataFetcherResult);
                }
            }

            @Override
            public OffsetDateTime parseValue(Object input) throws CoercingParseValueException {
                return null;
            }

            @Override
            public OffsetDateTime parseLiteral(Object input) throws CoercingParseLiteralException {
                return null;
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
        Coercing<OffsetDateTime, String> originalCoercing = originalScalar.getCoercing();
        return GraphQLScalarType.newScalar().coercing(new Coercing<OffsetTime, String>() {
            public String serialize(Object dataFetcherResult) throws CoercingSerializeException {
                return dataFetcherResult instanceof String
                        ? (String) dataFetcherResult
                        : originalCoercing.serialize(dataFetcherResult);
            }

            @Override
            public OffsetTime parseValue(Object input) throws CoercingParseValueException {
                return null;
            }


            @Override
            public OffsetTime parseLiteral(Object input) throws CoercingParseLiteralException {
                return null;
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
        Coercing<OffsetDateTime, String> originalCoercing = originalScalar.getCoercing();
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
                return input instanceof StringValue ? ((StringValue) input).getValue()
                        : originalCoercing.parseLiteral(input);
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

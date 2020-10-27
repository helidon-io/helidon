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
    public static final GraphQLScalarType CUSTOM_OFFSET_DATE_TIME_SCALAR = newOffsetDateTimeScalar(
            FORMATTED_OFFSET_DATETIME_SCALAR);

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
     * @return a new custom date/time scalar
     */
    public static GraphQLScalarType newDateTimeScalar(String name) {
        GraphQLScalarType originalScalar = ExtendedScalars.DateTime;

        return GraphQLScalarType.newScalar()
                .coercing(new DateTimeCoercing())
                .name(name)
                .description("Custom: " + originalScalar.getDescription())
                .build();
    }

    /**
     * Return a new custom offset date/time scalar.
     *
     * @param name the name of the scalar
     * @return a new custom date/time scalar
     */
    @SuppressWarnings("unchecked")
    public static GraphQLScalarType newOffsetDateTimeScalar(String name) {
        GraphQLScalarType originalScalar = ExtendedScalars.DateTime;

        return GraphQLScalarType.newScalar()
                .coercing(new DateTimeCoercing())
                .name(name)
                .description("Custom: " + originalScalar.getDescription())
                .build();
    }

    /**
     * Return a new custom zoned date/time scalar.
     *
     * @param name the name of the scalar
     * @return a new custom date/time scalar
     */
    @SuppressWarnings("unchecked")
    public static GraphQLScalarType newZonedDateTimeScalar(String name) {
        GraphQLScalarType originalScalar = ExtendedScalars.DateTime;

        return GraphQLScalarType.newScalar()
                .coercing(new DateTimeCoercing())
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
    public static GraphQLScalarType newTimeScalar(String name) {
        GraphQLScalarType originalScalar = ExtendedScalars.Time;

        return GraphQLScalarType.newScalar()
                .coercing(new TimeCoercing())
                .name(name)
                .description("Custom: " + originalScalar.getDescription())
                .build();
    }

    /**
     * Return a new custom date scalar.
     *
     * @param name the name of the scalar
     * @return a new custom date scalar
     */
    @SuppressWarnings("unchecked")
    public static GraphQLScalarType newDateScalar(String name) {
        GraphQLScalarType originalScalar = ExtendedScalars.Date;
        return GraphQLScalarType.newScalar()
                .coercing(new DateTimeCoercing())
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

        return GraphQLScalarType.newScalar()
                .coercing(new NumberCoercing<BigDecimal>(originalScalar.getCoercing()))
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

        return GraphQLScalarType.newScalar()
                .coercing(new NumberCoercing<Integer>(originalScalar.getCoercing()))
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

        return GraphQLScalarType.newScalar()
                .coercing(new NumberCoercing<Double>(originalScalar.getCoercing()))
                .name(originalScalar.getName())
                .description("Custom: " + originalScalar.getDescription())
                .build();
    }

    /**
     * Return a new custom BigInteger scalar.
     *
     * @return a new custom BigInteger scalar
     */
    private static GraphQLScalarType newCustomGraphQLBigInteger() {
        GraphQLScalarType originalScalar = GraphQLBigInteger;

        return GraphQLScalarType.newScalar()
                .coercing(new NumberCoercing<BigInteger>(originalScalar.getCoercing()))
                .name(originalScalar.getName())
                .description("Custom: " + originalScalar.getDescription())
                .build();
    }

    /**
     * Abstract implementation of {@link Coercing} interface for given classes.
     */
    public abstract static class AbstractDateTimeCoercing implements Coercing {

        /**
         * {@link Class}es that can be coerced.
         */
        private final Class<?>[] clazzes;

        /**
         * Construct a {@link AbstractDateTimeCoercing}.
         *
         * @param clazzes {@link Class}es to coerce
         */
        public AbstractDateTimeCoercing(Class<?>... clazzes) {
            this.clazzes = clazzes;
        }

        @Override
        public Object serialize(Object dataFetcherResult) throws CoercingSerializeException {
            return convert(dataFetcherResult);
        }

        @Override
        public Object parseValue(Object input) throws CoercingParseValueException {
            return convert(input);
        }

        @Override
        public Object parseLiteral(Object input) throws CoercingParseLiteralException {
            return parseStringLiteral(input);
        }

        /**
         * Convert the given input to the type of if a String then leave it be.
         *
         * @param input input to coerce
         * @return the coerced value
         * @throws CoercingParseLiteralException if any exceptions converting
         */
        private Object convert(Object input) throws CoercingParseLiteralException {
            if (input instanceof String) {
                return (String) input;
            }

            for (Class<?> clazz : clazzes) {
                if (input.getClass().isInstance(clazz)) {
                    return clazz.cast(input);
                }
            }

            throw new CoercingParseLiteralException("Unable to convert type of " + input.getClass());
        }

        /**
         * Parse a String literal and return instance of {@link StringValue} or throw an exception.
         *
         * @param input input to parse
         * @throws CoercingParseLiteralException if it is not a {@link StringValue}
         */
        private String parseStringLiteral(Object input) throws CoercingParseLiteralException {
            if (!(input instanceof StringValue)) {
                throw new CoercingParseLiteralException("Expected AST type 'StringValue' but was '"
                                                                + (
                        input == null
                                ? "null"
                                : input.getClass().getSimpleName()) + "'.");
            }
            return ((StringValue) input).getValue();
        }
    }

    /**
     * Coercing Implementation for Date/Time.
     */
    public static class DateTimeCoercing extends AbstractDateTimeCoercing {

        /**
         * Construct a {@link DateTimeCoercing}.
         */
        public DateTimeCoercing() {
            super(LocalDateTime.class, OffsetDateTime.class, ZonedDateTime.class);
        }
    }

    /**
     * Coercing implementation for Time.
     */
    public static class TimeCoercing extends AbstractDateTimeCoercing {

        /**
         * Construct a {@link TimeCoercing}.
         */
        public TimeCoercing() {
            super(LocalTime.class, OffsetTime.class);
        }
    }

    /**
     * Coercing implementation for Date.
     */
    public static class DateCoercing extends AbstractDateTimeCoercing {

        /**
         * Construct a {@link DateCoercing}.
         */
        public DateCoercing() {
            super(LocalDate.class);
        }
    }

    /**
     * Coercing implementation for BigDecimal.
     */
    public static class BigDecimalCoercing extends AbstractDateTimeCoercing {

        /**
         * Construct a {@link DateCoercing}.
         */
        public BigDecimalCoercing() {
            super(BigDecimal.class);
        }
    }

    /**
     * Number implementation of {@link Coercing} interface for given classes.
     * @param <I> defines input type
     */
    public static class NumberCoercing<I> implements Coercing<I, Object> {

        /**
         * Original {@link Coercing} to fall back on if neeed.
         */
        private final Coercing originalCoercing;

        /**
         * Construct a {@link NumberCoercing} from an original {@link Coercing}.
         *
         * @param originalCoercing original {@link Coercing}
         */
        public NumberCoercing(Coercing originalCoercing) {
            this.originalCoercing = originalCoercing;
        }

        @Override
        public Object serialize(Object dataFetcherResult) throws CoercingSerializeException {
            return dataFetcherResult instanceof String
                    ? (String) dataFetcherResult
                    : originalCoercing.serialize(dataFetcherResult);
        }

        @Override
        public I parseValue(Object input) throws CoercingParseValueException {
            return (I) originalCoercing.parseValue(input);
        }

        @Override
        public I parseLiteral(Object input) throws CoercingParseLiteralException {
            return (I) originalCoercing.parseLiteral(input);
        }
    }
}

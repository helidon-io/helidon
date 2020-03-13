/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates. All rights reserved.
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

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.util.function.Function;

import graphql.language.StringValue;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.GraphQLScalarType;

import static graphql.scalars.util.Kit.typeName;

/**
 * Custom Time scalar. Copied initially from graphql.scalars.datetime.DateTimeScalar from extend scalars package.
 */
public class CustomDateTimeScalar extends GraphQLScalarType {

    public static GraphQLScalarType INSTANCE = new CustomDateTimeScalar();

    public CustomDateTimeScalar() {
        super("DateTime", "An RFC-3339 compliant DateTime Scalar", new Coercing<OffsetDateTime, String>() {
            @Override
            public String serialize(Object input) throws CoercingSerializeException {
                OffsetDateTime offsetDateTime;
                if (input instanceof LocalDateTime) {
                    return DateTimeFormatter.ISO_LOCAL_DATE_TIME.format((TemporalAccessor) input);
                }
                if (input instanceof OffsetDateTime) {
                    offsetDateTime = (OffsetDateTime) input;
                } else if (input instanceof ZonedDateTime) {
                    offsetDateTime = ((ZonedDateTime) input).toOffsetDateTime();
                } else if (input instanceof String) {
                    offsetDateTime = parseOffsetDateTime(input.toString(), CoercingSerializeException::new);
                } else {
                    throw new CoercingSerializeException(
                            "Expected something we can convert to 'java.time.OffsetDateTime' but was '" + typeName(input) + "'."
                    );
                }
                try {
                    return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(offsetDateTime);
                } catch (DateTimeException e) {
                    throw new CoercingSerializeException(
                            "Unable to turn TemporalAccessor into OffsetDateTime because of : '" + e.getMessage() + "'."
                    );
                }
            }

            @Override
            public OffsetDateTime parseValue(Object input) throws CoercingParseValueException {
                OffsetDateTime offsetDateTime;
                if (input instanceof OffsetDateTime) {
                    offsetDateTime = (OffsetDateTime) input;
                } else if (input instanceof ZonedDateTime) {
                    offsetDateTime = ((ZonedDateTime) input).toOffsetDateTime();
                } else if (input instanceof String) {
                    offsetDateTime = parseOffsetDateTime(input.toString(), CoercingParseValueException::new);
                } else {
                    throw new CoercingParseValueException(
                            "Expected a 'String' but was '" + typeName(input) + "'."
                    );
                }
                return offsetDateTime;
            }

            @Override
            public OffsetDateTime parseLiteral(Object input) throws CoercingParseLiteralException {
                if (!(input instanceof StringValue)) {
                    throw new CoercingParseLiteralException(
                            "Expected AST type 'StringValue' but was '" + typeName(input) + "'."
                    );
                }
                return parseOffsetDateTime(((StringValue) input).getValue(), CoercingParseLiteralException::new);
            }

            private OffsetDateTime parseOffsetDateTime(String s, Function<String, RuntimeException> exceptionMaker) {
                try {
                    return OffsetDateTime.parse(s, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                } catch (DateTimeParseException e) {
                    throw exceptionMaker.apply("Invalid RFC3339 value : '" + s + "'. because of : '" + e.getMessage() + "'");
                }
            }
        });
    }
}

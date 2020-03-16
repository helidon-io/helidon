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
import java.time.LocalTime;
import java.time.OffsetTime;
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
 * Custom Time scalar. Copied initially from graphql.scalars.datetime.TimeScalar from extend scalars package.
 */
public class CustomTimeScalar
        extends GraphQLScalarType {

    /**
     * Instance of the scalar.
     */
    public static final GraphQLScalarType INSTANCE = new CustomTimeScalar();

    /**
     * Construct a new scalar.
     */
    public CustomTimeScalar() {
        super("Time", "An Customized RFC-3339 compliant Full Time Scalar", new Coercing<OffsetTime, String>() {
            @Override
            public String serialize(Object input) throws CoercingSerializeException {
                TemporalAccessor temporalAccessor;
                if (input instanceof TemporalAccessor) {
                    temporalAccessor = (TemporalAccessor) input;
                } else if (input instanceof String) {
                    temporalAccessor = parseOffsetTime(input.toString(), CoercingSerializeException::new);
                } else {
                    throw new CoercingSerializeException(
                            "Expected a 'String' or 'java.time.temporal.TemporalAccessor' but was '" + typeName(input) + "'."
                    );
                }
                try {
                    return input instanceof LocalTime
                            ? DateTimeFormatter.ISO_LOCAL_TIME.format(temporalAccessor)
                            : DateTimeFormatter.ISO_OFFSET_TIME.format(temporalAccessor);
                } catch (DateTimeException e) {
                    throw new CoercingSerializeException(
                            "Unable to turn TemporalAccessor into full time because of : '" + e.getMessage() + "'."
                    );
                }
            }

            @Override
            public OffsetTime parseValue(Object input) throws CoercingParseValueException {
                TemporalAccessor temporalAccessor;
                if (input instanceof TemporalAccessor) {
                    temporalAccessor = (TemporalAccessor) input;
                } else if (input instanceof String) {
                    temporalAccessor = parseOffsetTime(input.toString(), CoercingParseValueException::new);
                } else {
                    throw new CoercingParseValueException(
                            "Expected a 'String' or 'java.time.temporal.TemporalAccessor' but was '" + typeName(input) + "'."
                    );
                }
                try {
                    return OffsetTime.from(temporalAccessor);
                } catch (DateTimeException e) {
                    throw new CoercingParseValueException(
                            "Unable to turn TemporalAccessor into full time because of : '" + e.getMessage() + "'."
                    );
                }
            }

            @Override
            public OffsetTime parseLiteral(Object input) throws CoercingParseLiteralException {
                if (!(input instanceof StringValue)) {
                    throw new CoercingParseLiteralException(
                            "Expected AST type 'StringValue' but was '" + typeName(input) + "'."
                    );
                }
                return parseOffsetTime(((StringValue) input).getValue(), CoercingParseLiteralException::new);
            }

            private OffsetTime parseOffsetTime(String s, Function<String, RuntimeException> exceptionMaker) {
                try {
                    TemporalAccessor temporalAccessor = DateTimeFormatter.ISO_OFFSET_TIME.parse(s);
                    return OffsetTime.from(temporalAccessor);
                } catch (DateTimeParseException e) {
                    throw exceptionMaker
                            .apply("Invalid RFC3339 full time value : '" + s + "'. because of : '" + e.getMessage() + "'");
                }
            }
        });
    }
}

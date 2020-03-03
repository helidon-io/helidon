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

package io.helidon.microprofile.graphql.server.util;

import java.math.BigDecimal;
import java.math.BigInteger;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import graphql.Scalars;
import graphql.scalars.ExtendedScalars;
import io.helidon.microprofile.graphql.server.model.Enum;
import io.helidon.microprofile.graphql.server.model.Scalar;
import io.helidon.microprofile.graphql.server.model.Schema;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Input;
import org.eclipse.microprofile.graphql.Interface;
import org.eclipse.microprofile.graphql.Name;
import org.eclipse.microprofile.graphql.Type;

/**
 * Various utilities for generating {@link Schema}s from classes.
 */
public class SchemaUtils {

    /**
     * List of supported scalars keyed by the full class name.
     */
    private static final Map<String, Scalar> SUPPORTED_SCALARS = new HashMap<>() {{
        put(OffsetTime.class.getName(), new Scalar("Time", OffsetTime.class.getName(), ExtendedScalars.Time));
        put(LocalTime.class.getName(), new Scalar("LocalTime", OffsetTime.class.getName(), ExtendedScalars.Time));
        put(Object.class.getName(), new Scalar("Object", Object.class.getName(), ExtendedScalars.Object));
        put(Long.class.getName(), new Scalar("Long", Long.class.getName(), Scalars.GraphQLLong));
        put(OffsetDateTime.class.getName(), new Scalar("DateTime", OffsetDateTime.class.getName(), ExtendedScalars.DateTime));
        put(LocalDate.class.getName(), new Scalar("Date", LocalDate.class.getName(), ExtendedScalars.Date));
        put(BigDecimal.class.getName(), new Scalar("BigDecimal", Long.class.getName(), Scalars.GraphQLBigDecimal));
        put(BigInteger.class.getName(), new Scalar("BigInteger", Long.class.getName(), Scalars.GraphQLBigInteger));
    }};

    /**
     * GraphQL Int.
     */
    protected static final String INT = "Int";

    /**
     * GraphQL Float.
     */
    protected static final String FLOAT = "Float";

    /**
     * GraphQL String.
     */
    protected static final String STRING = "String";

    /**
     * GraphQL ID.
     */
    protected static final String ID = "ID";

    /**
     * GraphQL Boolean.
     */
    protected static final String BOOLEAN = "Boolean";

    /**
     * Private no-args constructor.
     */
    private SchemaUtils() {
    }

    /**
     * Generate a {@link Schema} from a given array of classes.  The classes are checked to see if they contain any of the
     * annotations from the microprofile spec.
     *
     * @param clazzes array of classes to check
     * @return a {@link Schema}
     */
    public static Schema generateSchemaFromClasses(Class<?>... clazzes) {
        Schema schema = new Schema();

        for (Class<?> clazz : clazzes) {
            // Enum
            if (clazz.isAnnotationPresent(org.eclipse.microprofile.graphql.Enum.class)) {
                org.eclipse.microprofile.graphql.Enum annotation = clazz
                        .getAnnotation(org.eclipse.microprofile.graphql.Enum.class);

                String enumName = annotation.value();

                // only check for Name annotation if the Enum didn't have a value
                if (enumName.equals("")) {
                    // check to see if this class has @Name annotation
                    Name nameAnnotation = getNameAnnotation(clazz);
                    if (nameAnnotation != null) {
                        enumName = nameAnnotation.value();
                    }
                }
                schema.addEnum(generateEnum(clazz, enumName));
            }

            // Type, Interface, Input are all treated similarly
            Type      typeAnnotation = clazz.getAnnotation(Type.class);
            Interface interfaceAnnotation = clazz.getAnnotation(Interface.class);
            Input     inputAnnotation  = clazz.getAnnotation(Input.class);

            if (typeAnnotation != null || interfaceAnnotation != null || inputAnnotation != null) {

            }
            
            // obtain top level query API's t
            if (clazz.isAnnotationPresent(GraphQLApi.class)) {
                // defines top level

            }
        }

        return schema;
    }

    /**
     * Process a {@link Class} which has been annotated with {@link GraphQLApi}.
     *
     * @param schema the {@link Schema} to add the discovered information to
     * @param clazz  {@link Class} to introspect
     * @param
     */
    private static void processGraphQLApi(Schema schema, Class<?> clazz) {

    }

    /**
     * Generate an {@link Enum} from a given  {@link java.lang.Enum}.
     *
     * @param clazz    the {@link java.lang.Enum} to introspect
     * @param enumName the name of the enum, if "" then use the simple class name
     * @return a new {@link Enum} or null if the class provided is not an {@link java.lang.Enum}
     */
    private static Enum generateEnum(Class<?> clazz, String enumName) {
        if (clazz.isEnum()) {
            Enum newEnum = new Enum("".equals(enumName.strip()) ? clazz.getSimpleName() : enumName);

            Arrays.stream(clazz.getEnumConstants())
                    .map(v -> v.toString())
                    .forEach(newEnum::addValue);
            return newEnum;
        }
        return null;
    }

    /**
     * Returns a {@link Scalar} if one matches the known list of scalars available from the {@link ExtendedScalars} helper.
     *
     * @param clazzName class name to check for
     * @return a {@link Scalar} if one matches the known list of scalars or null if none found
     */
    private static Scalar getScalar(String clazzName) {
        return SUPPORTED_SCALARS.get(clazzName);
    }

    /**
     * Return the {@link Name} annotation if it exists on the given class.
     * @param clazz {@link Class} to introspect
     * @return the {@link Name} annotation or null if the class does not contain the annotation
     */
    public static Name getNameAnnotation(Class<?> clazz) {
        if (clazz != null && clazz.isAnnotationPresent(Name.class)) {
             return clazz.getAnnotation(Name.class);
        }
        return null;
    }

}

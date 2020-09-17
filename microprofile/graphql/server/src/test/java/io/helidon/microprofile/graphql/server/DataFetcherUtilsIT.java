/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import io.helidon.microprofile.graphql.server.test.db.TestDB;
import io.helidon.microprofile.graphql.server.test.queries.SimpleQueriesWithArgs;
import io.helidon.microprofile.graphql.server.test.types.AbstractVehicle;
import io.helidon.microprofile.graphql.server.test.types.Car;
import io.helidon.microprofile.graphql.server.test.types.ObjectWithIgnorableFieldsAndMethods;
import io.helidon.microprofile.graphql.server.test.types.SimpleContact;
import io.helidon.microprofile.graphql.server.test.types.SimpleContactWithNumberFormats;
import io.helidon.microprofile.graphql.server.test.types.SimpleContactWithSelf;
import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldJunit5Extension;
import org.jboss.weld.junit5.WeldSetup;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Tests for {@link DataFetcherUtils} class.
 */
@ExtendWith(WeldJunit5Extension.class)
class DataFetcherUtilsIT
        extends AbstractGraphQLIT {

    @WeldSetup
    private final WeldInitiator weld = WeldInitiator.of(WeldInitiator.createWeld()
                                                                .addBeanClass(SimpleQueriesWithArgs.class)
                                                                .addBeanClass(SimpleContactWithNumberFormats.class)
                                                                .addBeanClass(SimpleContact.class)
                                                                .addBeanClass(TestDB.class)
                                                                .addExtension(new GraphQLCdiExtension()));

    @Test
    public void testSimpleContact() throws Exception {
        setupIndex(indexFileName, SimpleQueriesWithArgs.class, SimpleContact.class);
        ExecutionContext executionContext = new ExecutionContext(defaultContext);
        Schema schema = executionContext.getSchema();

        Map<String, Object> mapContact = Map.of("id", "1", "name", "Tim", "age", 52);
        SimpleContact simpleContact = new SimpleContact("1", "Tim", 52);
        assertArgumentResult(schema, "canFindContact", "contact", mapContact, simpleContact);
    }

    @Test
    public void testSimpleContactWithNumberFormats() throws Exception {
        setupIndex(indexFileName, SimpleQueriesWithArgs.class, SimpleContactWithNumberFormats.class);
        ExecutionContext executionContext = new ExecutionContext(defaultContext);
        Schema schema = executionContext.getSchema();

        // input SimpleContactWithNumberFormatsInput {
        //  "0 'years old'"
        //  age: String
        //  "¤ 000.00 en-AU"
        //  bankBalance: String
        //  "BigDecimal-##########"
        //  bigDecimal: String
        //  id: Int
        //  "LongValue-##########"
        //  longValue: String
        //  name: String
        //  "0 'value'"
        //  value: String!
        //}
        Map<String, Object> mapContact = Map.of("age",  "52 years old", "bankBalance", "$ 100.00",
                                                "bigDecimal", "BigDecimal-123", "longValue", "LongValue-321",
                                                "name", "Tim", "value", "1 value", "id", "100");
        SimpleContactWithNumberFormats contact =
                new SimpleContactWithNumberFormats(100, "Tim", 52, 100.0f, 1, 321L, BigDecimal.valueOf(123));
        assertArgumentResult(schema, "canFindSimpleContactWithNumberFormats", "contact", mapContact, contact);
    }

    @Test
    public void testSimpleTypes() throws Exception {
        setupIndex(indexFileName, SimpleQueriesWithArgs.class, SimpleContact.class);
        ExecutionContext executionContext = new ExecutionContext(defaultContext);
        Schema schema = executionContext.getSchema();

        assertArgumentResult(schema, "echoString", "String", "string-value", "string-value");

        assertArgumentResult(schema, "returnIntegerAsId", "param1", 1, 1);

        UUID uuid = UUID.randomUUID();
        assertArgumentResult(schema,"returnUUIDAsId", "param1", uuid, uuid);
        assertArgumentResult(schema, "returnStringAsId", "param1", "abc", "abc");
        assertArgumentResult(schema, "returnStringAsId", "param1", "abc", "abc");
        assertArgumentResult(schema, "returnLongAsId", "param1", 1L, 1L);
        assertArgumentResult(schema, "returnLongPrimitiveAsId", "param1", 1L, 1L);
        assertArgumentResult(schema, "returnLongPrimitiveAsId", "param1", 1L, 1L);

    }

    /**
     * Validate that the given argument results in the correct value.
     * @param schema   {@link Schema}
     * @param fdName   the name of the {@link SchemaFieldDefinition}
     * @param argumentName the name of the {@link SchemaArgument}
     * @param input      the input
     * @param expected   the expected output
     * @throws Exception if any errors
     */
    protected void assertArgumentResult(Schema schema, String fdName,
                                        String argumentName, Object input, Object expected) throws Exception {
        SchemaArgument argument = getArgument(schema, "Query", fdName, argumentName);
        assertThat(argument, is(notNullValue()));
        Object result = DataFetcherUtils.generateArgumentValue(schema, argument, input);
        assertThat(result, is(expected));
    }

    protected SchemaArgument getArgument(Schema schema, String typeName, String fdName, String argumentName) {
        SchemaType type = schema.getTypeByName(typeName);
        if (type != null) {
            SchemaFieldDefinition fd = getFieldDefinition(type, fdName);
            if (fd != null) {
                return fd.getArguments().stream()
                        .filter(a -> a.getArgumentName().equals(argumentName))
                        .findFirst()
                        .orElse(null);
            }
        }
        return null;
    }

}

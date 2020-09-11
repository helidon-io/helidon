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

import java.io.IOException;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import io.helidon.microprofile.graphql.server.test.db.TestDB;
import io.helidon.microprofile.graphql.server.test.queries.DescriptionQueries;
import io.helidon.microprofile.graphql.server.test.types.DescriptionType;

import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldJunit5Extension;
import org.jboss.weld.junit5.WeldSetup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(WeldJunit5Extension.class)
public class DescriptionIT extends AbstractGraphQLIT {

    @WeldSetup
    private final WeldInitiator weld = WeldInitiator.of(WeldInitiator.createWeld()
                                                                .addBeanClass(DescriptionType.class)
                                                                .addBeanClass(DescriptionQueries.class)
                                                                .addBeanClass(TestDB.class)
                                                                .addExtension(new GraphQLCdiExtension()));

    @Test
    public void testDescriptions() throws IOException {
        setupIndex(indexFileName, DescriptionType.class, DescriptionQueries.class);
        ExecutionContext executionContext =  new ExecutionContext(defaultContext);

        Schema schema = executionContext.getSchema();
        assertThat(schema, is(notNullValue()));
        SchemaType type = schema.getTypeByName("DescriptionType");
        assertThat(type, is(notNullValue()));
        type.getFieldDefinitions().forEach(fd -> {
            if (fd.getName().equals("id")) {
                assertThat(fd.getDescription(), is("this is the description"));
            }
            if (fd.getName().equals("value")) {
                assertThat(fd.getDescription(), is("description of value"));
            }
            if (fd.getName().equals("longValue1")) {
                // no description so include the format
                assertThat(fd.getDescription(), is(nullValue()));
                assertThat(fd.getFormat()[0], is("L-########"));
            }
            if (fd.getName().equals("longValue2")) {
                // both description and formatting
                assertThat(fd.getDescription(), is("Description"));
            }
        });

        SchemaInputType inputType = schema.getInputTypeByName("DescriptionTypeInput");
        assertThat(inputType, is(notNullValue()));
        inputType.getFieldDefinitions().forEach(fd -> {
            if (fd.getName().equals("value")) {
                assertThat(fd.getDescription(), is("description on set for input"));
            }
        });

        SchemaType query = schema.getTypeByName("Query");
        assertThat(query, is(notNullValue()));
        SchemaFieldDefinition fd = getFieldDefinition(query, "descriptionOnParam");
        assertThat(fd, (is(notNullValue())));

        fd.getArguments().forEach(a -> {
            if (a.getArgumentName().equals("param1")) {
                assertThat(a.getDescription(), is("Description for param1"));
            }
        });
    }

}

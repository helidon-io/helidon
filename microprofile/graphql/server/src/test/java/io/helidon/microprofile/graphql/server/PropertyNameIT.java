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
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import io.helidon.microprofile.graphql.server.test.db.TestDB;
import io.helidon.microprofile.graphql.server.test.queries.PropertyNameQueries;
import io.helidon.microprofile.graphql.server.test.types.TypeWithNameAndJsonbProperty;

import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldJunit5Extension;
import org.jboss.weld.junit5.WeldSetup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;


@ExtendWith(WeldJunit5Extension.class)
public class PropertyNameIT extends AbstractGraphQLIT {

    @WeldSetup
    private final WeldInitiator weld = WeldInitiator.of(WeldInitiator.createWeld()
                                                                .addBeanClass(PropertyNameQueries.class)
                                                                .addBeanClass(TypeWithNameAndJsonbProperty.class)
                                                                .addBeanClass(TestDB.class)
                                                                .addExtension(new GraphQLCdiExtension()));

    @Test
    @SuppressWarnings("unchecked")
    public void testDifferentPropertyNames() throws IOException {
        setupIndex(indexFileName, PropertyNameQueries.class, TypeWithNameAndJsonbProperty.class);
        ExecutionContext executionContext =  new ExecutionContext(defaultContext);

        Map<String, Object> mapResults = getAndAssertResult(
                executionContext.execute("query { query1 { newFieldName1 newFieldName2 "
                                                 + " newFieldName3 newFieldName4 newFieldName5 newFieldName6 } }"));
        assertThat(mapResults.size(), is(1));

        Map<String, Object> mapResults2 = (Map<String, Object>) mapResults.get("query1");
        assertThat(mapResults2.size(), is(6));
        for (int i = 1; i <= 6; i++) {
            assertThat(mapResults2.get("newFieldName" + i), is("name" + i));
        }
    }

}

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

import static org.junit.jupiter.api.Assertions.assertThrows;

import io.helidon.microprofile.graphql.server.test.mutations.VoidMutations;

import io.helidon.microprofile.graphql.server.test.queries.DuplicateNameQueries;
import io.helidon.microprofile.graphql.server.test.queries.InvalidQueries;
import io.helidon.microprofile.graphql.server.test.queries.VoidQueries;
import io.helidon.microprofile.graphql.server.test.types.InvalidNamedTypes;

import io.helidon.microprofile.tests.junit5.AddBean;
import io.helidon.microprofile.tests.junit5.AddExtension;
import io.helidon.microprofile.tests.junit5.DisableDiscovery;
import io.helidon.microprofile.tests.junit5.HelidonTest;
import org.junit.jupiter.api.Test;

/**
 * Container for Error conditions tests.
 */

public class ErrorConditionsTestContainer {
    @HelidonTest
    @DisableDiscovery
    @AddExtension(GraphQLCdiExtension.class)
    @AddBean(VoidMutations.class)
    public static class VoidMutationsIT extends AbstractGraphQLIT {

        @Test
        public void testVoidMutations() throws IOException {
            setupIndex(indexFileName, VoidMutations.class);
            assertThrows(RuntimeException.class, () -> new ExecutionContext(defaultContext));
        }
    }

    @HelidonTest
    @DisableDiscovery
    @AddExtension(GraphQLCdiExtension.class)
    @AddBean(VoidMutations.class)
    public static class VoidQueriesIT extends AbstractGraphQLIT {

        @Test
        public void testVoidQueries() throws IOException {
            setupIndex(indexFileName, VoidQueries.class);
            assertThrows(RuntimeException.class, () -> new ExecutionContext(defaultContext));
        }
    }

    @HelidonTest
    @DisableDiscovery
    @AddExtension(GraphQLCdiExtension.class)
    @AddBean(VoidMutations.class)
    public static class InvalidQueriesIT extends AbstractGraphQLIT {

        @Test
        public void testInvalidQueries() throws IOException {
            setupIndex(indexFileName, InvalidQueries.class);
            assertThrows(RuntimeException.class, () -> new ExecutionContext(defaultContext));
        }
    }

    @HelidonTest
    @DisableDiscovery
    @AddExtension(GraphQLCdiExtension.class)
    @AddBean(VoidMutations.class)
    public static class DuplicateQueriesAndMutationsIT extends AbstractGraphQLIT {

        @Test
        public void testDuplicateQueryOrMutationNames() throws IOException {
            setupIndex(indexFileName, DuplicateNameQueries.class);
            assertThrows(RuntimeException.class, () -> new ExecutionContext(defaultContext));
        }
    }


    @HelidonTest
    @DisableDiscovery
    @AddExtension(GraphQLCdiExtension.class)
    @AddBean(InvalidNamedTypes.InvalidNamedPerson.class)
    public static class InvalidNamedTypeIT extends AbstractGraphQLIT {

        @Test
        public void testInvalidNamedType() throws IOException {
            setupIndex(indexFileName, InvalidNamedTypes.InvalidNamedPerson.class);
            assertThrows(RuntimeException.class, () -> new ExecutionContext(defaultContext));
        }
    }

    @HelidonTest
    @DisableDiscovery
    @AddExtension(GraphQLCdiExtension.class)
    @AddBean(InvalidNamedTypes.InvalidNamedPerson.class)
    public static class InvalidNamedInputTypeIT extends AbstractGraphQLIT {

        @Test
        public void testInvalidNamedInputType() throws IOException {
            setupIndex(indexFileName, InvalidNamedTypes.InvalidInputType.class);
            assertThrows(RuntimeException.class, () -> new ExecutionContext(defaultContext));
        }
    }


    @HelidonTest
    @DisableDiscovery
    @AddExtension(GraphQLCdiExtension.class)
    @AddBean(InvalidNamedTypes.InvalidNamedPerson.class)
    public static class InvalidNamedInterfaceIT extends AbstractGraphQLIT {

        @Test
        public void testInvalidNamedInterface() throws IOException {
            setupIndex(indexFileName, InvalidNamedTypes.InvalidInterface.class, InvalidNamedTypes.InvalidClass.class);
            assertThrows(RuntimeException.class, () -> new ExecutionContext(defaultContext));
        }
    }


    @HelidonTest
    @DisableDiscovery
    @AddExtension(GraphQLCdiExtension.class)
    @AddBean(InvalidNamedTypes.ClassWithInvalidQuery.class)
    public static class InvalidNamedQueryIT extends AbstractGraphQLIT {

        @Test
        public void testInvalidNamedQuery() throws IOException {
            setupIndex(indexFileName, InvalidNamedTypes.ClassWithInvalidQuery.class);
            assertThrows(RuntimeException.class, () -> new ExecutionContext(defaultContext));
        }
    }


    @HelidonTest
    @DisableDiscovery
    @AddExtension(GraphQLCdiExtension.class)
    @AddBean(InvalidNamedTypes.ClassWithInvalidMutation.class)
    public static class InvalidNamedMutationIT extends AbstractGraphQLIT {

        @Test
        public void testInvalidNamedMutation() throws IOException {
            setupIndex(indexFileName, InvalidNamedTypes.ClassWithInvalidMutation.class);
            assertThrows(RuntimeException.class, () -> new ExecutionContext(defaultContext));
        }
    }


    @HelidonTest
    @DisableDiscovery
    @AddExtension(GraphQLCdiExtension.class)
    @AddBean( InvalidNamedTypes.Size.class)
    public static class InvalidNamedEnumIT extends AbstractGraphQLIT {

        @Test
        public void testInvalidNameEnum() throws IOException {
            setupIndex(indexFileName, InvalidNamedTypes.Size.class);
            assertThrows(RuntimeException.class, () -> new ExecutionContext(defaultContext));
        }
    }
}

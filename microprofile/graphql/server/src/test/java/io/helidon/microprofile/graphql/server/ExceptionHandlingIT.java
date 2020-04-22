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

import java.beans.IntrospectionException;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.ID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import graphql.ExecutionResult;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.microprofile.graphql.server.test.db.TestDB;
import io.helidon.microprofile.graphql.server.test.enums.EnumTestWithNameAnnotation;
import io.helidon.microprofile.graphql.server.test.mutations.SimpleMutations;
import io.helidon.microprofile.graphql.server.test.mutations.VoidMutations;
import io.helidon.microprofile.graphql.server.test.queries.ArrayAndListQueries;
import io.helidon.microprofile.graphql.server.test.queries.DefaultValueQueries;
import io.helidon.microprofile.graphql.server.test.queries.DescriptionQueries;
import io.helidon.microprofile.graphql.server.test.queries.DuplicateNameQueries;
import io.helidon.microprofile.graphql.server.test.queries.InvalidQueries;
import io.helidon.microprofile.graphql.server.test.queries.NumberFormatQueriesAndMutations;
import io.helidon.microprofile.graphql.server.test.queries.OddNamedQueriesAndMutations;
import io.helidon.microprofile.graphql.server.test.queries.PropertyNameQueries;
import io.helidon.microprofile.graphql.server.test.queries.QueriesWithIgnorable;
import io.helidon.microprofile.graphql.server.test.queries.QueriesWithNulls;
import io.helidon.microprofile.graphql.server.test.queries.SimpleQueriesNoArgs;
import io.helidon.microprofile.graphql.server.test.queries.SimpleQueriesWithArgs;
import io.helidon.microprofile.graphql.server.test.queries.SimpleQueriesWithSource;
import io.helidon.microprofile.graphql.server.test.queries.VoidQueries;
import io.helidon.microprofile.graphql.server.test.types.AbstractVehicle;
import io.helidon.microprofile.graphql.server.test.types.Car;
import io.helidon.microprofile.graphql.server.test.types.ContactRelationship;
import io.helidon.microprofile.graphql.server.test.types.DateTimePojo;
import io.helidon.microprofile.graphql.server.test.types.DefaultValuePOJO;
import io.helidon.microprofile.graphql.server.test.types.DescriptionType;
import io.helidon.microprofile.graphql.server.test.types.Level0;
import io.helidon.microprofile.graphql.server.test.types.Motorbike;
import io.helidon.microprofile.graphql.server.test.types.MultiLevelListsAndArrays;
import io.helidon.microprofile.graphql.server.test.types.NullPOJO;
import io.helidon.microprofile.graphql.server.test.types.ObjectWithIgnorableFieldsAndMethods;
import io.helidon.microprofile.graphql.server.test.types.Person;
import io.helidon.microprofile.graphql.server.test.types.PersonWithName;
import io.helidon.microprofile.graphql.server.test.types.SimpleContact;
import io.helidon.microprofile.graphql.server.test.types.SimpleContactInputType;
import io.helidon.microprofile.graphql.server.test.types.SimpleContactInputTypeWithAddress;
import io.helidon.microprofile.graphql.server.test.types.SimpleContactInputTypeWithName;
import io.helidon.microprofile.graphql.server.test.types.SimpleContactInputTypeWithNameValue;
import io.helidon.microprofile.graphql.server.test.types.SimpleContactWithNumberFormats;
import io.helidon.microprofile.graphql.server.test.types.SimpleContactWithSelf;
import io.helidon.microprofile.graphql.server.test.types.TypeWithIDs;
import io.helidon.microprofile.graphql.server.test.types.TypeWithNameAndJsonbProperty;
import io.helidon.microprofile.graphql.server.test.types.Vehicle;
import io.helidon.microprofile.graphql.server.test.types.VehicleIncident;
import javax.enterprise.inject.se.SeContainer;
import javax.enterprise.inject.se.SeContainerInitializer;
import org.eclipse.microprofile.graphql.ConfigKey;
import org.eclipse.microprofile.graphql.NonNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

/**
 * Integration tests for testing exception handing in {@link SchemaGeneratorTest}.
 */
@SuppressWarnings("unchecked")
public class ExceptionHandlingIT
        extends AbstractGraphQLIT {

    @Test
    public void testAllDefaultsForConfig() throws IOException {
        setupIndex(indexFileName);
        ExecutionContext<DefaultContext> executionContext = new ExecutionContext<>(defaultContext);
        assertThat(executionContext, is(notNullValue()));
        assertThat(executionContext.getDefaultErrorMessage(), is("Server Error"));
        assertThat(executionContext.getExceptionBlacklist().size(), is(0));
        assertThat(executionContext.getExceptionWhitelist().size(), is(0));
    }

    @Test
    public void testDifferentMessage() throws IOException {
        Properties properties = new Properties();
        Config config = Config.create(ConfigSources.create(Map.of(ConfigKey.DEFAULT_ERROR_MESSAGE, "new message")).build());
        setupIndex(indexFileName);
        ExecutionContext<DefaultContext> executionContext = new ExecutionContext<>(defaultContext);
        assertThat(executionContext.getDefaultErrorMessage(), is("new message"));
    }

}

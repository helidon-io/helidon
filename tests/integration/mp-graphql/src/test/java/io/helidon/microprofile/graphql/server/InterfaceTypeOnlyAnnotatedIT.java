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
import java.io.IOException;

import io.helidon.microprofile.graphql.server.test.queries.NoopQueriesAndMutations;
import javax.inject.Inject;

import io.helidon.microprofile.graphql.server.test.db.TestDB;
import io.helidon.microprofile.graphql.server.test.types.AbstractVehicle;
import io.helidon.microprofile.graphql.server.test.types.Car;
import io.helidon.microprofile.graphql.server.test.types.Motorbike;
import io.helidon.microprofile.graphql.server.test.types.Vehicle;
import io.helidon.microprofile.graphql.server.test.types.VehicleIncident;
import io.helidon.microprofile.tests.junit5.AddBean;

import org.junit.jupiter.api.Test;

/**
 * Tests for interfaces and subsequent unresolved type which has a Name annotation .
 */
@AddBean(Vehicle.class)
@AddBean(Car.class)
@AddBean(Motorbike.class)
@AddBean(VehicleIncident.class)
@AddBean(TestDB.class)
@AddBean(NoopQueriesAndMutations.class)
public class InterfaceTypeOnlyAnnotatedIT extends AbstractGraphQlCdiIT {

    @Inject
    InterfaceTypeOnlyAnnotatedIT(GraphQlCdiExtension graphQlCdiExtension) {
        super(graphQlCdiExtension);
    }

    @Test
    public void testInterfaceDiscoveryWithUnresolvedType() throws IOException, IntrospectionException, ClassNotFoundException {
        setupIndex(indexFileName, Vehicle.class, Car.class, Motorbike.class, VehicleIncident.class,
                   AbstractVehicle.class, NoopQueriesAndMutations.class);
        assertInterfaceResults();
    }

}

/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.graphql.server.test.queries;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.json.bind.annotation.JsonbProperty;

import io.helidon.microprofile.graphql.server.test.db.TestDB;
import io.helidon.microprofile.graphql.server.test.enums.EnumTestWithNameAnnotation;
import io.helidon.microprofile.graphql.server.test.types.Address;
import io.helidon.microprofile.graphql.server.test.types.ContactRelationship;
import io.helidon.microprofile.graphql.server.test.types.Person;
import io.helidon.microprofile.graphql.server.test.types.SimpleContact;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Id;
import org.eclipse.microprofile.graphql.Name;
import org.eclipse.microprofile.graphql.Query;
import org.eclipse.microprofile.graphql.Source;

/**
 * Class that holds simple query definitions with arguments with the .
 */
@GraphQLApi
@ApplicationScoped
public class SimpleQueriesWithSource {

    public SimpleQueriesWithSource() {
    }

    @Inject
    private TestDB testDB;

    // the following should add a "idAndName" field on the SimpleContact type
    // which will return the id and name concatenated
    public String idAndName(@Source @Name("contact") SimpleContact contact) {
        return contact.getId() + " " + contact.getName();
    }

    // the following should add a "currentJob" field on the SimpleContact type and a top level query as well
    @Query
    public String currentJob(@Source @Name("contact") SimpleContact contact) {
        return "Manager " + contact.getId();
    }

    // technically this is a mutation but just treading as returning random contact
    @Query
    @Name("findContact")
    public SimpleContact retrieveSimpleContact() {
        return testDB.createRandomContact();
    }

    @Name("lastAddress")
    public Address returnTheLastAddress(@Source @Name("contact") SimpleContact contact) {
        return testDB.generateWorkAddress();
    }

    @Name("lastNAddress")
    public Collection<Address> returnTheLastNAddress(@Source @Name("contact") SimpleContact contact, @Name("count") int count) {
        Set<Address> setAddresses = new HashSet<>();
        setAddresses.add(testDB.generateWorkAddress());
        setAddresses.add(testDB.generateHomeAddress());
        return setAddresses;
    }
}

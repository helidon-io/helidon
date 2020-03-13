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

package io.helidon.microprofile.graphql.server.test.mutations;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.json.bind.annotation.JsonbProperty;

import io.helidon.microprofile.graphql.server.test.db.TestDB;
import io.helidon.microprofile.graphql.server.test.enums.EnumTestWithNameAnnotation;
import io.helidon.microprofile.graphql.server.test.types.ContactRelationship;
import io.helidon.microprofile.graphql.server.test.types.Person;
import io.helidon.microprofile.graphql.server.test.types.SimpleContact;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Id;
import org.eclipse.microprofile.graphql.Mutation;
import org.eclipse.microprofile.graphql.Name;
import org.eclipse.microprofile.graphql.Query;

/**
 * Class that holds simple mutations definitions with various numbers of arguments.
 */
@GraphQLApi
@ApplicationScoped
public class SimpleMutations {

    public SimpleMutations() {
    }

    @Inject
    private TestDB testDB;

    @Mutation
    public SimpleContact createNewContact() {
        return testDB.createRandomContact();
    }

    @Mutation("createContactWithName")
    public SimpleContact createContact(@Name("name") String name) {
        return testDB.createContactWithName(name);
    }

    // this method should have then name with the set removed
    @Mutation
    public String setEchoStringValue(@Name("value") String value) {
        return value;
    }

    @Mutation
    public String testId(@Name("name") String name, @Name("id") Long idNumber) {
        return "OK";
    }

    @Mutation("createAndReturnNewContact")
    public SimpleContact createNewContact(@Name("newContact") SimpleContact contact) {
        return contact;
    }

}

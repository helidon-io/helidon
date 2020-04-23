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

package io.helidon.microprofile.graphql.server.test.types;

import org.eclipse.microprofile.graphql.Enum;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Input;
import org.eclipse.microprofile.graphql.Interface;
import org.eclipse.microprofile.graphql.Mutation;
import org.eclipse.microprofile.graphql.Name;
import org.eclipse.microprofile.graphql.Query;
import org.eclipse.microprofile.graphql.Type;

/**
 * Class representing types with invalid names.
 */
@Type("_InvalidName")
public class InvalidNamedTypes {

    @Type("__InvalidName")
    public static class InvalidNamedPerson {
        private int personId;
        private String name;

        public InvalidNamedPerson(int personId, String name) {
            this.personId = personId;
            this.name = name;
        }

        public int getPersonId() {
            return personId;
        }

        public void setPersonId(int personId) {
            this.personId = personId;
        }
    }

    @Input
    @Name("1ThisIsInvalid")
    public static class InvalidInputType {
        private String string;

        public InvalidInputType(String string) {
            this.string = string;
        }

        public String getString() {
            return string;
        }

        public void setString(String string) {
            this.string = string;
        }
    }

    @Interface("####Name")
    public static interface InvalidInterface {
        String getName();
    }

    public static class InvalidClass implements InvalidInterface {

        private String name;

        public InvalidClass(String name) {
            this.name = name;
        }

        public void setName(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }
    }

    @GraphQLApi
    public static class ClassWithInvalidQuery {
        @Query("__Name")
        public String getString() {
            return "string";
        }
    }

    @GraphQLApi
    public static class ClassWithInvalidMutation {
        @Mutation("123BadName")
        public String echoString(String string) {
            return string;
        }
    }

    @Enum("&!@@!")
    public enum Size {
        S,
        M,
        L,
        XL,
        XXL,
        XXXL
    }
}

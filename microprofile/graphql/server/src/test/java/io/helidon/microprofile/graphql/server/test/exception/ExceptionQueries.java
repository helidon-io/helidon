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

package io.helidon.microprofile.graphql.server.test.exception;

import io.helidon.microprofile.graphql.server.test.db.TestDB;
import io.helidon.microprofile.graphql.server.test.enums.EnumTestWithEnumName;
import io.helidon.microprofile.graphql.server.test.types.SimpleContact;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import java.io.IOException;
import java.security.AccessControlException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.GraphQLException;
import org.eclipse.microprofile.graphql.Name;
import org.eclipse.microprofile.graphql.Query;


/**
 * Class that holds queries that raise various exceptions.
 */
@GraphQLApi
@ApplicationScoped
public class ExceptionQueries {

    @Inject
    private TestDB testDB;

    public ExceptionQueries() {
    }

    @Query
    public String query1() {
        return "hello world";
    }

    @Query("uncheckedQuery1")
    public String uncheckedQuery1() {
        throw new IllegalArgumentException(new AccessControlException("my exception"));
    }

    @Query("uncheckedQuery2")
    public String uncheckedQuery2() {
        throw new MyIllegalArgumentException(new AccessControlException("my exception"));
    }

    @Query
    public String checkedQuery1(@Name("throwException") boolean throwException) throws IOException {
        if (throwException) {
            throw new IOException("exception");
        }
        return String.valueOf(throwException);
    }

    @Query("checkedException")
    public String checkedException() throws IOException {
        throw new IOException("unable to do this");
    }

    @Query("checkedException2")
    public String checkedException2() throws MyIOException {
        throw new MyIOException("my message");
    }

    @Query("defaultContact")
    public SimpleContact getDefaultContact() {
        return testDB.createRandomContact();
    }

    @Query
    public List<Integer> failAfterNResults(@Name("failAfter") int failAfter) throws GraphQLException {
        List<Integer> listIntegers = new ArrayList<>();
        int i = 0;
        while (i++ < failAfter) {
            listIntegers.add(i);
        }
        throw new GraphQLException("Partial results", listIntegers);
    }

    @Query
    public List<SimpleContact> failAfterNContacts(@Name("failAfter") int failAfter) throws GraphQLException {
        List<SimpleContact> listContacts = new ArrayList<>();
        int i = 0;
        while (i++ < failAfter) {
            listContacts.add(new SimpleContact("id-" + i, "Name-" + i, i, EnumTestWithEnumName.XL));
        }
        throw new GraphQLException("Partial results", listContacts);
    }

    @Query
    public String throwOOME() {
        throw new OutOfMemoryError("error");
    }

    public static class MyIOException extends IOException {
        public MyIOException() {
            super();
        }

        public MyIOException(String message) {
            super(message);
        }
    }

    public static class MyIllegalArgumentException extends IllegalArgumentException {
        public MyIllegalArgumentException(Throwable cause) {
            super(cause);
        }
    }
}

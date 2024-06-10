/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.docs.mp;

import java.util.Collection;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Mutation;
import org.eclipse.microprofile.graphql.Name;
import org.eclipse.microprofile.graphql.NonNull;
import org.eclipse.microprofile.graphql.Query;

@SuppressWarnings("ALL")
class GraphqlSnippets {

    // stub
    record Customer() {
    }

    // stub
    static class CustomerService {

        Customer createCustomer(int id, String name, float balance) {
            return null;
        }

        Customer getCustomer(int id) {
            return null;
        }

        Collection<Customer> getAllCustomers() {
            return List.of();
        }

        Collection<Customer> getAllCustomers(String name) {
            return List.of();
        }
    }

    // tag::snippet_1[]
    @ApplicationScoped
    @GraphQLApi
    public class ContactGraphQLApi {

        @Inject
        private CustomerService customerService;

        @Query
        public Collection<Customer> findAllCustomers() { // <1>
            return customerService.getAllCustomers();
        }

        @Query
        public Customer findCustomer(@Name("customerId") int id) { // <2>
            return customerService.getCustomer(id);
        }

        @Query
        public Collection<Customer> findCustomersByName(@Name("name") String name) { // <3>
            return customerService.getAllCustomers(name);
        }

        @Mutation
        public Customer createCustomer(@Name("customerId") int id, // <4>
                                      @Name("name") String name,
                                      @Name("balance") float balance) {
            return customerService.createCustomer(id, name, balance);
        }
    }

    public class customer {
        private int id;
        @NonNull
        private String name;
        private float balance;

        // getters and setters omitted for brevity
    }
    // end::snippet_1[]

}

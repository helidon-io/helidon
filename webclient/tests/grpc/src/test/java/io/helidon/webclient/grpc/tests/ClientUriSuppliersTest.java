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
package io.helidon.webclient.grpc.tests;

import java.net.URI;
import java.util.stream.IntStream;

import io.helidon.webclient.api.ClientUri;

import org.junit.jupiter.api.Test;

import static io.helidon.webclient.grpc.ClientUriSuppliers.OrderedSupplier;
import static io.helidon.webclient.grpc.ClientUriSuppliers.RandomSupplier;
import static io.helidon.webclient.grpc.ClientUriSuppliers.RoundRobinSupplier;
import static io.helidon.webclient.grpc.ClientUriSuppliers.SingleSupplier;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.collection.IsIn.isIn;
import static org.hamcrest.MatcherAssert.assertThat;

class ClientUriSuppliersTest {

    private static final ClientUri[] CLIENT_URIS = {
            ClientUri.create(URI.create("http://localhost:8000")),
            ClientUri.create(URI.create("http://localhost:8001")),
            ClientUri.create(URI.create("http://localhost:8002"))
    };

    @Test
    void testOrderedSupplier() {
        OrderedSupplier supplier = OrderedSupplier.create(CLIENT_URIS);
        assertThat(supplier.hasNext(), is(true));
        assertThat(supplier.next(), is(CLIENT_URIS[0]));
        assertThat(supplier.hasNext(), is(true));
        assertThat(supplier.next(), is(CLIENT_URIS[1]));
        assertThat(supplier.hasNext(), is(true));
        assertThat(supplier.next(), is(CLIENT_URIS[2]));
        assertThat(supplier.hasNext(), is(false));
    }

    @Test
    void testRoundRobinSupplier() {
        RoundRobinSupplier supplier = RoundRobinSupplier.create(CLIENT_URIS);
        IntStream.range(0, 5).forEach(i -> {
            assertThat(supplier.hasNext(), is(true));
            assertThat(supplier.next(), is(CLIENT_URIS[0]));
            assertThat(supplier.hasNext(), is(true));
            assertThat(supplier.next(), is(CLIENT_URIS[1]));
            assertThat(supplier.hasNext(), is(true));
            assertThat(supplier.next(), is(CLIENT_URIS[2]));
        });
    }

    @Test
    void testSingleSupplier() {
        SingleSupplier supplier = SingleSupplier.create(CLIENT_URIS[0]);
        IntStream.range(0, 5).forEach(i -> {
            assertThat(supplier.hasNext(), is(true));
            assertThat(supplier.next(), is(CLIENT_URIS[0]));
        });
    }

    @Test
    void testRandomSupplier() {
        RandomSupplier supplier = RandomSupplier.create(CLIENT_URIS);
        IntStream.range(0, 5).forEach(i -> {
            assertThat(supplier.hasNext(), is(true));
            assertThat(supplier.next(), isIn(CLIENT_URIS));
        });
    }
}

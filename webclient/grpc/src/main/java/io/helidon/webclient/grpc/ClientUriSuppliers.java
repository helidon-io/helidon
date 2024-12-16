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
package io.helidon.webclient.grpc;

import java.net.URI;
import java.security.SecureRandom;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import io.helidon.webclient.api.ClientUri;

/**
 * Some popular implementations of the {@link io.helidon.webclient.grpc.ClientUriSupplier}
 * interface.
 */
public class ClientUriSuppliers {

    /**
     * Supplies an iterator that returns URIs chosen in order from
     * first to last.
     */
    public static class OrderedSupplier implements ClientUriSupplier {

        private final Iterator<ClientUri> clientUris;

        /**
         * Creates an ordered supplier.
         *
         * @param clientUris array of client URIs
         * @return new supplier
         */
        public static OrderedSupplier create(ClientUri... clientUris) {
            return new OrderedSupplier(List.of(clientUris));
        }

        /**
         * Creates an ordered supplier.
         *
         * @param clientUris collection of client URIs
         * @return new supplier
         */
        public static OrderedSupplier create(Collection<ClientUri> clientUris) {
            return new OrderedSupplier(clientUris);
        }

        protected OrderedSupplier(Collection<ClientUri> clientUris) {
            this.clientUris = List.copyOf(clientUris).iterator();
        }

        @Override
        public boolean hasNext() {
            return clientUris.hasNext();
        }

        @Override
        public ClientUri next() {
            return clientUris.next();
        }
    }

    /**
     * Supplies a neven-ending iterator that returns URIs chosen using
     * a round-robin strategy.
     */
    public static class RoundRobinSupplier implements ClientUriSupplier {

        private int next;
        private final ClientUri[] clientUris;

        /**
         * Creates a round-robin supplier.
         *
         * @param clientUris array of client URIs
         * @return new supplier
         */
        public static RoundRobinSupplier create(ClientUri... clientUris) {
            return new RoundRobinSupplier(clientUris);
        }

        /**
         * Creates a round-robin supplier.
         *
         * @param clientUris collection of client URIs
         * @return new supplier
         */
        public static RoundRobinSupplier create(Collection<ClientUri> clientUris) {
            return new RoundRobinSupplier(clientUris.toArray(new ClientUri[]{}));
        }

        protected RoundRobinSupplier(ClientUri[] clientUris) {
            this.clientUris = clientUris;
        }

        @Override
        public boolean hasNext() {
            return true;
        }

        @Override
        public ClientUri next() {
            return clientUris[next++ % clientUris.length];
        }
    }

    /**
     * Supplies the same client URI over and over, never ends.
     */
    public static class SingleSupplier implements ClientUriSupplier {

        private final ClientUri clientUri;

        /**
         * Creates a single supplier.
         *
         * @param clientUri the client URI as a string
         * @return new supplier
         */
        public static SingleSupplier create(String clientUri) {
            return new SingleSupplier(ClientUri.create(URI.create(clientUri)));
        }

        /**
         * Creates a single supplier.
         *
         * @param clientUri the client URI
         * @return new supplier
         */
        public static SingleSupplier create(ClientUri clientUri) {
            return new SingleSupplier(clientUri);
        }

        protected SingleSupplier(ClientUri clientUri) {
            this.clientUri = clientUri;
        }

        @Override
        public boolean hasNext() {
            return true;
        }

        @Override
        public ClientUri next() {
            return clientUri;
        }
    }

    /**
     * Supplies an iterator that returns a URI chosen at random, never ends.
     */
    public static class RandomSupplier implements ClientUriSupplier {

        private final ClientUri[] clientUris;
        private final SecureRandom random = new SecureRandom();

        /**
         * Creates a random supplier.
         *
         * @param clientUris array of client URIs
         * @return new supplier
         */
        public static RandomSupplier create(ClientUri... clientUris) {
            return new RandomSupplier(clientUris);
        }

        /**
         * Creates a random supplier.
         *
         * @param clientUris collection of client URIs
         * @return new supplier
         */
        public static RandomSupplier create(Collection<ClientUri> clientUris) {
            return new RandomSupplier(clientUris.toArray(new ClientUri[]{}));
        }

        protected RandomSupplier(ClientUri[] clientUris) {
            this.clientUris = clientUris;
        }

        @Override
        public boolean hasNext() {
            return true;
        }

        @Override
        public ClientUri next() {
            return clientUris[random.nextInt(clientUris.length)];
        }
    }
}

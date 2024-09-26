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

import java.util.Iterator;

import io.helidon.webclient.api.ClientUri;

/**
 * Interface implemented by all client URI suppliers.
 */
public interface ClientUriSupplier extends Iterator<ClientUri>, Iterable<ClientUri> {

    @Override
    default Iterator<ClientUri> iterator() {
        return this;
    }
}

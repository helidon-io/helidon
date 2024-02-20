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

package io.helidon.webserver;

import java.util.Optional;

import io.helidon.common.GenericType;
import io.helidon.http.Http;
import io.helidon.service.inject.api.Injection;
import io.helidon.service.inject.api.Injection.QualifiedInstance;
import io.helidon.service.inject.api.Lookup;
import io.helidon.service.inject.api.Qualifier;
import io.helidon.webserver.http.ServerRequest;

@Injection.RequestScope
@Http.HttpQualified
class EntityProvider implements Injection.QualifiedProvider<Http.Entity, Object> {
    private final ServerRequest req;

    @Injection.Inject
    EntityProvider(ServerRequest req) {
        this.req = req;
    }

    @Override
    public Optional<QualifiedInstance<Object>> first(Qualifier qualifier, Lookup lookup, GenericType<Object> type) {
        return Optional.of(QualifiedInstance.create(req.content().as(type)));
    }
}

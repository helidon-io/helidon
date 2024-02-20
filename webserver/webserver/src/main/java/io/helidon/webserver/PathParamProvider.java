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
import io.helidon.common.mapper.OptionalValue;
import io.helidon.common.parameters.Parameters;
import io.helidon.http.Http;
import io.helidon.service.inject.api.Injection;
import io.helidon.service.inject.api.Injection.QualifiedInstance;
import io.helidon.service.inject.api.Injection.QualifiedProvider;
import io.helidon.service.inject.api.Lookup;
import io.helidon.service.inject.api.Qualifier;
import io.helidon.webserver.http.ServerRequest;

@Injection.RequestScope
class PathParamProvider implements QualifiedProvider<Http.PathParam, Object> {
    private final Parameters pathParams;

    @Injection.Inject
    PathParamProvider(ServerRequest req) {
        this.pathParams = req.path().pathParameters();
    }

    @Override
    public Optional<QualifiedInstance<Object>> first(Qualifier qualifier, Lookup lookup, GenericType<Object> type) {
        String requestedQueryParam = qualifier.stringValue().orElseThrow(() -> new IllegalArgumentException(
                "Annotation @PathParam has required value, yet it was not specified for " + lookup));


        OptionalValue<?> value;

        if (pathParams.contains(requestedQueryParam)) {
            value = pathParams.first(requestedQueryParam);
        } else {
            return Optional.empty();
        }

        Object realValue;
        if (type.equals(GenericType.OBJECT)) {
            realValue = value.asString().get();
        } else {
            realValue = value.as(type).get();
        }

        return Optional.of(QualifiedInstance.create(realValue, Qualifier.createNamed(requestedQueryParam)));
    }
}

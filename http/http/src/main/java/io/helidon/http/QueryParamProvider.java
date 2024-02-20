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

package io.helidon.http;

import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.common.GenericType;
import io.helidon.common.LazyValue;
import io.helidon.common.mapper.OptionalValue;
import io.helidon.common.uri.UriQuery;
import io.helidon.service.inject.api.Injection;
import io.helidon.service.inject.api.Injection.QualifiedInstance;
import io.helidon.service.inject.api.Injection.QualifiedProvider;
import io.helidon.service.inject.api.Lookup;
import io.helidon.service.inject.api.Qualifier;

@Injection.RequestScope
@Http.HttpQualified
class QueryParamProvider implements QualifiedProvider<Http.QueryParam, Object> {
    private final LazyValue<UriQuery> querySupplier;

    @Injection.Inject
    QueryParamProvider(Supplier<Optional<HttpPrologue>> prologue) {
        this.querySupplier = LazyValue.create(() -> {
            Optional<HttpPrologue> httpPrologue = prologue.get();
            if (httpPrologue.isEmpty()) {
                throw new IllegalStateException(
                        "You are injecting @QueryParam, yet there is no provider of HttpPrologue available.");
            }
            return httpPrologue.get().query();
        });
    }

    @Override
    public Optional<QualifiedInstance<Object>> first(Qualifier qualifier, Lookup lookup, GenericType<Object> type) {
        String requestedQueryParam = qualifier.stringValue().orElseThrow(() -> new IllegalArgumentException(
                "Annotation @QueryParam has required value, yet it was not specified for " + lookup));

        OptionalValue<?> value;
        UriQuery query = querySupplier.get();
        if (query.contains(requestedQueryParam)) {
            value = query.first(requestedQueryParam);
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

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
import io.helidon.service.inject.api.Injection;
import io.helidon.service.inject.api.Injection.QualifiedInstance;
import io.helidon.service.inject.api.Injection.QualifiedProvider;
import io.helidon.service.inject.api.Lookup;
import io.helidon.service.inject.api.Qualifier;

@Injection.RequestScope
@Http.HttpQualified
class HeaderParamProvider implements QualifiedProvider<Http.HeaderParam, Object> {
    private static final GenericType<?> HEADER_TYPE = GenericType.create(Header.class);

    private final LazyValue<Headers> headerSupplier;

    @Injection.Inject
    HeaderParamProvider(Supplier<Optional<Headers>> headerSupplier) {
        this.headerSupplier = LazyValue.create(() -> {
            Optional<Headers> headers = headerSupplier.get();
            if (headers.isEmpty()) {
                throw new IllegalStateException("You are injecting @HeaderParam, yet there is no provider of Headers available.");
            }
            return headers.get();
        });
    }

    @Override
    public Optional<QualifiedInstance<Object>> first(Qualifier qualifier, Lookup lookup, GenericType<Object> type) {
        String requestedHeader = qualifier.stringValue().orElseThrow(() -> new IllegalArgumentException(
                "Annotation @HeaderParam has required value, yet it was not specified for " + lookup));

        Headers headers = headerSupplier.get();

        HeaderName headerName = HeaderNames.create(requestedHeader);

        if (!headers.contains(headerName)) {
            return Optional.empty();
        }

        Header header = headers.get(headerName);

        Object value;

        if (type.equals(GenericType.OBJECT)) {
            value = header.get();
        } else if (type.equals(HEADER_TYPE)) {
            value = header;
        } else {
            value = header.as(type).get();
        }

        return Optional.of(QualifiedInstance.create(value, Qualifier.createNamed(requestedHeader)));
    }
}

package io.helidon.http;

import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.common.GenericType;
import io.helidon.common.LazyValue;
import io.helidon.inject.service.Injection;
import io.helidon.inject.service.Lookup;
import io.helidon.inject.service.QualifiedInstance;
import io.helidon.inject.service.QualifiedProvider;
import io.helidon.inject.service.Qualifier;

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

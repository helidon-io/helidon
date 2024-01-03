package io.helidon.webserver;

import java.util.List;
import java.util.Optional;

import io.helidon.common.GenericType;
import io.helidon.common.types.TypeName;
import io.helidon.http.Header;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.Http;
import io.helidon.http.ServerRequestHeaders;
import io.helidon.inject.service.Injection;
import io.helidon.inject.service.Qualifier;
import io.helidon.webserver.http.ServerRequest;

@Injection.Requeston
class HeaderProducer implements QualifiedProvider {
    private static final TypeName HEADER_PARAM = TypeName.create(Http.HeaderParam.class);
    private static final GenericType<Header> HEADER_TYPE = GenericType.create(Header.class);


    private final ServerRequestHeaders headers;

    @Injection.Inject
    HeaderProducer(ServerRequest req) {
        this.headers = req.headers();
    }

    @Override
    public TypeName qualifierType() {
        return HEADER_PARAM;
    }

    @Override
    public <T> Optional<T> first(Qualifier qualifier, GenericType<T> expectedType) {
        String name = qualifier.stringValue().orElseThrow(() -> new IllegalArgumentException(
                "A HeaderParam qualifier without a name was received. The value is required on the annotation, this should not "
                        + "happen."));

        if (Injection.Named.WILDCARD_NAME.equals(name)) {
            throw new IllegalArgumentException("Cannot provide a single header value for wildcard name.");
        }
        HeaderName headerName = HeaderNames.create(name);
        if (headers.contains(headerName)) {
            if (HEADER_TYPE.equals(expectedType)) {
                return Optional.of(expectedType.cast(headers.get(headerName)));
            }
            return headers.get(headerName).as(expectedType).asOptional();
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> List<T> all(Qualifier qualifier, GenericType<T> expectedType) {
        String name = qualifier.stringValue().orElseThrow(() -> new IllegalArgumentException(
                "A HeaderParam qualifier without a name was received. The value is required on the annotation, this should not "
                        + "happen."));

        List<Header> headerList;
        if (Injection.Named.WILDCARD_NAME.equals(name)) {
            headerList = headers.stream().toList();
        } else {
            HeaderName headerName = HeaderNames.create(name);
            if (headers.contains(headerName)) {
                headerList = List.of(headers.get(headerName));
            } else {
                headerList = List.of();
            }
        }

        if (HEADER_TYPE.equals(expectedType)) {
            return (List<T>) headerList;
        }

        return headerList.stream()
                .flatMap(it -> it.as(expectedType).stream())
                .toList();
    }
}

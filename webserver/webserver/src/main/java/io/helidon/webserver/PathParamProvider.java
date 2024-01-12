package io.helidon.webserver;

import java.util.Optional;

import io.helidon.common.GenericType;
import io.helidon.common.mapper.OptionalValue;
import io.helidon.common.parameters.Parameters;
import io.helidon.http.Http;
import io.helidon.inject.service.Injection;
import io.helidon.inject.service.Lookup;
import io.helidon.inject.service.QualifiedInstance;
import io.helidon.inject.service.QualifiedProvider;
import io.helidon.inject.service.Qualifier;
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

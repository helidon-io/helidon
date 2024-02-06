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

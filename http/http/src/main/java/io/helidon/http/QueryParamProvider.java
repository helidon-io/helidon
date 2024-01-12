package io.helidon.http;

import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.common.GenericType;
import io.helidon.common.LazyValue;
import io.helidon.common.mapper.OptionalValue;
import io.helidon.common.uri.UriQuery;
import io.helidon.inject.service.Injection;
import io.helidon.inject.service.Lookup;
import io.helidon.inject.service.QualifiedInstance;
import io.helidon.inject.service.QualifiedProvider;
import io.helidon.inject.service.Qualifier;

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

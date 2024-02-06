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

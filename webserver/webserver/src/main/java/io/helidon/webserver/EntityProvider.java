package io.helidon.webserver;

import java.util.Optional;

import io.helidon.common.GenericType;
import io.helidon.http.Http;
import io.helidon.inject.service.Injection;
import io.helidon.inject.service.Lookup;
import io.helidon.inject.service.QualifiedInstance;
import io.helidon.inject.service.QualifiedProvider;
import io.helidon.inject.service.Qualifier;
import io.helidon.webserver.http.ServerRequest;

@Injection.RequestScope
@Http.HttpQualified
class EntityProvider implements QualifiedProvider<Http.Entity, Object> {
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

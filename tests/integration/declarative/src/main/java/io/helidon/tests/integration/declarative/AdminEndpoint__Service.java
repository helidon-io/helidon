package io.helidon.tests.integration.declarative;

import java.util.function.Supplier;

import io.helidon.common.types.TypeName;
import io.helidon.inject.RequestonControl;
import io.helidon.inject.Scope;
import io.helidon.inject.Services;
import io.helidon.inject.service.Injection;
import io.helidon.inject.service.Lookup;
import io.helidon.inject.service.Qualifier;
import io.helidon.security.SecurityContext;
import io.helidon.webserver.http.HttpFeature;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

@Injection.Singleton
public class AdminEndpoint__Service implements HttpFeature {
    private final RequestonControl requestCtrl;
    private final AdminEndpoint endpoint;

    @Injection.Inject
    AdminEndpoint__Service(RequestonControl requestCtrl, AdminEndpoint endpoint) {
        this.requestCtrl = requestCtrl;
        this.endpoint = endpoint;
    }

    @Override
    public void setup(HttpRouting.Builder routing) {
        routing.get("/admin", this::method1);
    }

    @Override
    public String socket() {
        return "admin";
    }

    private void method1(ServerRequest req, ServerResponse res) {
        Scope scope = requestCtrl.startRequestScope();

        try {
            scope.bind(ServerRequest__ServiceDescriptor.INSTANCE, req);
            scope.bind(ServerResponse__ServiceDescriptor.INSTANCE, res);
            scope.bind(Context__ServiceDescriptor.INSTANCE, req.context());

            Services requestScopeServices = scope.services();
            Supplier<SecurityContext> securityContextSupplier = requestScopeServices.get(SecurityContext.class);
            String hostHeader = requestScopeServices.get(Lookup.builder()
                                                                        .addContract(String.class)
                                                                        .addQualifier(Qualifier.builder()
                                                                                              .typeName(TypeName.create("io.helidon.http.Http.HeaderParam"))
                                                                                              .value("Host")
                                                                                              .build())
                                                                        .build())
                            .get();


            endpoint.admin();
        } finally {
            scope.close();
        }
    }

}

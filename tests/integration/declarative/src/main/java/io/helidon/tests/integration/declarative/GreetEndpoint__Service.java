package io.helidon.tests.integration.declarative;

import java.util.Map;
import java.util.function.Supplier;

import io.helidon.inject.RequestonControl;
import io.helidon.inject.Scope;
import io.helidon.inject.service.Injection;
import io.helidon.webserver.http.HttpFeature;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

@Injection.Singleton
class GreetEndpoint__Service implements HttpFeature {
    private final RequestonControl requestCtrl;
    private final Supplier<GreetEndpoint_greetNamed__Service> greetNamedMethod;

    @Injection.Inject
    GreetEndpoint__Service(RequestonControl requestCtrl,
                           Supplier<GreetEndpoint_greetNamed__Service> greetNamedMethod) {
        this.requestCtrl = requestCtrl;
        this.greetNamedMethod = greetNamedMethod;
    }

    @Override
    public void setup(HttpRouting.Builder routing) {
        routing.get("/admin", this::method1);
    }

    private void method1(ServerRequest req, ServerResponse res) {
        Scope scope = requestCtrl.startRequestScope(req.context().id(),
                                                    Map.of(ServerRequest__ServiceDescriptor.INSTANCE, req,
                                                           ServerResponse__ServiceDescriptor.INSTANCE, res,
                                                           Context__ServiceDescriptor.INSTANCE, req.context()));

        try {
            greetNamedMethod.get()
                    .invoke(req, res);
        } finally {
            scope.close();
        }
    }

}

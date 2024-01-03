package io.helidon.tests.integration.declarative;

import io.helidon.http.HeaderNames;
import io.helidon.http.Http;
import io.helidon.inject.service.Injection;
import io.helidon.security.SecurityContext;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

@Injection.Requeston
class GreetEndpoint_greetNamed__Service {
    private final GreetEndpoint helidonInject__endpoint;
    private final String name;
    private final boolean shouldThrow;
    private final String hostHeader;
    private final SecurityContext securityContext;

    @Injection.Inject
    GreetEndpoint_greetNamed__Service(GreetEndpoint helidonInject__endpoint,
                                      @Http.PathParam("name") String name,
                                      @Http.QueryParam(value = "throw", defaultValue = "false") boolean shouldThrow,
                                      @Http.HeaderParam(HeaderNames.HOST_STRING) String hostHeader,
                                      SecurityContext securityContext) {
        this.helidonInject__endpoint = helidonInject__endpoint;
        this.name = name;
        this.shouldThrow = shouldThrow;
        this.hostHeader = hostHeader;
        this.securityContext = securityContext;
    }

    void invoke(ServerRequest helidonInject__serverRequest,
                ServerResponse helidonInject__serverResponse) {

        String helidonInject__response = helidonInject__endpoint.greetNamed(name,
                                                                            shouldThrow,
                                                                            hostHeader,
                                                                            securityContext);

        helidonInject__serverResponse.send(helidonInject__response);
    }
}

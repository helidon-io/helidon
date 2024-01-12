package io.helidon.examples.inject.webserver;

import io.helidon.inject.service.Injection;
import io.helidon.webserver.http.HttpFeature;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

@Injection.Singleton
class DirectService implements HttpFeature {
    @Override
    public void setup(HttpRouting.Builder routing) {
        routing.register(this::routing);
    }

    public void routing(HttpRules rules) {
        rules.get("/direct", this::direct);
    }

    void direct(ServerRequest req, ServerResponse res) {
        res.send("Hello World!");
    }
}

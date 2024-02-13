package io.helidon.examples.webserver.serviceregistry;

import io.helidon.service.inject.api.Injection;
import io.helidon.webserver.http.HttpFeature;
import io.helidon.webserver.http.HttpRouting;

@Injection.Singleton
class HttpFeatureService implements HttpFeature {
    @Override
    public void setup(HttpRouting.Builder routing) {
        routing.get("/service", (req, res) -> res.send("Hello from service"));
    }
}

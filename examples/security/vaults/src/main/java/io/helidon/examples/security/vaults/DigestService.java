package io.helidon.examples.security.vaults;

import java.nio.charset.StandardCharsets;

import io.helidon.security.Security;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;

class DigestService implements Service {
    private final Security security;

    DigestService(Security security) {
        this.security = security;
    }

    @Override
    public void update(Routing.Rules rules) {
        rules.get("/digest/{config}/{text}", this::digest)
                .get("/verify/{config}/{text}/{digest:.*}", this::verify);
    }

    private void digest(ServerRequest req, ServerResponse res) {
        String configName = req.path().param("config");
        String text = req.path().param("text");

        security.digest(configName, text.getBytes(StandardCharsets.UTF_8))
                .forSingle(res::send)
                .exceptionally(res::send);
    }

    private void verify(ServerRequest req, ServerResponse res) {
        String configName = req.path().param("config");
        String text = req.path().param("text");
        String digest = req.path().param("digest");

        security.verifyDigest(configName, text.getBytes(StandardCharsets.UTF_8), digest)
                .map(it -> it ? "Valid" : "Invalid")
                .forSingle(res::send)
                .exceptionally(res::send);
    }
}

package io.helidon.examples.security.vaults;

import java.nio.charset.StandardCharsets;

import io.helidon.security.Security;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;

class EncryptionService implements Service {
    private final Security security;

    EncryptionService(Security security) {
        this.security = security;
    }

    @Override
    public void update(Routing.Rules rules) {
        rules.get("/encrypt/{config}/{text:.*}", this::encrypt)
                .get("/decrypt/{config}/{cipherText:.*}", this::decrypt);
    }

    private void encrypt(ServerRequest req, ServerResponse res) {
        String configName = req.path().param("config");
        String text = req.path().param("text");

        security.encrypt(configName, text.getBytes(StandardCharsets.UTF_8))
                .forSingle(res::send)
                .exceptionally(res::send);
    }

    private void decrypt(ServerRequest req, ServerResponse res) {
        String configName = req.path().param("config");
        String cipherText = req.path().param("cipherText");

        security.decrypt(configName, cipherText)
                .forSingle(res::send)
                .exceptionally(res::send);
    }
}

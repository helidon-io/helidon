package io.helidon.examples.security.vaults;

import io.helidon.security.Security;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;

class SecretsService implements Service {
    private final Security security;

    SecretsService(Security security) {
        this.security = security;
    }

    @Override
    public void update(Routing.Rules rules) {
        rules.get("/{name}", this::secret);
    }

    private void secret(ServerRequest req, ServerResponse res) {
        String secretName = req.path().param("name");
        security.secret(secretName, "default-" + secretName)
                .forSingle(res::send)
                .exceptionally(res::send);
    }
}

module helidon.tests.nimage.mp {
    requires jakarta.enterprise.cdi.api;
    requires java.ws.rs;
    requires io.helidon.security.annotations;
    requires io.helidon.security.abac.scope;
    requires java.annotation;
    requires microprofile.openapi.api;
    requires java.json;
    requires jakarta.inject.api;
    requires java.logging;
    requires microprofile.jwt.auth.api;
    requires microprofile.health.api;
    requires io.helidon.security.jwt;
    requires io.helidon.microprofile.server;
    requires microprofile.fault.tolerance.api;
    requires microprofile.rest.client.api;
    requires microprofile.metrics.api;
    requires java.json.bind;

    exports io.helidon.tests.integration.nativeimage.mp1;

    opens io.helidon.tests.integration.nativeimage.mp1 to weld.core.impl,hk2.utils;
}
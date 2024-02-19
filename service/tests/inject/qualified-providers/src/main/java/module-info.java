module io.helidon.service.tests.qualified.providers {
    requires io.helidon.service.registry;
    requires io.helidon.service.inject.api;
    // we use Application
    requires io.helidon.service.inject;
    requires io.helidon.http;

    exports io.helidon.service.tests.inject.qualified.providers;
}
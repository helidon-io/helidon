module io.helidon.service.tests.config.driven {
    requires io.helidon.builder.api;
    requires io.helidon.common.config;
    requires io.helidon.config;
    requires io.helidon.service.inject.api;

    // we use Application
    requires io.helidon.service.inject;
    requires io.helidon.http;

    exports io.helidon.service.tests.inject.configdriven;
}
module io.helidon.service.tests.codegen.test {
    exports io.helidon.service.tests.codegen;

    requires io.helidon.service.registry;
    requires io.helidon.service.inject;
    requires io.helidon.service.inject.api;
    requires io.helidon.service.codegen;
    requires io.helidon.config.metadata;

    requires hamcrest.all;
    requires org.junit.jupiter.api;

    opens io.helidon.service.tests.codegen to org.junit.platform.commons;
}
module io.helidon.service.tests.core {
    requires io.helidon.service.core;

    exports io.helidon.service.test.core;

    opens io.helidon.service.test.core to io.helidon.service.core;
}
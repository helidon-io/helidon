module helidon.tests.nimage.quickstartmp {
    requires java.logging;
    requires io.helidon.microprofile.bundle;

    exports io.helidon.tests.integration.nativeimage.mp3;

    opens io.helidon.tests.integration.nativeimage.mp3 to weld.core.impl,hk2.utils, io.helidon.microprofile.cdi;
}
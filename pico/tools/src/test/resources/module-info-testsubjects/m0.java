import io.helidon.pico.Application;
import io.helidon.pico.Module;
import io.helidon.pico.PicoServices;

module io.helidon.pico {

    requires transitive io.helidon.pico.api;
    requires static com.fasterxml.jackson.annotation;
    requires static lombok;
    requires io.helidon.common;

    exports io.helidon.pico.spi.impl;

    provides PicoServices with io.helidon.pico.spi.impl.DefaultPicoServices;

    uses Module;
    uses Application;
}

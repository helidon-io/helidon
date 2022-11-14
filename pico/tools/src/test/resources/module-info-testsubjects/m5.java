import io.helidon.pico.Module;

// @Generated({"provider=oracle", "generator=io.helidon.pico.tools.types.SimpleModuleDescriptor", "ver=1.0-SNAPSHOT"})
module unnamed {
    provides Module with io.helidon.pico.testsubjects.ext.tbox.pico.pico.picoModule;
    exports io.helidon.pico.spi;
    requires transitive io.helidon.pico;
}

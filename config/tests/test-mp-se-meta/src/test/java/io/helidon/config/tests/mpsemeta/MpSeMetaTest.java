package io.helidon.config.tests.mpsemeta;

import io.helidon.common.config.GlobalConfig;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalValue;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MpSeMetaTest {
    @Order(0)
    @Test
    public void testSeMeta() {
        // this should not fail
        io.helidon.config.Config config = io.helidon.config.Config.create();
        assertThat(config.get("helidon.app.value").asString().asOptional(),
                   optionalValue(is("app-value")));
    }

    @Order(1)
    @Test
    public void testMpMeta() {
        System.setProperty("io.helidon.config.mp.meta-config", "meta-config.yaml");
        Config config = ConfigProvider.getConfig();
        assertThat(config.getValue("helidon.test.value", String.class), is("value"));

        assertThat(GlobalConfig.config()
                           .get("helidon.test.value")
                           .asString()
                           .asOptional(), optionalValue(is("value")));
    }
}

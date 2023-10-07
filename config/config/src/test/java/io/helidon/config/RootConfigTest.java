package io.helidon.config;

import java.io.IOException;
import java.io.StringReader;
import java.util.Properties;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalValue;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class RootConfigTest {
    private static Config config;

    @BeforeAll
    static void createConfig() throws IOException {
        Properties properties = new Properties();
        properties.load(new StringReader("""
                                                 server.host=localhost
                                                 server.port=8080
                                                 server.tls.enabled=true
                                                 """));
        config = Config.builder()
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .addSource(ConfigSources.create(properties))
                .build();
    }

    @Test
    void testRootFromRoot() {
        Config root = config.root();
        assertThat(root.get("server.host").asString().asOptional(), optionalValue(is("localhost")));
    }

    @Test
    void testRootFromNested() {
        Config root = config.get("server").root();
        assertThat(root.get("server.host").asString().asOptional(), optionalValue(is("localhost")));
    }

    @Test
    void testRootFromDetached() {
        Config root = config.get("server").detach().root();
        assertThat(root.get("host").asString().asOptional(), optionalValue(is("localhost")));
    }
}

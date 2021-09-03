/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.helidon.microprofile.server;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.config.testing.OptionalMatcher.empty;
import static io.helidon.config.testing.OptionalMatcher.value;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit test for {@link io.helidon.microprofile.server.JaxRsCdiExtension}.
 */
public class JaxRsCdiExtensionTest {
    private static JaxRsCdiExtension extension;
    private static final Config EMPTY_CONFIG = Config.empty();

    @BeforeAll
    static void initClass() {
        extension = new JaxRsCdiExtension();
    }

    @Test
    void testRoutingRequired() {
        JaxRsApplication app = JaxRsApplication.create(TestApp.class);

        boolean required = extension.isNamedRoutingRequired(EMPTY_CONFIG, app);
        assertThat("Routing name is required by annotation", required, is(true));
    }

    @Test
    void testRoutingNotRequired() {
        JaxRsApplication app = JaxRsApplication.builder()
                .application(TestApp.class)
                .routingNameRequired(false)
                .build();

        boolean required = extension.isNamedRoutingRequired(EMPTY_CONFIG, app);
        assertThat("Routing name required is overridden by builder", required, is(false));
    }

    @Test
    void testRoutingNotRequiredConfig() {
        JaxRsApplication app = JaxRsApplication.create(TestApp.class);

        Config config = Config
                .create(ConfigSources.create(Map.of(TestApp.class.getName() + "." + RoutingName.CONFIG_KEY_REQUIRED, "false")));

        boolean required = extension.isNamedRoutingRequired(config, app);
        assertThat("Routing name required is overridden by config", required, is(false));
    }

    @Test
    void testRouting() {
        JaxRsApplication app = JaxRsApplication.create(TestApp.class);

        Optional<String> namedRouting = extension.findNamedRouting(EMPTY_CONFIG, app);
        assertThat(namedRouting, value(is("admin")));
    }

    @Test
    void testRoutingNone() {
        JaxRsApplication app = JaxRsApplication.builder()
                .application(TestApp.class)
                .routingName(RoutingName.DEFAULT_NAME)
                .build();

        Optional<String> namedRouting = extension.findNamedRouting(EMPTY_CONFIG, app);
        assertThat(namedRouting, empty());
    }

    @Test
    void testRoutingOverrideByConfig() {
        JaxRsApplication app = JaxRsApplication.create(TestApp.class);

        Config config = Config
                .create(ConfigSources.create(Map.of(TestApp.class.getName() + "." + RoutingName.CONFIG_KEY_NAME, "config")));
        Optional<String> namedRouting = extension.findNamedRouting(config, app);
        assertThat(namedRouting, value(is("config")));

        config = Config
                .create(ConfigSources.create(Map.of(TestApp.class.getName() + "." + RoutingName.CONFIG_KEY_NAME,
                                                    RoutingName.DEFAULT_NAME)));
        namedRouting = extension.findNamedRouting(config, app);
        assertThat(namedRouting, empty());
    }

    @Test
    void testContextRootOverrideByConfig() {
        JaxRsApplication app = JaxRsApplication.create(TestApp.class);

        Optional<String> contextRoot = extension.findContextRoot(EMPTY_CONFIG, app);
        assertThat(contextRoot, value(is("/wrong")));

        Config config = Config
                .create(ConfigSources.create(Map.of(TestApp.class.getName() + "." + RoutingPath.CONFIG_KEY_PATH, "config")));
        contextRoot = extension.findContextRoot(config, app);
        assertThat(contextRoot, value(is("/config")));
    }

    @Test
    void testContextRootNoConfigGoodPath() {
        JaxRsApplication app = JaxRsApplication.builder()
                .contextRoot("/myApp")
                .build();

        Optional<String> contextRoot = extension.findContextRoot(EMPTY_CONFIG, app);
        assertThat(contextRoot, value(is("/myApp")));
    }

    @Test
    void testContextRootNone() {
        JaxRsApplication app = JaxRsApplication.builder()
                .build();

        Optional<String> contextRoot = extension.findContextRoot(EMPTY_CONFIG, app);
        assertThat(contextRoot, empty());
    }

    @Test
    void testContextRootNoConfigNoLeadingSlash() {
        JaxRsApplication app = JaxRsApplication.builder()
                .contextRoot("myApp")
                .build();

        Optional<String> contextRoot = extension.findContextRoot(EMPTY_CONFIG, app);
        assertThat(contextRoot, value(is("/myApp")));
    }

    @Test
    void testContextRootNoConfigWithTrailingSlash() {
        JaxRsApplication app = JaxRsApplication.builder()
                .contextRoot("/myApp/")
                .build();

        Optional<String> contextRoot = extension.findContextRoot(EMPTY_CONFIG, app);
        assertThat(contextRoot, value(is("/myApp")));
    }

    @Test
    void testAppsNotModifiableAfterUse() {
        JaxRsCdiExtension ext = new JaxRsCdiExtension();
        ext.fixApps(new Object());

        assertThrows(IllegalStateException.class, () -> ext.addApplication(new JaxRsApplicationTest.MyApplication()));
        assertThrows(IllegalStateException.class, () -> ext.addApplication("/", new JaxRsApplicationTest.MyApplication()));
        assertThrows(IllegalStateException.class, () -> ext.addApplications(List.of()));
        assertThrows(IllegalStateException.class, () -> ext.addResourceClasses(List.of()));
        assertThrows(IllegalStateException.class, () -> ext.addSyntheticApplication(List.of()));
        assertThrows(IllegalStateException.class, ext::removeApplications);
        assertThrows(IllegalStateException.class, ext::removeResourceClasses);
    }
}

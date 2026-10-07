/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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
package io.helidon.webserver.observe.tracing;

import java.util.Optional;

import io.helidon.config.metadata.model.CmModel;
import io.helidon.config.metadata.model.CmModel.CmOption;
import io.helidon.config.metadata.model.CmModel.CmType;
import io.helidon.config.metadata.model.CmResolver;
import io.helidon.tracing.config.ComponentTracingConfig;
import io.helidon.tracing.config.SpanLogTracingConfig;
import io.helidon.tracing.config.SpanTracingConfig;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;

class TracingConfigMetadataTest {
    @Test
    void testMergedTracingOptions() {
        var resolver = CmResolver.create(CmModel.loadAll(getClass().getClassLoader()));
        var observer = resolver.type(TracingObserver.class.getName()).orElseThrow();
        assertThat(observer.options().stream().map(option -> option.key().orElseThrow()).toList(),
                   containsInAnyOrder("components", "enabled", "paths", "weight", "wait-tracing-enabled"));

        var components = option(observer, "components");
        assertThat(components.kind(), is(CmOption.Kind.MAP));
        assertThat(components.typeName(), is(ComponentTracingConfig.class.getName()));
        assertThat(option(observer, "enabled").defaultValue(), is(Optional.of("true")));

        var component = resolver.type(components.typeName()).orElseThrow();
        assertThat(component.options().stream().map(option -> option.key().orElseThrow()).toList(),
                   containsInAnyOrder("enabled", "spans"));
        assertThat(option(component, "enabled").defaultValue(), is(Optional.of("true")));
        var spans = option(component, "spans");
        assertThat(spans.kind(), is(CmOption.Kind.LIST));
        assertThat(spans.typeName(), is(SpanTracingConfig.class.getName()));

        var span = resolver.type(spans.typeName()).orElseThrow();
        assertThat(span.options().stream().map(option -> option.key().orElseThrow()).toList(),
                   containsInAnyOrder("name", "enabled", "new-name", "logs"));
        assertThat(option(span, "name").required(), is(true));
        assertThat(option(span, "enabled").defaultValue(), is(Optional.of("true")));
        assertThat(option(span, "new-name").required(), is(false));
        assertThat(option(span, "new-name").defaultValue(), is(Optional.empty()));
        var logs = option(span, "logs");
        assertThat(logs.kind(), is(CmOption.Kind.LIST));
        assertThat(logs.typeName(), is(SpanLogTracingConfig.class.getName()));

        var log = resolver.type(logs.typeName()).orElseThrow();
        assertThat(log.options().stream().map(option -> option.key().orElseThrow()).toList(),
                   containsInAnyOrder("name", "enabled"));
        assertThat(option(log, "name").required(), is(true));
        assertThat(option(log, "enabled").defaultValue(), is(Optional.of("true")));
    }

    private static CmOption option(CmType type, String key) {
        return type.options().stream()
                .filter(option -> option.key().filter(key::equals).isPresent())
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing option " + key + " in " + type.typeName()));
    }
}

/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.discovery.providers.eureka;

import java.io.IOException;
import java.util.Map;
import java.util.SequencedSet;

import io.helidon.discovery.providers.eureka.EurekaDiscoveryImpl.Instance;
import io.helidon.discovery.providers.eureka.EurekaDiscoveryImpl.Instance.Status;

import jakarta.json.JsonArray;
import jakarta.json.JsonReaderFactory;

import org.junit.jupiter.api.Test;

import static io.helidon.discovery.providers.eureka.EurekaDiscoveryImpl.Instance.Status.UP;
import static jakarta.json.Json.createReaderFactory;
import static java.time.temporal.ChronoUnit.SECONDS;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestJsonParsing {

    private static JsonReaderFactory jrf = createReaderFactory(Map.of());

    private TestJsonParsing() {
        super();
    }

    @Test
    void testApps() throws IOException {
        Map<String, SequencedSet<Instance>> cache =
            EurekaDiscoveryImpl.instancesMap(jrf.createReader(this.getClass().getResource("/apps.json").openStream())
                                             .readObject()
                                             .getJsonObject("applications")
                                             .getJsonArray("application"),
                                             false);
        assertThat(cache.size(), is(1));
        assertThat(cache.containsKey("EXAMPLE"), is(true));
        assertThrows(UnsupportedOperationException.class, cache::clear);
        SequencedSet<Instance> instances = cache.get("EXAMPLE");
        assertThat(instances.size(), is(1));
        assertThrows(UnsupportedOperationException.class, instances::clear);
        Instance i = instances.getFirst();
        assertThat(i.id(), is("localhost:80"));
        assertThat(i.host(), is("localhost"));
        assertThat(i.securePort() < 0, is(true));
        assertThat(i.nonSecurePort(), is(80));
        assertThat(i.status(), is(UP));
        Map<String, String> md = i.metadata();
        assertThat(md.toString(), md.size(), is(2)); // 2 because of @class (!) and io.helidon.discovery.status
        assertThat(md.toString(), md.get("@class"), is("java.util.Collections$EmptyMap")); // !
        assertThat(md.toString(), md.get("io.helidon.discovery.status"), is("UP")); // added by EurekaDiscoveryImpl
    }

    @Test
    void testAppsEXAMPLE() throws IOException {
        SequencedSet<Instance> instances =
            EurekaDiscoveryImpl.instances(jrf.createReader(this.getClass().getResource("/apps-EXAMPLE.json").openStream())
                                          .readObject()
                                          .getJsonObject("application")
                                          .getJsonArray("instance"),
                                          true); // prefer IP address, just for kicks to test this path too
        assertThat(instances.size(), is(1));
        Instance i = instances.getFirst();
        assertThat(i.id(), is("localhost:80")); // the id was set on registration; it's just an opaque string
        assertThat(i.host(), is("127.0.0.1")); // preferIpAddress was true
        assertThat(i.securePort() < 0, is(true));
        assertThat(i.nonSecurePort(), is(80));
        assertThat(i.status(), is(UP));
        Map<String, String> md = i.metadata();
        assertThat(md.toString(), md.size(), is(2)); // 2 because of @class (!) and io.helidon.discovery.status
        assertThat(md.toString(), md.get("@class"), is("java.util.Collections$EmptyMap")); // !
        assertThat(md.toString(), md.get("io.helidon.discovery.status"), is("UP")); // added by EurekaDiscoveryImpl
    }

    @Test
    void testAppsDelta() throws IOException {
        JsonArray applications = jrf.createReader(this.getClass().getResource("/apps.json").openStream())
            .readObject()
            .getJsonObject("applications")
            .getJsonArray("application");
        assertThat(applications.size(), is(1));
        
        Map<String, SequencedSet<Instance>> cache = EurekaDiscoveryImpl.instancesMap(applications, false);
        assertThat(cache.size(), is(1));

        // Now pretend that a deletion happened.
        JsonArray changes = jrf.createReader(this.getClass().getResource("/apps-delta-deleted.json").openStream())
            .readObject()
            .getJsonObject("applications")
            .getJsonArray("application");
        assertThat(changes.size(), is(1));

        // Apply the changes.
        cache = EurekaDiscoveryImpl.change(cache, changes, false);

        // The only change was a deletion, note that applying the changes deleted the sole entry.
        assertThat(cache.isEmpty(), is(true));
    }

}

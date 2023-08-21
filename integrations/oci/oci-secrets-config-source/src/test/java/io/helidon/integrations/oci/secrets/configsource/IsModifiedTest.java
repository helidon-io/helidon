/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.integrations.oci.secrets.configsource;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import static io.helidon.integrations.oci.secrets.configsource.OciSecretsConfigSourceProvider.SecretBundleConfigSource.isModified;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class IsModifiedTest {

    @Test
    void testIsModified() {
        // Test java.time behavior.
        Instant now0 = Instant.now();
        Instant now1 = Instant.from(now0);
        assertThat(now1, is(now0));
        Instant later = now0.plusSeconds(500); // arbitrary amount
        assertThat(later.isAfter(now0), is(true));
        Instant earlier = now0.minusSeconds(500); // arbitrary amount
        assertThat(earlier.isBefore(now0), is(true));

        // Test that isModified properly encapsulates java.time behavior.
        assertThat(isModified(now0, later), is(false));
        assertThat(isModified(now0, now1), is(false));
        assertThat(isModified(now0, earlier), is(true));
    }

}


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

import java.util.Base64;

import org.junit.jupiter.api.Test;

import static io.helidon.integrations.oci.secrets.configsource.AbstractSecretBundleConfigSource.valueNode;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class ValueNodeTest {

    @Test
    void testValueNode() {
      // Test the JDK's base64 decoding behavior.
      String raw = new String("abc".getBytes(), UTF_8);
      byte[] bytes = Base64.getEncoder().encode(raw.getBytes());
      String encoded = new String(bytes, UTF_8);
      Base64.Decoder decoder = Base64.getDecoder();
      bytes = decoder.decode(encoded);
      String decoded = new String(bytes, UTF_8);
      assertThat(decoded, is(raw));

      // Test that valueNode properly encapsulates this platform behavior.
      assertThat(valueNode(encoded, decoder).get(), is(decoded));
    }

}


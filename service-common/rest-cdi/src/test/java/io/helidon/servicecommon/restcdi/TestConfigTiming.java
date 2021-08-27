/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
package io.helidon.servicecommon.restcdi;

import io.helidon.microprofile.tests.junit5.AddBean;
import io.helidon.microprofile.tests.junit5.AddConfig;
import io.helidon.microprofile.tests.junit5.HelidonTest;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

@HelidonTest
@AddBean(TestResource.class)
@AddBean(TestSupportProducer.class)

// Acts as runtime config, overriding the build-time setting in microprofile-config.properties
@AddConfig(key = "test.importance", value = "3")

public class TestConfigTiming {

    @Inject
    private WebTarget webTarget;

    @Test
    void checkImportance() {
        String response = webTarget
                .path("/test")
                .request(MediaType.TEXT_PLAIN_TYPE)
                .get(String.class);

        assertThat("Returned importance", response, is(equalTo("3")));
    }
}

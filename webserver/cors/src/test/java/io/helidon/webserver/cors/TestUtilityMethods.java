/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
 *
 */
package io.helidon.webserver.cors;

import org.junit.jupiter.api.Test;

import static io.helidon.webserver.cors.CorsSupportHelper.normalize;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isEmptyString;

public class TestUtilityMethods {

    @Test
    public void testNormalize() {
        assertThat(normalize("something"), is("something"));
        assertThat(normalize("/something"), is("something"));
        assertThat(normalize("something/"), is("something"));
        assertThat(normalize("/something/"), is("something"));
        assertThat(normalize("/"), isEmptyString());
        assertThat(normalize(""), isEmptyString());
    }
}

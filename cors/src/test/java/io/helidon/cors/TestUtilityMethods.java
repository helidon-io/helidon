/*
 * Copyright (c) 2020, 2022 Oracle and/or its affiliates.
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
package io.helidon.cors;

import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isEmptyString;

public class TestUtilityMethods {

    @Test
    public void testNormalize() {
        MatcherAssert.assertThat(CorsSupportHelper.normalize("something"), is("something"));
        MatcherAssert.assertThat(CorsSupportHelper.normalize("/something"), is("something"));
        MatcherAssert.assertThat(CorsSupportHelper.normalize("something/"), is("something"));
        MatcherAssert.assertThat(CorsSupportHelper.normalize("/something/"), is("something"));
        MatcherAssert.assertThat(CorsSupportHelper.normalize("/"), isEmptyString());
        MatcherAssert.assertThat(CorsSupportHelper.normalize(""), isEmptyString());
    }
}

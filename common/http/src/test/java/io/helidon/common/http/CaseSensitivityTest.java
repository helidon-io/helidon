/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
package io.helidon.common.http;

import org.junit.jupiter.api.Test;

import static io.helidon.config.testing.CaseSensitivityTester.testMap;
import static io.helidon.config.testing.CaseSensitivityTester.testStrict;

class CaseSensitivityTest  {

    @Test
    void testFormParamsImpl() {
        FormParams.Builder builder = FormParams.builder();
        testMap().forEach((k, v) -> builder.add(k, v.toArray(new String[0])));
        testStrict(builder.build(), FormParams::all);
    }

    @Test
    void testHashParameters() {
        testStrict(HashParameters.create(testMap()), HashParameters::all);
    }

    @Test
    void testReadOnlyParameters() {
        testStrict(new ReadOnlyParameters(testMap()), ReadOnlyParameters::all);
    }

    @Test
    void testUnmodifiableParameters() {
        testStrict(new UnmodifiableParameters(new ReadOnlyParameters(testMap())), UnmodifiableParameters::all);
    }
}
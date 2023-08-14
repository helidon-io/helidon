/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

import io.helidon.builder.test.testsubjects.ProviderNoImpls;
import io.helidon.builder.test.testsubjects.SomeProvider;
import io.helidon.builder.test.testsubjects.SomeServiceProvider1;
import io.helidon.builder.test.testsubjects.SomeServiceProvider2;

/**
 * Helidon Builder Test module.
 */
module io.helidon.builder.test.builder {
    requires static jakarta.annotation;
    requires static com.fasterxml.jackson.annotation;
    requires static io.helidon.config.metadata;

    requires io.helidon.common;
    requires io.helidon.common.config;
    requires io.helidon.builder.api;

    exports io.helidon.builder.test.testsubjects;

    uses SomeProvider;
    uses ProviderNoImpls;

    provides SomeProvider with SomeServiceProvider1, SomeServiceProvider2;
}

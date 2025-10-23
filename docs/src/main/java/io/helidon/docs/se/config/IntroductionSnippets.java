/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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
package io.helidon.docs.se.config;

import io.helidon.config.Config;
import io.helidon.service.registry.Services;

@SuppressWarnings("ALL")
class IntroductionSnippets {

    void snippet_1() {
        // tag::snippet_1[]
        Config config = Services.get(Config.class);
        // end::snippet_1[]
    }

    void snippet_2() {
        // tag::snippet_2[]
        Config config = Config.global();
        // end::snippet_2[]
    }

    void snippet_3() {
        Config config = Config.create();
        // tag::snippet_3[]
        Services.set(Config.class, config);
        // end::snippet_3[]
    }

    void snippet_4(Config config) {
        // tag::snippet_4[]
        int pageSize = config.get("web.page-size")
                .asInt()
                .orElse(20);
        // end::snippet_4[]
    }


    void snippet_5(Config config) {
        // tag::snippet_5[]
        int pageSize = config
                .get("web")
                .get("page-size")
                .asInt()
                .orElse(20);
        // end::snippet_5[]
    }

}

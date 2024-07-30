/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

import java.util.List;

import io.helidon.config.Config;

@SuppressWarnings("ALL")
class HierarchicalFeaturesSnippets {

    void snippet_1(Config config) {
        // tag::snippet_1[]
        config.get("key.does.not.exist")
        // end::snippet_1[]
        ;
    }

    void snippet_2(Config config) {
        // tag::snippet_2[]
        assert config.get("") == config;
        Config provName1 = config.get("data.providers.0.name"); // <1>
        Config provName2 = config.get("data.providers.0").get("name"); // <2>
        Config provName3 = config.get("data.providers").get("0.name");
        Config provName4 = config.get("data").get("providers.0").get("name");
        Config provName5 = config.get("data").get("providers").get("0").get("name"); // <3>
        // end::snippet_2[]
    }

    void snippet_3(Config config) {
        // tag::snippet_3[]
        List<String> appNodeNames = config.get("app")
                .asNodeList() // <1>
                .map(nodes -> { // <2>
                    return nodes
                            .stream()
                            .map(Config::name)
                            .sorted()
                            .toList();
                })
                .orElse(List.of()); // <3>

        assert appNodeNames.get(0).equals("basic-range"); // <4>
        assert appNodeNames.get(1).equals("greeting"); // <4>
        assert appNodeNames.get(2).equals("page-size"); // <4>
        // end::snippet_3[]
    }

    void snippet_4(Config config) {
        // tag::snippet_4[]
        List<Config> providers = config.get("data.providers")
                .asNodeList().orElse(List.of()); // <1>

        assert providers.get(0).key().toString().equals("data.providers.0"); // <2>
        assert providers.get(1).key().toString().equals("data.providers.1"); // <2>
        // end::snippet_4[]
    }

    void snippet_5(Config config) {
        // tag::snippet_5[]
        config.get("data.providers")
                .traverse() // <1>
                .forEach(node -> System.out.println(node.type() + " \t" + node.key())); // <2>
        // end::snippet_5[]
    }

    void snippet_6(Config config) {
        // tag::snippet_6[]
        config.traverse(node -> !node.name().equals("data")) // <1>
                .forEach(node -> System.out.println(node.type() + " \t" + node.key())); // <2>
        // end::snippet_6[]
    }

    void snippet_7(Config alternateRoot, Config originalRoot) {
        // tag::snippet_7[]
        // originalRoot is from the original example `.conf` file
        // alternateRoot is from the alternate structure above

        Config detachedFromOriginal = originalRoot.get("web").detach();
        Config detachedFromAlternate = alternateRoot.get("server.web").detach();

        assert originalRoot.get("web.debug").equals("true"); // <1>
        assert alternateRoot.get("server.web.debug").equals("true"); // <1>

        assert detachedFromOriginal.get("debug").equals("true"); // <2>
        assert detachedFromAlternate.get("debug").equals("true"); // <2>
        // end::snippet_7[]
    }

}

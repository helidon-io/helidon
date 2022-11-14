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

package io.helidon.pico.config.testsubjects;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.helidon.pico.builder.api.ConfiguredOption;
import io.helidon.pico.builder.Singular;
import io.helidon.pico.config.api.ConfigBean;

@ConfigBean(drivesActivation = false)
//@Builder(mapImplType = TreeMap.class)
public interface SchemaRequiredConfig {

    // implicitly will map to key = "port"
    @ConfiguredOption(required = true)
    int getPort();

    @ConfiguredOption(value = "-1", key = "port-integer")
    Optional<Integer> getOptionalPort();

    // implicitly will map to key = "port-integer"
    @ConfiguredOption(required = true)
    Integer getPortInteger();

    // implicitly will map to key = "[.n.]my-simple-config"
    @ConfiguredOption(required = true)
    MySimpleConfig mySimpleConfig();

    @ConfiguredOption(required = true, key = "my-simple-config")
    @Singular
    Set<MySimpleConfig> mySimpleConfigSet();

    @ConfiguredOption(required = true, key = "my-simple-config")
    @Singular
    List<MySimpleConfig> mySimpleConfigList();

    @ConfiguredOption(required = true, key = "my-simple-config")
    @Singular
    Map<String, MySimpleConfig> mySimpleConfigMap();

    // 1. this should default if not present, and 2. there should never be allowed more than 1 due to the policy on it
    @ConfiguredOption(required = true, key = "my-singleton-config")
    @Singular
    Set<ASingletonConfig> mySingletonConfigSet();

    // implicitly will map to key = "[.n.]my-singleton-config"
    @ConfiguredOption(required = true, key = "my-singleton-config")
    Optional<ASingletonConfig> getMySingletonConfig();

    @ConfiguredOption(key = "strings")
    String[] arrayOfStrings();

    // TODO:
//    @ConfiguredOption(key = "strings")
//    @Singular
//    List<String> listOfStrings();

    // TODO:
//    @ConfiguredOption(key = "strings")
//    @Singular
//    Set<String> setOfStrings();

    // TODO:
//    @Singular
//    Map<Integer, String> mapOfIntegerToStrings();

}

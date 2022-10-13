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

package io.helidon.pico.builder.test.testsubjects;

import java.util.List;
import java.util.Map;

import io.helidon.config.metadata.ConfiguredOption;
import io.helidon.pico.builder.api.Builder;
import io.helidon.pico.builder.api.Singular;

@Builder(requireBeanStyle = true, implPrefix = "", implSuffix = "Impl")
public interface Level2 extends Level1 {

    @Override
    @ConfiguredOption("2")
    String getLevel0StringAttribute();

    @Singular
    List<Level0> getLevel2Level0Info();

    @Singular("Level0")
    List<Level0> getLevel2ListOfLevel0s();

    @Singular("stringToLevel1")
    Map<String, Level1> getLevel2MapOfStringToLevel1s();

}

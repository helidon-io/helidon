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

import io.helidon.pico.builder.test.testsubjects.impl.DefaultCustomNamed;

public class DependsOnAllBuilders {

    public static CustomNamed createCustomNamed() {
        return DefaultCustomNamed.builder().build();
    }

    public static Level2 createLevel2() {
        return Level2Impl.builder().build();
    }

    public static MyConfigBean createMyConfigBean() {
        return MyConfigBeanImpl.builder().build();
    }
}

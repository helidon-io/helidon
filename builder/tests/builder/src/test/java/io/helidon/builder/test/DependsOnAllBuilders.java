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

package io.helidon.builder.test;

import io.helidon.builder.test.testsubjects.CustomNamed;
import io.helidon.builder.test.testsubjects.Level2;
import io.helidon.builder.test.testsubjects.Level2Impl;
import io.helidon.builder.test.testsubjects.MyConfigBean;
import io.helidon.builder.test.testsubjects.MyConfigBeanImpl;
import io.helidon.builder.test.testsubjects.impl.DefaultCustomNamed;

class DependsOnAllBuilders {

    static CustomNamed createCustomNamed() {
        return DefaultCustomNamed.builder().build();
    }

    static Level2 createLevel2() {
        return Level2Impl.builder().build();
    }

    static MyConfigBean createMyConfigBean() {
        return MyConfigBeanImpl.builder().name("test").build();
    }

}

/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.config.tests.mappers2;

import io.helidon.config.Config;

/**
 * Same test as {@link DifferentIntMapperServicesEnabledTest} with {@link Config.Builder#disableMapperServices()},
 * i.e. it gets original integer value ({@value #CONFIGURED_VALUE}).
 */
public class DifferentIntMapperServicesDisabledTest extends AbstractDifferentIntMapperServicesTest {

    protected Config.Builder configBuilder() {
        return super.configBuilder()
                .disableMapperServices();
    }

    protected int expected() {
        return CONFIGURED_INT;
    }

}

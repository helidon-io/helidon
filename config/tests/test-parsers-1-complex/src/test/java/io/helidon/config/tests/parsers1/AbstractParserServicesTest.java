/*
 * Copyright (c) 2017, 2022 Oracle and/or its affiliates.
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

package io.helidon.config.tests.parsers1;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

/**
 * Common Parser tests.
 */
public abstract class AbstractParserServicesTest {

    protected static final String KEY = "text-text";
    protected static final String VALUE = "string value";

    protected Config.Builder configBuilder() {
        return Config.builder()
                .sources(ConfigSources.create(KEY + "=" + VALUE, MediaTypes.create("text/x-java-properties")));
    }

}

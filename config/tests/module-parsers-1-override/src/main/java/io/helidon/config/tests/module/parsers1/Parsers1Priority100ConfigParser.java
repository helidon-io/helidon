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

package io.helidon.config.tests.module.parsers1;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.config.spi.ConfigNode;
import io.helidon.config.spi.ConfigNode.ValueNode;

/**
 * Testing implementation of {@code text/x-java-properties} media type
 * with Weight {@code 100}.
 */
@Weight(Weighted.DEFAULT_WEIGHT)
public class Parsers1Priority100ConfigParser extends AbstractParsers1ConfigParser {

    /**
     * Prefix used with each key.
     */
    public static final String KEY_PREFIX = "p100.";

    @Override
    protected void addValue(ConfigNode.ObjectNode.Builder rootBuilder, String key, ValueNode value) {
        super.addValue(rootBuilder, KEY_PREFIX + key, value);
    }

}

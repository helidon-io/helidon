/*
 * Copyright (c) 2017, 2021 Oracle and/or its affiliates.
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

package io.helidon.config.tests;

import io.helidon.config.Config;
import io.helidon.config.spi.ConfigFilter;

/**
 * Provides possibility to decrypt passwords from configuration sources.
 * Configuration can be used to enforce encryption (e.g. we will fail on clear-text value).
 */
public class SecurityConfigFilter implements ConfigFilter {
    @Override
    public String apply(Config.Key key, String stringValue) {
        if ("${AES=thisIsEncriptedPassphrase}".equals(stringValue)) {
            return "Password1.";
        } else {
            return stringValue;
        }
    }
}

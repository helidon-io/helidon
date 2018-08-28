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

package io.helidon.config;

import java.util.function.Supplier;

/**
 * Exception representing a specific failures related to a missing configuration value.
 */
public class MissingValueException extends ConfigException {

    private static final long serialVersionUID = 1L;

    /**
     * Create new missing value exception.
     *
     * @param key configuration key associated with the expected value.
     */
    private MissingValueException(Config.Key key) {
        super("Requested value for configuration key '" + key + "' is not present in the configuration.");
    }

    /**
     * Create new missing value exception.
     *
     * @param key configuration key associated with the expected value.
     * @return new missing value exception associated with a given key.
     */
    public static MissingValueException forKey(Config.Key key) {
        return new MissingValueException(key);
    }

    /**
     * Create new missing value exception supplier.
     *
     * @param key configuration key associated with the expected value.
     * @return new supplier of a missing value exception associated with a given key.
     */
    public static Supplier<MissingValueException> supplierForKey(Config.Key key) {
        return () -> new MissingValueException(key);
    }
}

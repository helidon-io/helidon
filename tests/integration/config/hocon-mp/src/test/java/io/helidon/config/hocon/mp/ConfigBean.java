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

package io.helidon.config.hocon.mp;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.inject.Inject;

public class ConfigBean {
    // Main Hocon config file properties
    @Inject
    @ConfigProperty(name = "hocon.string")
    public String hocon_string;

    @Inject
    @ConfigProperty(name = "hocon.number")
    public int hocon_number;

    @Inject
    @ConfigProperty(name = "hocon.array.0")
    public String hocon_array_0;

    @Inject
    @ConfigProperty(name = "hocon.array.1")
    public String hocon_array_1;

    @Inject
    @ConfigProperty(name = "hocon.array.2")
    public String hocon_array_2;

    @Inject
    @ConfigProperty(name = "hocon.boolean")
    public boolean hocon_boolean;

    // Include properties
    @Inject
    @ConfigProperty(name = "hocon_include.string")
    public String hocon_include_string;

    @Inject
    @ConfigProperty(name = "hocon_include.number")
    public int hocon_include_number;

    @Inject
    @ConfigProperty(name = "hocon_include.array.0")
    public String hocon_include_array_0;

    @Inject
    @ConfigProperty(name = "hocon_include.array.1")
    public String hocon_include_array_1;

    @Inject
    @ConfigProperty(name = "hocon_include.array.2")
    public String hocon_include_array_2;

    @Inject
    @ConfigProperty(name = "hocon_include.boolean")
    public boolean hocon_include_boolean;

    // Json config file properties
    @Inject
    @ConfigProperty(name = "json.string")
    public String json_string;

    @Inject
    @ConfigProperty(name = "json.number")
    public int json_number;

    @Inject
    @ConfigProperty(name = "json.array.0")
    public String json_array_0;

    @Inject
    @ConfigProperty(name = "json.array.1")
    public String json_array_1;

    @Inject
    @ConfigProperty(name = "json.array.2")
    public String json_array_2;

    @Inject
    @ConfigProperty(name = "json.boolean")
    public boolean json_boolean;
}

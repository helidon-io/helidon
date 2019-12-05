/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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

/**
 * Common tools that use config component.
 *
 * @see io.helidon.common.configurable.Resource
 */
module io.helidon.common.configurable {
    requires java.logging;
    requires java.management;
    requires transitive io.helidon.config;
    requires io.helidon.common;
    requires io.helidon.common.context;
    exports io.helidon.common.configurable;
}

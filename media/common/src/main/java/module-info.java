/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates. All rights reserved.
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

import io.helidon.media.common.spi.MediaSupportProvider;

/**
 * Media type support common classes.
 *
 * @see io.helidon.media.common.ContentReaders
 * @see io.helidon.media.common.ContentWriters
 */
module io.helidon.media.common {
    requires java.logging;
    requires io.helidon.common.mapper;
    requires io.helidon.common.reactive;
    requires io.helidon.common.http;
    requires io.helidon.config;

    exports io.helidon.media.common;
    exports io.helidon.media.common.spi;

    uses MediaSupportProvider;
}

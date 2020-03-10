/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.dbclient.common.mapper;

import java.sql.Time;
import java.time.LocalTime;

import io.helidon.common.mapper.Mapper;

/**
 * Maps {@link java.sql.Time} to {@link java.time.LocaleTime}.
 */
public class SqlTimeToLocalTime implements Mapper<Time, LocalTime> {

    @Override
    public LocalTime map(Time source) {
        return source != null ? source.toLocalTime() : null;
    }

}

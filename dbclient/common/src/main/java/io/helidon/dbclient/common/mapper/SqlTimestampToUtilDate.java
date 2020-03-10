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

import java.sql.Timestamp;
import java.util.Date;

import io.helidon.common.mapper.Mapper;

/**
 * Maps {@link java.sql.Timestamp} to {@link java.util.Date} with zone set to UTC.
 */
public class SqlTimestampToUtilDate implements Mapper<Timestamp, Date> {

    @Override
    public Date map(Timestamp source) {
        return source;
    }

}

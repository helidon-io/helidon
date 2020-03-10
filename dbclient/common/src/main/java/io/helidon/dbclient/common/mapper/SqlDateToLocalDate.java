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

import java.sql.Date;
import java.time.LocalDate;

import io.helidon.common.mapper.Mapper;

/**
 * Maps {@link java.sql.Date} to {@link java.time.LocalDate} with zone set to UTC.
 */
public class SqlDateToLocalDate implements Mapper<Date, LocalDate> {

    @Override
    public LocalDate map(Date source) {
       return source != null ? source.toLocalDate() : null;
    }

}

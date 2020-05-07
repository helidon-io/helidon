/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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

package io.helidon.microprofile.graphql.server;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Defines a value that has been formatted with a format and {@link Locale}.
 */
public interface FormattedNumber {
    /**
     * Return the {@link NumberFormat} to format with.
     * @return the  {@link NumberFormat} to format with
     */
    NumberFormat getFormat();

    /**
     * Return the formatted value.
     * @return the formatted value
     */
    String getFormattedValue();
}

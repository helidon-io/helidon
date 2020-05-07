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

/**
 * An implementation of a {@link FormattedNumber}.
 */
public class FormattedNumberImpl implements FormattedNumber {
    /**
     * The {@link NumberFormat} used to format the value.
     */
    private final NumberFormat formatter;

    /**
     * The formatted value.
     */
    private final String formattedValue;

    /**
     * Create a {@link FormattedNumberImpl}.
     *
     * @param formatter      the {@link NumberFormat} used to format the value
     * @param formattedValue the formatted value
     */
    public FormattedNumberImpl(NumberFormat formatter, String formattedValue) {
        this.formatter = formatter;
        this.formattedValue = formattedValue;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NumberFormat getFormat() {
        return formatter;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getFormattedValue() {
        return formattedValue;
    }
}

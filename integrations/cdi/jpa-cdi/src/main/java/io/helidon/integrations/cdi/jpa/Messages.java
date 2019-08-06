/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.integrations.cdi.jpa;

import java.text.MessageFormat;
import java.util.ResourceBundle;

final class Messages {

  private static final ResourceBundle MESSAGES = ResourceBundle.getBundle(Messages.class.getName());

    private Messages() {
        super();
    }

    static String getMessage(final String key) {
        final ResourceBundle rb = MESSAGES;
        final String returnValue;
        if (rb == null) {
            returnValue = key;
        } else {
            returnValue = rb.getString(key);
        }
        return returnValue;
    }

    static String format(final String key, final Object... args) {
        return MessageFormat.format(getMessage(key), args);
    }

}

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

package io.helidon.common.http;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import io.helidon.common.LazyValue;

class ServerResponseHeadersImpl extends HeadersImpl<ServerResponseHeaders> implements ServerResponseHeaders {
    private static final LazyValue<ZonedDateTime> START_OF_YEAR_1970 = LazyValue.create(
            () -> ZonedDateTime.of(1970, 1, 1, 0, 0, 0, 0, ZoneId.of("GMT+0")));

    ServerResponseHeadersImpl() {
    }

    ServerResponseHeadersImpl(Headers existing) {
        super(existing);
    }

    @Override
    public ServerResponseHeaders addCookie(SetCookie cookie) {
        add(Http.HeaderNames.create(Http.HeaderNames.SET_COOKIE, cookie.toString()));
        return this;
    }

    @Override
    public ServerResponseHeaders clearCookie(String name) {
        SetCookie expiredCookie = SetCookie.builder(name, "deleted")
                .path("/")
                .expires(START_OF_YEAR_1970.get())
                .build();

        if (contains(Http.HeaderNames.SET_COOKIE)) {
            remove(Http.HeaderNames.SET_COOKIE, it -> {
                List<String> currentValues = it.allValues();
                String[] newValues = new String[currentValues.size()];
                boolean found = false;
                for (int i = 0; i < currentValues.size(); i++) {
                    String currentValue = currentValues.get(i);
                    if (SetCookie.parse(currentValue).name().equals(name)) {
                        newValues[i] = expiredCookie.text();
                        found = true;
                    } else {
                        newValues[i] = currentValue;
                    }
                }
                if (!found) {
                    String[] values = new String[newValues.length + 1];
                    System.arraycopy(newValues, 0, values, 0, newValues.length);
                    values[values.length - 1] = expiredCookie.text();
                    newValues = values;
                }

                set(Http.HeaderNames.create(Http.HeaderNames.SET_COOKIE, newValues));
            });
        } else {
            addCookie(expiredCookie);
        }
        return this;
    }
}

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
package io.helidon.common.serviceloader;

import io.helidon.common.Prioritized;

/**
 * A service implementation.
 */
public class ServiceImpl2 implements ServiceInterface, Prioritized {
    private final String message;

    public ServiceImpl2() {
        this.message = ServiceImpl2.class.getName();
    }

    public ServiceImpl2(String message) {
        this.message = message;
    }

    @Override
    public String message() {
        return message;
    }

    @Override
    public int priority() {
        return 12;
    }
}

/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.common.mapper;

import io.helidon.common.GenericType;
import io.helidon.common.Size;
import io.helidon.service.registry.Service;

@Service.Singleton
class CustomMapper implements Mapper<String, Size> {
    @Override
    public Size map(String string) {
        return Size.parse(string);
    }

    @Override
    public GenericType<String> sourceType() {
        return GenericType.STRING;
    }

    @Override
    public GenericType<Size> targetType() {
        return GenericType.create(Size.class);
    }
}

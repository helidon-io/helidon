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

package org.openapitools.client.api;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import io.helidon.common.GenericType;

class ResponseType<T> {

  static <T> GenericType<T> create(Type rawType, Type... typeParams) {
    return typeParams.length == 0
      ? GenericType.create(rawType)
      : GenericType.create(new ParameterizedType() {

        @Override
        public Type[] getActualTypeArguments() {
          return typeParams;
        }

        @Override
        public Type getRawType() {
          return rawType;
        }

        @Override
        public Type getOwnerType() {
          return null;
        }
      });
  }
}
/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.integrations.kotlin.support

import io.helidon.common.config.Config
import io.helidon.common.config.ConfigValue
import io.helidon.common.mapper.MapperException
import io.helidon.common.reactive.Single
import io.helidon.reactive.dbclient.DbColumn
import io.helidon.reactive.dbclient.DbRow
import io.helidon.reactive.media.common.MessageBodyReadableContent
import io.helidon.reactive.media.multipart.ReadableBodyPart

/**
 * Extension function to hide keyword `as`.
 */
inline fun <reified T> MessageBodyReadableContent.single(): Single<T> {
    return this.`as`(T::class.java)
}

inline fun <reified T> DbRow.to(): T {
    return this.`as`(T::class.java)
}

inline fun <reified T> Config.to(): ConfigValue<T> {
    return this.`as`(T::class.java);
}

@Throws(MapperException::class)
inline fun <reified T> DbColumn.to(): T {
    return this.`as`(T::class.java)
}

inline fun <reified T> ReadableBodyPart.to(): T {
    return this.`as`(T::class.java)
}

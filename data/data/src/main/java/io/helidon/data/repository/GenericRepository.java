/*
 * Copyright (c) 2019, 2022 Oracle and/or its affiliates.
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
package io.helidon.data.repository;

// TODO: Large amount of this interface implementations may exist. Some internal annotation and indexing mechanism may be required.
/**
 * Data repository interface.
 * This is the parent interface of all data repositories.
 *
 * @param <E> type of the entity
 * @param <ID> type of the ID
 */
public interface GenericRepository<E, ID> {
}
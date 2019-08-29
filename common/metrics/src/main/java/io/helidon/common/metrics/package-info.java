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
 *
 */

/**
 * A collection of version-neutral interfaces (and some implementations where they
 * apply to all versions) of constructs used in MicroProfile Metrics.
 * <p>
 * Note: Only Helidon internal clients of metrics should use the classes and
 * interfaces in this package. The abstracting interfaces expose only the bare
 * minimum surface area of their version-specific counterparts that are used by
 * the various internal Helidon clients of metrics.
 */
package io.helidon.common.metrics;

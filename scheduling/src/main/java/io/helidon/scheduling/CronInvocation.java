/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.scheduling;

/**
 * Specific method invocation metadata for method scheduled with {@link io.helidon.microprofile.scheduling.Scheduled}.
 */
public interface CronInvocation extends Invocation {

    /**
     * Cron expression specifying interval invocation is scheduled with.
     *
     * @return cron expression
     */
    String cron();

    /**
     * When true, next task is started even if previous didn't finish yet.
     *
     * @return true means allowing concurrent invocation
     */
    boolean concurrent();
}

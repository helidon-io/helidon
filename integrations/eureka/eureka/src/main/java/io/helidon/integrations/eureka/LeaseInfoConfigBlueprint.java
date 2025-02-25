/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.integrations.eureka;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/*
 * Note that Javadoc for this interface is actually Javadoc for the prototype interface that is generated from it.
 *
 * Note as well that @see references to the prototype's methods cannot be used because they will reference the (publicly
 * inaccessible) blueprint interface.
 */

/**
 * A {@linkplain Prototype.Api prototype} describing initial Eureka Server service instance registration lease details.
 *
 * <p>This interface is deliberately modeled to closely resemble the {@code com.netflix.appinfo.LeaseInfo} class for
 * familiarity.</p>
 *
 * <p>Its configuration is deliberately modeled to closely resemble that expressed by the {@code
 * com.netflix.appinfo.PropertiesInstanceConfig} class and its supertypes for user familiarity.</p>
 */
@Prototype.Blueprint
@Prototype.Configured
interface LeaseInfoConfigBlueprint {

  /**
   * The lease renewal interval in seconds; the default value is strongly recommended.
   *
   * @return the lease renewal interval in seconds
   */
  @Option.Configured("renewalInterval")
  @Option.DefaultInt(30)
  int renewalIntervalInSecs();

  /**
   * The lease duration in seconds; the default value is strongly recommended.
   *
   * @return the lease duration in seconds
   */
  @Option.Configured("duration")
  @Option.DefaultInt(90)
  int durationInSecs();


}

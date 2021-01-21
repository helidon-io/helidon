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
 */

package io.helidon.microprofile.scheduling;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Marks the method to be invoked periodically according to supplied cron expression.
 * <p>
 * Cron expression format:
 * <pre>{@code
 *  <seconds> <minutes> <hours> <day-of-month> <month> <day-of-week> <year>
 * }</pre>
 *
 * <table>
 *  <caption><b>Cron expression fields</b></caption>
 *  <tr>
 *      <th>Order</th>
 *      <th>Name</th>
 *      <th>Supported values</th>
 *      <th>Supported field format</th>
 *      <th>Optional</th>
 *  </tr>
 *  <tbody>
 *      <tr>
 *          <td>1</td>
 *          <td>seconds</td>
 *          <td>0-59</td>
 *          <td>CONST, LIST, RANGE, WILDCARD, INCREMENT</td>
 *          <td>false</td>
 *      </tr>
 *      <tr>
 *          <td>2</td>
 *          <td>minutes</td>
 *          <td>0-59</td>
 *          <td>CONST, LIST, RANGE, WILDCARD, INCREMENT</td>
 *          <td>false</td>
 *      </tr>
 *      <tr>
 *          <td>3</td>
 *          <td>hours</td>
 *          <td>0-23</td>
 *          <td>CONST, LIST, RANGE, WILDCARD, INCREMENT</td>
 *          <td>false</td>
 *      </tr>
 *      <tr>
 *          <td>4</td>
 *          <td>day-of-month</td>
 *          <td>1-31</td>
 *          <td>CONST, LIST, RANGE, WILDCARD, INCREMENT, ANY, LAST, WEEKDAY</td>
 *          <td>false</td>
 *      </tr>
 *      <tr>
 *          <td>5</td>
 *          <td>month</td>
 *          <td>1-12 or JAN-DEC</td>
 *          <td>CONST, LIST, RANGE, WILDCARD, INCREMENT</td>
 *          <td>false</td>
 *      </tr>
 *      <tr>
 *          <td>6</td>
 *          <td>day-of-week</td>
 *          <td>1-7 or SUN-SAT</td>
 *          <td>CONST, LIST, RANGE, WILDCARD, INCREMENT, ANY, NTH, LAST</td>
 *          <td>false</td>
 *      </tr>
 *      <tr>
 *          <td>7</td>
 *          <td>year</td>
 *          <td>1970-2099</td>
 *          <td>CONST, LIST, RANGE, WILDCARD, INCREMENT</td>
 *          <td>true</td>
 *      </tr>
 * </tbody>
 * </table>
 *
 * <p>
 *
 * <table>
 *  <caption><b>Field formats</b></caption>
 *  <tr>
 *      <th>Name</th>
 *      <th>Regex format</th>
 *      <th>Example</th>
 *      <th>Description</th>
 *  </tr>
 *  <tbody>
 *      <tr>
 *          <td>CONST</td>
 *          <td>\d+</td>
 *          <td>12</td>
 *          <td>exact value</td>
 *      </tr>
 *      <tr>
 *          <td>LIST</td>
 *          <td>\d+,\d+(,\d+)*</td>
 *          <td>1,2,3,4</td>
 *          <td>list of constants</td>
 *      </tr>
 *      <tr>
 *          <td>RANGE</td>
 *          <td>\d+-\d+</td>
 *          <td>15-30</td>
 *          <td>range of values from-to</td>
 *      </tr>
 *      <tr>
 *          <td>WILDCARD</td>
 *          <td>\*</td>
 *          <td>*</td>
 *          <td>all values withing the field</td>
 *      </tr>
 *      <tr>
 *          <td>INCREMENT</td>
 *          <td>\d+\/\d+</td>
 *          <td>0/5</td>
 *          <td>inital number / increments, 2/5 means 2,7,9,11,16,...</td>
 *      </tr>
 *      <tr>
 *          <td>ANY</td>
 *          <td>\?</td>
 *          <td>?</td>
 *          <td>any day(apply only to day-of-week and day-of-month)</td>
 *      </tr>
 *      <tr>
 *          <td>NTH</td>
 *          <td>\#</td>
 *          <td>1#3</td>
 *          <td>nth day of the month, 2#3 means third monday of the month</td>
 *      </tr>
 *      <tr>
 *          <td>LAST</td>
 *          <td>\d*L(\+\d+|\-\d+)?</td>
 *          <td>3L-3</td>
 *          <td>last day of the month in day-of-month or last nth day in the day-of-week</td>
 *      </tr>
 *      <tr>
 *          <td>WEEKDAY</td>
 *          <td>\#</td>
 *          <td>1#3</td>
 *          <td>nearest weekday of the nth day of month, 1W is the first monday of the week</td>
 *      </tr>
 * </tbody>
 * </table>
 *
 * <table>
 *  <caption><b>Examples</b></caption>
 *  <tr>
 *      <th>Cron expression</th>
 *      <th>Description</th>
 *  </tr>
 *  <tbody>
 *      <tr>
 *          <td>* * * * * ?</td>
 *          <td>Every second</td>
 *      </tr>
 *      <tr>
 *          <td>0/2 * * * * ? *</td>
 *          <td>Every 2 seconds</td>
 *      </tr>
 *      <tr>
 *          <td>0 45 9 ? * *</td>
 *          <td>Every day at 9:45</td>
 *      </tr>
 *      <tr>
 *          <td>0 15 8 ? * MON-FRI</td>
 *          <td>Every workday at 8:15</td>
 *      </tr>
 * </tbody>
 * </table>
 *
 * <p>
 */
@Retention(RUNTIME)
@Target({METHOD})
public @interface Scheduled {

    /**
     * Cron expression specifying period for invocation.
     *
     * @return cron expression as string
     */
    String value();

    /**
     * When true, next task is started even if previous didn't finish yet.
     *
     * @return true for allowing concurrent invocation
     */
    boolean concurrentExecution() default true;
}

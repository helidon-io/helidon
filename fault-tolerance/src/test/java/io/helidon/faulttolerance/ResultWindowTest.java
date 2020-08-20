/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.faulttolerance;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class ResultWindowTest {
    @Test
    void test() {
        ResultWindow window = new ResultWindow(10, 10);
        assertThat("Empty should not open", window.shouldOpen(), is(false));
        window.update(ResultWindow.Result.SUCCESS);
        window.update(ResultWindow.Result.SUCCESS);
        window.update(ResultWindow.Result.SUCCESS);
        assertThat("Only success should not open", window.shouldOpen(), is(false));
        window.update(ResultWindow.Result.FAILURE);
        window.update(ResultWindow.Result.FAILURE);
        assertThat("Should open on first failure (> 10%)", window.shouldOpen(), is(true));
        //now cycle through window and replace all with success
        for (int i = 0; i < 10; i++) {
             window.update(ResultWindow.Result.SUCCESS);
        }
        assertThat("All success should not open", window.shouldOpen(), is(false));
        window.update(ResultWindow.Result.FAILURE);
        window.update(ResultWindow.Result.FAILURE);
        assertThat("Should open on first failure (> 10%)", window.shouldOpen(), is(true));
        window.reset();
        assertThat("Should not open after reset", window.shouldOpen(), is(false));
    }
}
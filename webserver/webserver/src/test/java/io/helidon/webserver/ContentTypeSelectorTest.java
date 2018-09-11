/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.webserver;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import io.helidon.common.http.MediaType;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests {@link ContentTypeSelector}.
 */
public class ContentTypeSelectorTest {

    @Test
    public void testContentTypeSelection() throws Exception {
        Map<String, MediaType> map = new HashMap<>();
        map.put("txt", new MediaType("foo", "bar"));
        ContentTypeSelector selector = new ContentTypeSelector(map);
        // Empty headers
        RequestHeaders headers = mock(RequestHeaders.class);
        when(headers.isAccepted(any())).thenReturn(true);
        when(headers.acceptedTypes()).thenReturn(Collections.emptyList());
        assertEquals(MediaType.APPLICATION_XML, selector.determine("foo.xml", headers));
        assertEquals(new MediaType("foo", "bar"), selector.determine("foo.txt", headers));
        assertEquals(MediaType.APPLICATION_OCTET_STREAM, selector.determine("foo.undefined", headers));
        assertEquals(MediaType.APPLICATION_OCTET_STREAM, selector.determine("undefined", headers));
        // Accept text/html
        headers = mock(RequestHeaders.class);
        when(headers.acceptedTypes()).thenReturn(Collections.singletonList(MediaType.TEXT_HTML));
        assertEquals(MediaType.TEXT_HTML, selector.determine("foo.undefined", headers));
    }

    @Test
    public void testInvalidFile(){
        ContentTypeSelector selector = new ContentTypeSelector(Collections.emptyMap());
        RequestHeaders headers = mock(RequestHeaders.class);
        when(headers.isAccepted(any())).thenReturn(false);
        when(headers.acceptedTypes()).thenReturn(Collections.singletonList(MediaType.TEXT_HTML));
        HttpException ex = assertThrows(HttpException.class, () -> { selector.determine("foo.xml", headers); });
        assertEquals("Not accepted media-type!", ex.getMessage());
    }
}

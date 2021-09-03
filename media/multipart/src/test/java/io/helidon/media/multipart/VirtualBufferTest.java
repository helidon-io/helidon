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
package io.helidon.media.multipart;

import java.nio.ByteBuffer;
import java.util.List;

import io.helidon.common.http.Utils;
import io.helidon.media.multipart.VirtualBuffer.BufferEntry;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests {@link VirtualBuffer}.
 */
public class VirtualBufferTest {

    @Test
    public void singleBufferGetByteTest() {
        VirtualBuffer buf = new VirtualBuffer();
        buf.offer(ByteBuffer.wrap("foo".getBytes()), 0);
        assertThat((char) buf.getByte(0), is(equalTo('f')));
        assertThat((char) buf.getByte(1), is(equalTo('o')));
        assertThat((char) buf.getByte(2), is(equalTo('o')));
    }

    @Test
    public void singleBufferWithOffsetGetByteTest() {
        VirtualBuffer buf = new VirtualBuffer();
        buf.offer(ByteBuffer.wrap("xxxfoo".getBytes()), 3);
        assertThat((char) buf.getByte(0), is(equalTo('f')));
        assertThat((char) buf.getByte(1), is(equalTo('o')));
        assertThat((char) buf.getByte(2), is(equalTo('o')));
    }

    @Test
    public void multipleBuffersGetByteTest() {
        VirtualBuffer buf = new VirtualBuffer();
        buf.offer(ByteBuffer.wrap("foo".getBytes()), 0);
        buf.offer(ByteBuffer.wrap("bar".getBytes()), 0);
        assertThat((char) buf.getByte(3), is(equalTo('b')));
        assertThat((char) buf.getByte(4), is(equalTo('a')));
        assertThat((char) buf.getByte(5), is(equalTo('r')));
    }

    @Test
    public void multipleBuffersWithOffsetGetByteTest() {
        VirtualBuffer buf = new VirtualBuffer();
        buf.offer(ByteBuffer.wrap("foo".getBytes()), 0);
        buf.offer(ByteBuffer.wrap("bar".getBytes()), 3);
        assertThat((char) buf.getByte(0), is(equalTo('b')));
        assertThat((char) buf.getByte(1), is(equalTo('a')));
        assertThat((char) buf.getByte(2), is(equalTo('r')));
    }

    @Test
    public void singleBufferGetBytesTest() {
        VirtualBuffer buf = new VirtualBuffer();
        buf.offer(ByteBuffer.wrap("fooxxx".getBytes()), 0);
        assertThat(new String(buf.getBytes(0, 3)), is(equalTo("foo")));
    }

    @Test
    public void singleBufferWithOffsetGetBytesTest() {
        VirtualBuffer buf = new VirtualBuffer();
        buf.offer(ByteBuffer.wrap("xxxfooxxx".getBytes()), 3);
        assertThat(new String(buf.getBytes(0, 3)), is(equalTo("foo")));
    }

    @Test
    public void multipleBuffersGetBytesTest() {
        VirtualBuffer buf = new VirtualBuffer();
        buf.offer(ByteBuffer.wrap("foo".getBytes()), 0);
        buf.offer(ByteBuffer.wrap("bar".getBytes()), 0);
        assertThat(new String(buf.getBytes(3, 3)), is(equalTo("bar")));
    }

    @Test
    public void multipleBuffersWithOffsetGetBytesTest() {
        VirtualBuffer buf = new VirtualBuffer();
        buf.offer(ByteBuffer.wrap("foo".getBytes()), 0);
        buf.offer(ByteBuffer.wrap("bar".getBytes()), 3);
        assertThat(new String(buf.getBytes(0, 3)), is(equalTo("bar")));
    }

    @Test
    public void singleBufferSliceTest() {
        VirtualBuffer buf = new VirtualBuffer();
        buf.offer(ByteBuffer.wrap("xxxfoo".getBytes()), 0);
        List<BufferEntry> slices = buf.slice(3, 6);
        assertThat(slices.size(), is(equalTo(1)));
        ByteBuffer bb = slices.get(0).buffer();
        assertThat(new String(Utils.toByteArray(bb)), is(equalTo("foo")));
    }

    @Test
    public void singleBufferWithOffsetSliceTest() {
        VirtualBuffer buf = new VirtualBuffer();
        buf.offer(ByteBuffer.wrap("xxxfoo".getBytes()), 3);
        List<BufferEntry> slices = buf.slice(0, 3);
        assertThat(slices.size(), is(equalTo(1)));
        ByteBuffer bb = slices.get(0).buffer();
        assertThat(new String(Utils.toByteArray(bb)), is(equalTo("foo")));
    }

    @Test
    public void multipleBuffersSliceTest() {
        VirtualBuffer buf = new VirtualBuffer();
        buf.offer(ByteBuffer.wrap("xxx".getBytes()), 0);
        buf.offer(ByteBuffer.wrap("foo".getBytes()), 0);
        List<BufferEntry> slices = buf.slice(3, 6);
        assertThat(slices.size(), is(equalTo(1)));
        ByteBuffer bb = slices.get(0).buffer();
        assertThat(new String(Utils.toByteArray(bb)), is(equalTo("foo")));
    }

    @Test
    public void multipleBuffersWithOffsetSliceTest() {
        VirtualBuffer buf = new VirtualBuffer();
        byte[] bytes1 = "xxxfo".getBytes();
        byte[] bytes2 = "obarxxx".getBytes();
        buf.offer(ByteBuffer.wrap(bytes1), 0);
        buf.offer(ByteBuffer.wrap(bytes2), 3);
        List<BufferEntry> slices1 = buf.slice(0, 3);
        assertThat(slices1.size(), is(equalTo(2)));
        ByteBuffer bb1 = slices1.get(0).buffer();
        ByteBuffer bb2 = slices1.get(1).buffer();
        assertThat(new String(Utils.toByteArray(bb1)), is(equalTo("fo")));
        assertThat(new String(Utils.toByteArray(bb2)), is(equalTo("o")));

        List<BufferEntry> slices2 = buf.slice(3, 6);
        assertThat(slices2.size(), is(equalTo(1)));
        ByteBuffer barSlice = slices2.get(0).buffer();
        int barSlicePos = barSlice.position();
        assertThat(new String(Utils.toByteArray(barSlice)), is(equalTo("bar")));

        // test barSlice is backed by bytes2
        barSlice.position(barSlicePos);
        byte[] newBytes2 = "?abc???".getBytes();
        System.arraycopy(newBytes2, 0, bytes2, 0, newBytes2.length);
        assertThat(new String(Utils.toByteArray(barSlice)), is(equalTo("abc")));
    }

    @Test
    public void accumulateBufferTest() {
        VirtualBuffer buf = new VirtualBuffer();

        buf.offer(ByteBuffer.wrap(("--boundary\n"
                + "Content-Id: part1\n"
                + "\n"
                + "body 1.aaaa\n").getBytes()), 0);
        assertThat(buf.length(),  is(equalTo(42)));
        assertThat(new String(buf.getBytes(11, 17)), is(equalTo("Content-Id: part1")));
        List<BufferEntry> slices1 = buf.slice(30, 31);
        assertThat(slices1.size(), is(equalTo(1)));
        ByteBuffer bb1 = slices1.get(0).buffer();
        assertThat(new String(Utils.toByteArray(bb1)), is(equalTo("b")));

        buf.offer(ByteBuffer.wrap("body 1.bbbb\n".getBytes()), 31);
        assertThat(buf.length(), is(equalTo(23)));
        List<BufferEntry> slices2 = buf.slice(0, 12);
        assertThat(slices2.size(), is(equalTo(2)));
        ByteBuffer bb2 = slices2.get(0).buffer();
        ByteBuffer bb3 = slices2.get(1).buffer();
        assertThat(new String(Utils.toByteArray(bb2)), is(equalTo("ody 1.aaaa\n")));
        assertThat(new String(Utils.toByteArray(bb3)), is(equalTo("b")));

        buf.offer(ByteBuffer.wrap(("body 1.cccc\n"
                + "--boundary\n"
                + "Content-Id: part2\n"
                + "\n"
                + "This is the 2nd").getBytes()), 12);
        assertThat(buf.length(),  is(equalTo(68)));
        assertThat(buf.buffersCount(), is(equalTo(2)));
        assertThat(new String(buf.getBytes(34, 17)), is(equalTo("Content-Id: part2")));
        List<BufferEntry> slices3 = buf.slice(0, 22);
        assertThat(slices3.size(), is(equalTo(2)));
        ByteBuffer bb4 = slices3.get(0).buffer();
        ByteBuffer bb5 = slices3.get(1).buffer();
        assertThat(new String(Utils.toByteArray(bb4)), is(equalTo("ody 1.bbbb\n")));
        assertThat(new String(Utils.toByteArray(bb5)), is(equalTo("body 1.cccc")));
        List<BufferEntry> slices4 = buf.slice(53, 57);
        assertThat(slices4.size(), is(equalTo(1)));
        ByteBuffer bb6 = slices4.get(0).buffer();
        assertThat(new String(Utils.toByteArray(bb6)), is(equalTo("This")));

        buf.offer(ByteBuffer.wrap((" body.\n"
                + "--boundary--").getBytes()), 33);
        assertThat(buf.length(), is(equalTo(54)));
        assertThat(buf.buffersCount(), is(equalTo(2)));
        List<BufferEntry> slices5 = buf.slice(24, 41);
        assertThat(slices5.size(), is(equalTo(2)));
        ByteBuffer bb7 = slices5.get(0).buffer();
        ByteBuffer bb8 = slices5.get(1).buffer();
        assertThat(new String(Utils.toByteArray(bb7)), is(equalTo(" is the 2nd")));
        assertThat(new String(Utils.toByteArray(bb8)), is(equalTo(" body.")));
    }
}

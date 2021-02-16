

package io.helidon.common.http;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.HttpContent;
import org.junit.jupiter.api.Test;

/**
 * Class NettyTest.
 *
 * @author Santiago Pericas-Geertsen
 */
public class NettyTest {

    @Test
    void test1() {
        ByteBuf bb = PooledByteBufAllocator.DEFAULT.buffer(10);
        HttpContent content = new DefaultHttpContent(bb);
        System.out.println(content);
        bb.release();
    }
}

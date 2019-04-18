package io.helidon.common.http;

import org.junit.Test;

import java.net.URI;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Date;

import static org.junit.Assert.*;

public class SetCookieTest {

    private SetCookie setCookie = new SetCookie("foo", "bar");

    @Test
    public void testExpireswithInstant() {
        SimpleDateFormat sdf = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z");
        assertEquals("foo=bar; Expires=" + sdf.format(new Date()), setCookie.expires(Instant.now()).toString());
    }

    @Test
    public void testDomainAndPath() throws URISyntaxException {
        assertEquals("foo=bar", setCookie.domainAndPath(null).toString());
        assertEquals("foo=bar; Path=test", setCookie.domainAndPath(new URI("test")).toString());
    }

    @Test
    public void testToString1() {
        setCookie.maxAge(Duration.ofMinutes(1000));
        setCookie.expires(ZonedDateTime.now());
        setCookie.domain("http://");
        setCookie.path("baz");
        setCookie.secure(true);
        setCookie.httpOnly(true);
        SimpleDateFormat sdf = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z");

        String retval = "; Max-Age=60000; Domain=http://; Path=baz; Secure; HttpOnly";
        assertEquals("foo=bar; Expires=" + sdf.format(new Date()) + retval, setCookie.toString());
    }

    @Test
    public void testToString2() {
        setCookie.maxAge(null);
        setCookie.expires(ZonedDateTime.now());
        setCookie.domain(null);
        setCookie.path(null);
        setCookie.secure(false);
        setCookie.httpOnly(false);
        SimpleDateFormat sdf = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z");

        assertEquals("foo=bar; Expires=" + sdf.format(new Date()), setCookie.toString());
    }
}

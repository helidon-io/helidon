/*
 * Copyright (c) 2017, 2021 Oracle and/or its affiliates.
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

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;

/**
 * Provides mapping between filename extension and media type.
 */
@Deprecated
class ContentTypeSelector {

    private static final Map<String, MediaType> CONTENT_TYPES = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    static {
        putSingle("abs", "audio/x-mpeg");
        putSingle("ai", "application/postscript");
        putSingle("aif", "audio/x-aiff");
        putSingle("aifc", "audio/x-aiff");
        putSingle("aiff", "audio/x-aiff");
        putSingle("aim", "application/x-aim");
        putSingle("art", "image/x-jg");
        putSingle("asf", "video/x-ms-asf");
        putSingle("asx", "video/x-ms-asf");
        putSingle("au", "audio/basic");
        putSingle("avi", "video/x-msvideo");
        putSingle("avx", "video/x-rad-screenplay");
        putSingle("bcpio", "application/x-bcpio");
        putSingle("bin", "application/octet-stream");
        putSingle("bmp", "image/bmp");
        putSingle("body", "text/html");
        putSingle("cdf", "application/x-cdf");
        putSingle("cer", "application/x-x509-ca-cert");
        putSingle("class", "application/java");
        putSingle("cpio", "application/x-cpio");
        putSingle("csh", "application/x-csh");
        putSingle("css", "text/css");
        putSingle("dib", "image/bmp");
        putSingle("doc", "application/msword");
        putSingle("dtd", "application/xml-dtd");
        putSingle("dv", "video/x-dv");
        putSingle("dvi", "application/x-dvi");
        putSingle("eps", "application/postscript");
        putSingle("etx", "text/x-setext");
        putSingle("exe", "application/octet-stream");
        putSingle("gif", "image/gif");
        putSingle("gk", "application/octet-stream");
        putSingle("gtar", "application/x-gtar");
        putSingle("gz", "application/x-gzip");
        putSingle("hdf", "application/x-hdf");
        putSingle("hqx", "application/mac-binhex40");
        putSingle("htc", "text/x-component");
        putSingle("htm", "text/html");
        putSingle("html", "text/html");
        putSingle("hqx", "application/mac-binhex40");
        putSingle("ief", "image/ief");
        putSingle("jad", "text/vnd.sun.j2me.app-descriptor");
        putSingle("jar", "application/java-archive");
        putSingle("java", "text/plain");
        putSingle("jnlp", "application/x-java-jnlp-file");
        putSingle("jpe", "image/jpeg");
        putSingle("jpeg", "image/jpeg");
        putSingle("jpg", "image/jpeg");
        putSingle("js", "text/javascript");
        putSingle("kar", "audio/x-midi");
        putSingle("latex", "application/x-latex");
        putSingle("m3u", "audio/x-mpegurl");
        putSingle("mac", "image/x-macpaint");
        putSingle("man", "application/x-troff-man");
        putSingle("mathml", "application/mathml+xml");
        putSingle("me", "application/x-troff-me");
        putSingle("mid", "audio/x-midi");
        putSingle("midi", "audio/x-midi");
        putSingle("mif", "application/x-mif");
        putSingle("mov", "video/quicktime");
        putSingle("movie", "video/x-sgi-movie");
        putSingle("mp1", "audio/x-mpeg");
        putSingle("mp2", "audio/x-mpeg");
        putSingle("mp3", "audio/x-mpeg");
        putSingle("mpa", "audio/x-mpeg");
        putSingle("mpe", "video/mpeg");
        putSingle("mpeg", "video/mpeg");
        putSingle("mpega", "audio/x-mpeg");
        putSingle("mpg", "video/mpeg");
        putSingle("mpv2", "video/mpeg2");
        putSingle("ms", "application/x-wais-source");
        putSingle("nc", "application/x-netcdf");
        putSingle("oda", "application/oda");
        putSingle("ogg", "application/ogg");
        putSingle("pbm", "image/x-portable-bitmap");
        putSingle("pct", "image/pict");
        putSingle("pdf", "application/pdf");
        putSingle("pgm", "image/x-portable-graymap");
        putSingle("pic", "image/pict");
        putSingle("pict", "image/pict");
        putSingle("pls", "audio/x-scpls");
        putSingle("png", "image/png");
        putSingle("pnm", "image/x-portable-anymap");
        putSingle("pnt", "image/x-macpaint");
        putSingle("ppm", "image/x-portable-pixmap");
        putSingle("ppt", "application/powerpoint");
        putSingle("ps", "application/postscript");
        putSingle("psd", "image/x-photoshop");
        putSingle("qt", "video/quicktime");
        putSingle("qti", "image/x-quicktime");
        putSingle("qtif", "image/x-quicktime");
        putSingle("ras", "image/x-cmu-raster");
        putSingle("rdf", "application/rdf+xml");
        putSingle("rgb", "image/x-rgb");
        putSingle("rm", "application/vnd.rn-realmedia");
        putSingle("roff", "application/x-troff");
        putSingle("rtf", "application/rtf");
        putSingle("rtx", "text/richtext");
        putSingle("sh", "application/x-sh");
        putSingle("shar", "application/x-shar");
        putSingle("shtml", "text/x-server-parsed-html");
        putSingle("sit", "application/x-stuffit");
        putSingle("smf", "audio/x-midi");
        putSingle("snd", "audio/basic");
        putSingle("src", "application/x-wais-source");
        putSingle("sv4cpio", "application/x-sv4cpio");
        putSingle("sv4crc", "application/x-sv4crc");
        putSingle("svg", "image/svg+xml");
        putSingle("svgz", "image/svg+xml");
        putSingle("swf", "application/x-shockwave-flash");
        putSingle("t", "application/x-troff");
        putSingle("tar", "application/x-tar");
        putSingle("tcl", "application/x-tcl");
        putSingle("tex", "application/x-tex");
        putSingle("texi", "application/x-texinfo");
        putSingle("texinfo", "application/x-texinfo");
        putSingle("tif", "image/tiff");
        putSingle("tiff", "image/tiff");
        putSingle("tr", "application/x-troff");
        putSingle("tsv", "text/tab-separated-values");
        putSingle("txt", "text/plain");
        putSingle("ulw", "audio/basic");
        putSingle("ustar", "application/x-ustar");
        putSingle("xbm", "image/x-xbitmap");
        putSingle("xml", "application/xml");
        putSingle("xpm", "image/x-xpixmap");
        putSingle("xsl", "application/xml");
        putSingle("xslt", "application/xslt+xml");
        putSingle("xwd", "image/x-xwindowdump");
        putSingle("vsd", "application/x-visio");
        putSingle("vxml", "application/voicexml+xml");
        putSingle("wav", "audio/x-wav");
        putSingle("wbmp", "image/vnd.wap.wbmp");
        putSingle("wml", "text/vnd.wap.wml");
        putSingle("wmlc", "application/vnd.wap.wmlc");
        putSingle("wmls", "text/vnd.wap.wmls");
        putSingle("wmlscriptc", "application/vnd.wap.wmlscriptc");
        putSingle("wrl", "x-world/x-vrml");
        putSingle("xht", "application/xhtml+xml");
        putSingle("xhtml", "application/xhtml+xml");
        putSingle("xls", "application/vnd.ms-excel");
        putSingle("xul", "application/vnd.mozilla.xul+xml");
        putSingle("Z", "application/x-compress");
        putSingle("z", "application/x-compress");
        putSingle("zip", "application/zip");
        putSingle("json", "application/json");
    }

    private final Map<String, MediaType> specificContentTypes = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    /**
     * @param specificContentTypes rewrites or extends default file extension to media types mapping. It can be {@code null}
     *                             or empty.
     */
    ContentTypeSelector(Map<String, MediaType> specificContentTypes) {
        if (specificContentTypes != null) {
            this.specificContentTypes.putAll(specificContentTypes);
        }
    }

    /**
     * Add a single value to the STATIC map. <b>Should be used ONLY from static constructor.</b>
     *
     * @param extension a file extension without a leading dot character
     * @param contentTypeName a content type to parse into {@link MediaType}
     */
    private static void putSingle(String extension, String contentTypeName) {
        CONTENT_TYPES.put(extension, MediaType.parse(contentTypeName));
    }

    private MediaType get(String filename) {
        if (filename == null) {
            return null;
        }
        // Get extension
        int ind = filename.lastIndexOf('.');
        if (ind < 0) {
            return null;
        }
        String extension = filename.substring(ind + 1);
        // Get type for the extension
        MediaType result = specificContentTypes.get(extension);
        if (result == null) {
            result = CONTENT_TYPES.get(extension);
        }
        return result;
    }

    MediaType determine(String filename, RequestHeaders requestHeaders) {
        MediaType mediaType = get(filename);
        List<MediaType> accepted = requestHeaders.acceptedTypes();
        if (mediaType == null) {
            // First from Accepted
            if (accepted.isEmpty()) {
                // Most general
                return MediaType.APPLICATION_OCTET_STREAM;
            } else {
                return accepted.get(0);
            }
        } else {
            // Must deal type
            if (requestHeaders.isAccepted(mediaType)) {
                return mediaType;
            } else {
                throw new HttpException("Not accepted media-type!", Http.Status.NOT_FOUND_404);
            }
        }
    }
}

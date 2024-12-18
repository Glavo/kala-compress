/*
 * Copyright 2024 Glavo
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
package kala.compress.utils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.*;

import static java.nio.charset.StandardCharsets.*;

/**
 * Utility methods for charsets.
 *
 * See {@code kala.compress.archivers.zip.ZipEncoding}
 *
 * @since 1.21.0.1
 */
public class Charsets {
    private static final char[] HEX_CHARS = new char[]{
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
    };

    private static final Charset NATIVE_CHARSET;

    static {
        String property = System.getProperty("kala.compress.native.charset");
        Charset charset = Charset.defaultCharset();
        if (property != null) {
            charset = Charset.forName(property); // Do not suppress exceptions when native charset explicitly specified by the user
        } else {
            property = System.getProperty("native.encoding");
            if (property != null && !property.equals(charset.name())) {
                try {
                    charset = Charset.forName(property);
                } catch (Throwable ignored) {
                }
            }
        }

        NATIVE_CHARSET = charset;
    }

    /**
     * Returns the platform default charset.
     * Users can override it by setting the system property 'kala.compress.native.charset'.
     *
     * @since 1.21.0.1
     */
    public static Charset nativeCharset() {
        return NATIVE_CHARSET;
    }

    /**
     * Tests whether a given encoding is UTF-8 or null.
     *
     * @since 1.27.1-0
     */
    public static boolean isUTF8(final Charset charset) {
        return charset == null || charset == UTF_8;
    }

    /**
     * Returns a charset object for the named charset.
     *
     * Use this method instead of {@code kala.compress.archivers.zip.ZipEncodingHelper#getZipEncoding(String)}
     *
     * @param name The name of the encoding. Specify null for the UTF-8.
     * @throws  IllegalCharsetNameException
     *          If the given charset name is illegal
     * @throws  UnsupportedCharsetException
     *          If no support for the named charset is available
     *          in this instance of the Java virtual machine
     * @return A charset object for the named encoding
     */
    public static Charset toCharset(String name) {
        return name != null ? Charset.forName(name) : UTF_8;
    }

    /**
     * Returns a charset object for the named charset.
     * If the requested character set cannot be found, {@code defaultCharset} will be used instead.
     *
     * @param name The name of the encoding. Specify null for the UTF-8.
     * @return A charset object for the named encoding
     */
    public static Charset toCharset(String name, Charset defaultCharset) {
        if (name == null) {
            return defaultCharset;
        }

        try {
            return Charset.forName(name);
        } catch (Throwable ignored) {
            return defaultCharset;
        }
    }

    /**
     * Returns the given charset or the UTF-8 if the given charset is null.
     *
     * @param charset A charset or null.
     * @return the given Charset or the UTF-8 if the given Charset is null
     */
    public static Charset toCharset(Charset charset) {
        return charset != null ? charset : UTF_8;
    }

    /**
     * Returns the given charset or the UTF-8 if the given charset is null.
     *
     * @param charset A charset or null.
     * @return the given Charset or the UTF-8 if the given Charset is null
     */
    public static Charset toCharset(Charset charset, Charset defaultCharset) {
        return charset != null ? charset : defaultCharset;
    }

    /**
     * Check, whether the given string may be losslessly encoded using this
     * encoding.
     *
     * Use this method instead of {@code kala.compress.archivers.zip.ZipEncoding#canEncode(String)}
     *
     * @param name A file name or ZIP comment.
     * @return Whether the given name may be encoded with out any losses.
     * @see CharsetEncoder#canEncode(CharSequence)
     */
    public static boolean canEncode(Charset charset, String name) {
        if (charset == UTF_8 || charset.name().startsWith("UTF-")) {
            final int length = name.length();
            for (int i = 0; i < length; i++) {
                char ch = name.charAt(i);
                if (Character.isLowSurrogate(ch)) {
                    return false;
                }

                if (Character.isHighSurrogate(ch)) {
                    i++;
                    if (i >= length || !Character.isLowSurrogate(name.charAt(i))) {
                        return false;
                    }
                }
            }
            return true;
        }

        if (charset == US_ASCII || charset == ISO_8859_1) {
            final int maxChar = charset == US_ASCII ? 127 : 255;
            final int length = name.length();
            for (int i = 0; i < length; i++) {
                char ch = name.charAt(i);
                if (ch > maxChar)
                    return false;
            }
            return true;
        }

        return encoderFor(charset).canEncode(name);
    }

    /**
     * Encode a file name or a comment to a byte array suitable for
     * storing it to a serialized zip entry.
     *
     * <p>Examples for CP 437 (in pseudo-notation, right hand side is
     * C-style notation):</p>
     * <pre>
     *  encode("\u20AC_for_Dollar.txt") = "%U20AC_for_Dollar.txt"
     *  encode("\u00D6lf\u00E4sser.txt") = "\231lf\204sser.txt"
     * </pre>
     *
     * Use this method instead of {@code org.apache.commons.compress.archivers.zip.ZipEncoding#encode(String)}
     *
     * @param name A file name or ZIP comment.
     * @return A byte buffer with a backing array containing the
     * encoded name.  Unmappable characters or malformed
     * character sequences are mapped to a sequence of utf-16
     * words encoded in the format <code>%Uxxxx</code>.  It is
     * assumed, that the byte buffer is positioned at the
     * beginning of the encoded result, the byte buffer has a
     * backing array and the limit of the byte buffer points
     * to the end of the encoded result.
     * @throws IOException on error
     */
    public static ByteBuffer encode(Charset charset, String name) {
        if (charset == UTF_8) {
            return ByteBuffer.wrap(name.getBytes(UTF_8));
        }

        final CharsetEncoder enc = encoderFor(charset);

        final CharBuffer cb = CharBuffer.wrap(name);
        CharBuffer tmp = null;
        ByteBuffer out = ByteBuffer.allocate(estimateInitialBufferSize(enc, cb.remaining()));

        while (cb.hasRemaining()) {
            final CoderResult res = enc.encode(cb, out, false);

            if (res.isUnmappable() || res.isMalformed()) {

                // write the unmappable characters in utf-16
                // pseudo-URL encoding style to ByteBuffer.

                final int spaceForSurrogate = estimateIncrementalEncodingSize(enc, 6 * res.length());
                if (spaceForSurrogate > out.remaining()) {
                    // if the destination buffer isn't oversized, assume that the presence of one
                    // unmappable character makes it likely that there will be more. Find all the
                    // un-encoded characters and allocate space based on those estimates.
                    int charCount = 0;
                    for (int i = cb.position(); i < cb.limit(); i++) {
                        charCount += !enc.canEncode(cb.get(i)) ? 6 : 1;
                    }
                    final int totalExtraSpace = estimateIncrementalEncodingSize(enc, charCount);
                    out = growBufferBy(out, totalExtraSpace - out.remaining());
                }
                if (tmp == null) {
                    tmp = CharBuffer.allocate(6);
                }
                for (int i = 0; i < res.length(); ++i) {
                    out = encodeFully(enc, encodeSurrogate(tmp, cb.get()), out);
                }

            } else if (res.isOverflow()) {
                final int increment = estimateIncrementalEncodingSize(enc, cb.remaining());
                out = growBufferBy(out, increment);

            } else if (res.isUnderflow() || res.isError()) {
                break;
            }
        }
        // tell the encoder we are done
        enc.encode(cb, out, true);
        // may have caused underflow, but that's been ignored traditionally

        out.limit(out.position());
        out.rewind();
        return out;
    }

    /**
     * Use this method instead of {@code org.apache.commons.compress.archivers.zip.ZipEncoding#decode(byte[])}
     *
     * @param data The byte values to decode.
     * @return The decoded string.
     * @throws IOException on error
     */
    public static String decode(Charset charset, final byte[] data) throws IOException {
        if (charset == UTF_8) {
            return new String(data, UTF_8);
        }

        return decoderFor(charset).decode(ByteBuffer.wrap(data)).toString();
    }

    private static ByteBuffer growBufferBy(final ByteBuffer buffer, final int increment) {
        buffer.limit(buffer.position());
        buffer.rewind();

        final ByteBuffer on = ByteBuffer.allocate(buffer.capacity() + increment);

        on.put(buffer);
        return on;
    }

    private static ByteBuffer encodeFully(final CharsetEncoder enc, final CharBuffer cb, final ByteBuffer out) {
        ByteBuffer o = out;
        while (cb.hasRemaining()) {
            final CoderResult result = enc.encode(cb, o, false);
            if (result.isOverflow()) {
                final int increment = estimateIncrementalEncodingSize(enc, cb.remaining());
                o = growBufferBy(o, increment);
            }
        }
        return o;
    }

    private static CharBuffer encodeSurrogate(final CharBuffer cb, final char c) {
        cb.position(0).limit(6);
        cb.put('%');
        cb.put('U');

        cb.put(HEX_CHARS[(c >> 12) & 0x0f]);
        cb.put(HEX_CHARS[(c >> 8) & 0x0f]);
        cb.put(HEX_CHARS[(c >> 4) & 0x0f]);
        cb.put(HEX_CHARS[c & 0x0f]);
        cb.flip();
        return cb;
    }

    private static CharsetEncoder encoderFor(Charset charset) {
        return charset.newEncoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
    }

    private static CharsetDecoder decoderFor(Charset charset) {
        return charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
    }

    /**
     * Estimate the initial encoded size (in bytes) for a character buffer.
     * <p>
     * The estimate assumes that one character consumes uses the maximum length encoding,
     * whilst the rest use an average size encoding. This accounts for any BOM for UTF-16, at
     * the expense of a couple of extra bytes for UTF-8 encoded ASCII.
     * </p>
     *
     * @param enc        encoder to use for estimates
     * @param charChount number of characters in string
     * @return estimated size in bytes.
     */
    private static int estimateInitialBufferSize(final CharsetEncoder enc, final int charChount) {
        final float first = enc.maxBytesPerChar();
        final float rest = (charChount - 1) * enc.averageBytesPerChar();
        return (int) Math.ceil(first + rest);
    }

    /**
     * Estimate the size needed for remaining characters
     *
     * @param enc       encoder to use for estimates
     * @param charCount number of characters remaining
     * @return estimated size in bytes.
     */
    private static int estimateIncrementalEncodingSize(final CharsetEncoder enc, final int charCount) {
        return (int) Math.ceil(charCount * enc.averageBytesPerChar());
    }
}
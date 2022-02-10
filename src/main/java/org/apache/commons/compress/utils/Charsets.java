package org.apache.commons.compress.utils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.*;

/**
 * Utility methods for charsets.
 *
 * See {@code org.apache.commons.compress.archivers.zip.ZipEncoding}
 *
 * @since 1.21.0.1
 */
public class Charsets {
    private static final char REPLACEMENT = '?';
    private static final byte[] REPLACEMENT_BYTES = {(byte) REPLACEMENT};
    private static final String REPLACEMENT_STRING = String.valueOf(REPLACEMENT);
    private static final char[] HEX_CHARS = new char[]{
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
    };

    /**
     * Returns a charset object for the named charset.
     * If the requested character set cannot be found, UTF-8 will be used instead.
     *
     * Use this method instead of {@code org.apache.commons.compress.archivers.zip.ZipEncodingHelper#getZipEncoding(String)}
     *
     * @param name The name of the encoding. Specify null for the platform's default encoding.
     * @return A charset object for the named encoding
     */
    public static Charset toCharset(String name) {
        if (name == null) {
            return StandardCharsets.UTF_8;
        }
        try {
            return Charset.forName(name);
        } catch (Throwable ignored) {
        }
        return StandardCharsets.UTF_8;
    }

    /**
     * Returns the given charset or the UTF-8 if the given charset is null.
     *
     * @param charset A charset or null.
     * @return the given Charset or the UTF-8 if the given Charset is null
     */
    public static Charset toCharset(Charset charset) {
        return charset == null ? StandardCharsets.UTF_8 : charset;
    }

    /**
     * Check, whether the given string may be losslessly encoded using this
     * encoding.
     *
     * Use this method instead of {@code org.apache.commons.compress.archivers.zip.ZipEncoding#canEncode(String)}
     *
     * @param name A file name or ZIP comment.
     * @return Whether the given name may be encoded with out any losses.
     * @see java.nio.charset.CharsetEncoder#canEncode(CharSequence)
     */
    public static boolean canEncode(Charset charset, String name) {
        return newEncoder(charset).canEncode(name);
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
    public static ByteBuffer encode(Charset charset, String name) throws IOException {
        final CharsetEncoder enc = newEncoder(charset);

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
                    // if the destination buffer isn't over sized, assume that the presence of one
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
        return newDecoder(charset).decode(ByteBuffer.wrap(data)).toString();
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

    private static CharsetEncoder newEncoder(Charset charset) {
        if (charset == StandardCharsets.UTF_8) {
            return charset.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPLACE)
                    .onUnmappableCharacter(CodingErrorAction.REPLACE)
                    .replaceWith(REPLACEMENT_BYTES);
        } else {
            return charset.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
        }
    }

    private static CharsetDecoder newDecoder(Charset charset) {
        if (charset == StandardCharsets.UTF_8) {
            return charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPLACE)
                    .onUnmappableCharacter(CodingErrorAction.REPLACE)
                    .replaceWith(REPLACEMENT_STRING);
        } else {
            return charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
        }
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

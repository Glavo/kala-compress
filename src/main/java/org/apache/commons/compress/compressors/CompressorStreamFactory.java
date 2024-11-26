/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.commons.compress.compressors;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.commons.compress.utils.IOUtils;
import org.jetbrains.annotations.ApiStatus;

/**
 * <p>
 * Creates a Compressor[In|Out]putStreams from names. To add other implementations you should extend CompressorStreamFactory and override the
 * appropriate methods (and call their implementation from super of course).
 * </p>
 * <p>
 * Example (Compressing a file):
 *
 * <pre>
 * final OutputStream out = Files.newOutputStream(output.toPath());
 * CompressorOutputStream cos = new CompressorStreamFactory().createCompressorOutputStream(CompressorStreamFactory.BZIP2, out);
 * IOUtils.copy(Files.newInputStream(input.toPath()), cos);
 * cos.close();
 * </pre>
 * <p>
 * Example (Decompressing a file):
 *
 * <pre>
 * final InputStream is = Files.newInputStream(input.toPath());
 * CompressorInputStream in = new CompressorStreamFactory().createCompressorInputStream(CompressorStreamFactory.BZIP2, is);
 * IOUtils.copy(in, Files.newOutputStream(output.toPath()));
 * in.close();
 * </pre>
 *
 * @Immutable provided that the deprecated method setDecompressConcatenated is not used.
 * @ThreadSafe even if the deprecated method setDecompressConcatenated is used
 */
public class CompressorStreamFactory implements CompressorStreamProvider {

    /**
     * Constant (value {@value}) used to identify the BROTLI compression algorithm.
     *
     * @since 1.14
     */
    public static final String BROTLI = "br";

    /**
     * Constant (value {@value}) used to identify the BZIP2 compression algorithm.
     *
     * @since 1.1
     */
    public static final String BZIP2 = "bzip2";

    /**
     * Constant (value {@value}) used to identify the GZIP compression algorithm.
     *
     * @since 1.1
     */
    public static final String GZIP = "gz";

    /**
     * Constant (value {@value}) used to identify the PACK200 compression algorithm.
     *
     * @since 1.3
     */
    public static final String PACK200 = "pack200";

    /**
     * Constant (value {@value}) used to identify the XZ compression method.
     *
     * @since 1.4
     */
    public static final String XZ = "xz";

    /**
     * Constant (value {@value}) used to identify the LZMA compression method.
     *
     * @since 1.6
     */
    public static final String LZMA = "lzma";

    /**
     * Constant (value {@value}) used to identify the "framed" Snappy compression method.
     *
     * @since 1.7
     */
    public static final String SNAPPY_FRAMED = "snappy-framed";

    /**
     * Constant (value {@value}) used to identify the "raw" Snappy compression method. Not supported as an output stream type.
     *
     * @since 1.7
     */
    public static final String SNAPPY_RAW = "snappy-raw";

    /**
     * Constant (value {@value}) used to identify the traditional UNIX compress method. Not supported as an output stream type.
     *
     * @since 1.7
     */
    public static final String Z = "z";

    /**
     * Constant (value {@value}) used to identify the Deflate compress method.
     *
     * @since 1.9
     */
    public static final String DEFLATE = "deflate";

    /**
     * Constant (value {@value}) used to identify the Deflate64 compress method.
     *
     * @since 1.16
     */
    public static final String DEFLATE64 = "deflate64";

    /**
     * Constant (value {@value}) used to identify the block LZ4 compression method.
     *
     * @since 1.14
     */
    public static final String LZ4_BLOCK = "lz4-block";

    /**
     * Constant (value {@value}) used to identify the frame LZ4 compression method.
     *
     * @since 1.14
     */
    public static final String LZ4_FRAMED = "lz4-framed";

    /**
     * Constant (value {@value}) used to identify the Zstandard compression algorithm. Not supported as an output stream type.
     *
     * @since 1.16
     */
    public static final String ZSTANDARD = "zstd";

    private static final BuiltinCompressor[] BUILTIN_COMPRESSORS;
    private static final Set<String> ALL_NAMES;
    private static final Set<String> OUTPUT_NAMES;

    static {
        final String className = CompressorStreamFactory.class.getName();
        final String packagePrefix = className.substring(0, className.lastIndexOf(".") + 1);

        final String[] builtinCompressorClasses = {
                "brotli.BrotliCompressor",
                "bzip2.BZip2Compressor",
                "deflate.DeflateCompressor",
                "deflate64.Deflate64Compressor",
                "gzip.GzipCompressor",
                "lz4.BlockLZ4Compressor",
                "lz4.FramedLZ4Compressor",
                "lzma.LZMACompressor",
                "pack200.Pack200Compressor",
                "snappy.FramedSnappyCompressor",
                "snappy.SnappyCompressor",
                "xz.XZCompressor",
                "z.ZCompressor",
                "zstandard.ZstdCompressor"
        };

        ArrayList<BuiltinCompressor> compressors = new ArrayList<>();
        HashSet<String> compressorNames = new HashSet<>();
        HashSet<String> outputCompressorNames = new HashSet<>();
        for (String cls : builtinCompressorClasses) {
            Class<?> clazz;

            try {
                clazz = Class.forName(packagePrefix + cls);
            } catch (ClassNotFoundException ignored) {
                continue;
            }

            if (!BuiltinCompressor.class.isAssignableFrom(clazz)) {
                throw new LinkageError(clazz + " is not a subclass of BuiltinCompressor");
            }

            try {
                Constructor<?> constructor = clazz.getDeclaredConstructor();
                constructor.setAccessible(true);
                BuiltinCompressor compressor = (BuiltinCompressor) constructor.newInstance();
                compressors.add(compressor);
                compressorNames.add(compressor.getName());
                if (compressor.isOutputAvailable()) {
                    outputCompressorNames.add(compressor.getName());
                }
            } catch (Throwable e) {
                throw new LinkageError(null, e);
            }
        }

        BUILTIN_COMPRESSORS = compressors.toArray(new BuiltinCompressor[0]);
        ALL_NAMES = Collections.unmodifiableSet(compressorNames);
        OUTPUT_NAMES = Collections.unmodifiableSet(outputCompressorNames);
    }

    /**
     * @since 1.27.1-0
     */
    public static final CompressorStreamFactory DEFAULT = new CompressorStreamFactory();

    /**
     * Detects the type of compressor stream.
     *
     * @param inputStream input stream
     * @return type of compressor stream detected
     * @throws CompressorException      if no compressor stream type was detected or if something else went wrong
     * @throws IllegalArgumentException if stream is null or does not support mark
     * @since 1.14
     */
    public static String detect(final InputStream inputStream) throws CompressorException {
        return detect(inputStream, ALL_NAMES);
    }

    /**
     * Detects the type of compressor stream while limiting the type to the provided set of compressor names.
     *
     * @param inputStream     input stream
     * @param compressorNames compressor names to limit autodetection
     * @return type of compressor stream detected
     * @throws CompressorException      if no compressor stream type was detected or if something else went wrong
     * @throws IllegalArgumentException if stream is null or does not support mark
     */
    static String detect(final InputStream inputStream, final Set<String> compressorNames) throws CompressorException {
        if (inputStream == null) {
            throw new IllegalArgumentException("Stream must not be null.");
        }
        if (compressorNames == null || compressorNames.isEmpty()) {
            throw new IllegalArgumentException("Compressor names cannot be null or empty");
        }
        if (!inputStream.markSupported()) {
            throw new IllegalArgumentException("Mark is not supported.");
        }
        final byte[] signature = new byte[12];
        inputStream.mark(signature.length);
        int signatureLength = -1;
        try {
            signatureLength = IOUtils.readFully(inputStream, signature);
            inputStream.reset();
        } catch (final IOException e) {
            throw new CompressorException("Failed to read signature.", e);
        }

        for (BuiltinCompressor compressor : BUILTIN_COMPRESSORS) {
            if (compressorNames.contains(compressor.getName()) && compressor.matches(signature, signatureLength)) {
                return compressor.getName();
            }
        }
        throw new CompressorException("No Compressor found for the stream signature.");
    }

    private static String toKey(final String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private final SortedMap<String, CompressorStreamProvider> compressorInputStreamProviders = new TreeMap<>();
    private final SortedMap<String, CompressorStreamProvider> compressorOutputStreamProviders = new TreeMap<>();
    private final Set<String> inputStreamCompressorNames = new HashSet<>(ALL_NAMES);
    private final Set<String> outputStreamCompressorNames = new HashSet<>(OUTPUT_NAMES);

    /**
     * If true, decompress until the end of the input. If false, stop after the first stream and leave the input position to point to the next byte after the
     * stream
     */
    private final boolean decompressConcatenated;

    private final int memoryLimitInKb;

    /**
     * Constructs an instance with the decompress Concatenated option set to false.
     */
    public CompressorStreamFactory() {
        this(false);
    }

    /**
     * Constructs an instance with the provided decompress Concatenated option.
     *
     * @param decompressUntilEOF if true, decompress until the end of the input; if false, stop after the first stream and leave the input position to point to
     *                           the next byte after the stream. This setting applies to the gzip, bzip2 and XZ formats only.
     * @since 1.10
     */
    public CompressorStreamFactory(final boolean decompressUntilEOF) {
        this(decompressUntilEOF, -1);
    }

    /**
     * Constructs an instance with the provided decompress Concatenated option.
     *
     * @param decompressUntilEOF if true, decompress until the end of the input; if false, stop after the first stream and leave the input position to point to
     *                           the next byte after the stream. This setting applies to the gzip, bzip2 and XZ formats only.
     * @param memoryLimitInKb    Some streams require allocation of potentially significant byte arrays/tables, and they can offer checks to prevent OOMs on
     *                           corrupt files. Set the maximum allowed memory allocation in KBs.
     * @since 1.14
     */
    public CompressorStreamFactory(final boolean decompressUntilEOF, final int memoryLimitInKb) {
        // Also copy to existing variable so can continue to use that as the
        // current value
        this.decompressConcatenated = decompressUntilEOF;
        this.memoryLimitInKb = memoryLimitInKb;
    }

    /**
     * Creates a compressor input stream from an input stream, auto-detecting the compressor type from the first few bytes of the stream. The InputStream must
     * support marks, like BufferedInputStream.
     *
     * @param in the input stream
     * @return the compressor input stream
     * @throws CompressorException      if the compressor name is not known
     * @throws IllegalArgumentException if the stream is null or does not support mark
     * @since 1.1
     */
    public CompressorInputStream createCompressorInputStream(final InputStream in) throws CompressorException {
        return createCompressorInputStream(detect(in), in);
    }

    /**
     * Creates a compressor input stream from an input stream, auto-detecting the compressor type from the first few bytes of the stream while limiting the
     * detected type to the provided set of compressor names. The InputStream must support marks, like BufferedInputStream.
     *
     * @param in              the input stream
     * @param compressorNames compressor names to limit autodetection
     * @return the compressor input stream
     * @throws CompressorException      if the autodetected compressor is not in the provided set of compressor names
     * @throws IllegalArgumentException if the stream is null or does not support mark
     * @since 1.25.0
     */
    public CompressorInputStream createCompressorInputStream(final InputStream in, final Set<String> compressorNames) throws CompressorException {
        return createCompressorInputStream(detect(in, compressorNames), in);
    }

    /**
     * Creates a compressor input stream from a compressor name and an input stream.
     *
     * @param name of the compressor, i.e. {@value #GZIP}, {@value #BZIP2}, {@value #XZ}, {@value #LZMA}, {@value #PACK200}, {@value #SNAPPY_RAW},
     *             {@value #SNAPPY_FRAMED}, {@value #Z}, {@value #LZ4_BLOCK}, {@value #LZ4_FRAMED}, {@value #ZSTANDARD}, {@value #DEFLATE64} or
     *             {@value #DEFLATE}
     * @param in   the input stream
     * @return compressor input stream
     * @throws CompressorException      if the compressor name is not known or not available, or if there's an IOException or MemoryLimitException thrown during
     *                                  initialization
     * @throws IllegalArgumentException if the name or input stream is null
     */
    public CompressorInputStream createCompressorInputStream(final String name, final InputStream in) throws CompressorException {
        return createCompressorInputStream(name, in, decompressConcatenated);
    }

    @Override
    public CompressorInputStream createCompressorInputStream(final String name, final InputStream in, final boolean actualDecompressConcatenated)
            throws CompressorException {
        if (name == null || in == null) {
            throw new IllegalArgumentException("Compressor name and stream must not be null.");
        }

        for (BuiltinCompressor compressor : BUILTIN_COMPRESSORS) {
            if (compressor.getName().equalsIgnoreCase(name)) {
                return compressor.createCompressorInputStream(in, actualDecompressConcatenated, memoryLimitInKb);
            }
        }

        final CompressorStreamProvider compressorStreamProvider = compressorInputStreamProviders.get(toKey(name));
        if (compressorStreamProvider != null) {
            return compressorStreamProvider.createCompressorInputStream(name, in, actualDecompressConcatenated);
        }
        throw new CompressorException("Compressor: " + name + " not found.");
    }

    /**
     * Creates a compressor output stream from a compressor name and an output stream.
     *
     * @param name the compressor name, i.e. {@value #GZIP}, {@value #BZIP2}, {@value #XZ}, {@value #PACK200}, {@value #SNAPPY_FRAMED}, {@value #LZ4_BLOCK},
     *             {@value #LZ4_FRAMED}, {@value #ZSTANDARD} or {@value #DEFLATE}
     * @param out  the output stream
     * @return the compressor output stream
     * @throws CompressorException      if the archiver name is not known
     * @throws IllegalArgumentException if the archiver name or stream is null
     */
    @Override
    public CompressorOutputStream<?> createCompressorOutputStream(final String name, final OutputStream out) throws CompressorException {
        if (name == null || out == null) {
            throw new IllegalArgumentException("Compressor name and stream must not be null.");
        }

        boolean found = false;
        for (BuiltinCompressor compressor : BUILTIN_COMPRESSORS) {
            if (compressor.getName().equalsIgnoreCase(name)) {
                found = true;
                if (compressor.isOutputAvailable()) {
                    return compressor.createCompressorOutputStream(out);
                } else {
                    break;
                }
            }
        }

        final CompressorStreamProvider compressorStreamProvider = compressorOutputStreamProviders.get(toKey(name));
        if (compressorStreamProvider != null) {
            return compressorStreamProvider.createCompressorOutputStream(name, out);
        }

        if (found) {
            throw new CompressorException("Compressor: " + name + " currently only supports decompression.");
        } else {
            throw new CompressorException("Compressor: " + name + " not found.");
        }
    }

    /**
     * @since 1.27.1-0
     */
    public boolean getDecompressConcatenated() {
        return decompressConcatenated;
    }

    @Override
    public Set<String> getInputStreamCompressorNames() {
        return Collections.unmodifiableSet(inputStreamCompressorNames);
    }

    @Override
    public Set<String> getOutputStreamCompressorNames() {
        return Collections.unmodifiableSet(outputStreamCompressorNames);
    }

    /**
     * @since 1.27.1-0
     */
    @ApiStatus.Experimental
    public CompressorStreamFactory withInstalledProviders() {
       return withProviders(ServiceLoader.load(CompressorStreamProvider.class));
    }

    /**
     * @since 1.27.1-0
     */
    @ApiStatus.Experimental
    public CompressorStreamFactory withInstalledProviders(ClassLoader classLoader) {
        return withProviders(ServiceLoader.load(CompressorStreamProvider.class, classLoader));
    }

    /**
     * @since 1.27.1-0
     */
    @ApiStatus.Experimental
    public CompressorStreamFactory withProviders(Iterable<? extends CompressorStreamProvider> providers) {
        CompressorStreamFactory result = new CompressorStreamFactory(this.decompressConcatenated, this.memoryLimitInKb);
        for (CompressorStreamProvider provider : providers) {
            for (String name : provider.getInputStreamCompressorNames()) {
                result.compressorInputStreamProviders.put(toKey(name), provider);
                result.inputStreamCompressorNames.add(name);
            }
            for (String name : provider.getOutputStreamCompressorNames()) {
                result.compressorOutputStreamProviders.put(toKey(name), provider);
                result.outputStreamCompressorNames.add(name);
            }
        }
        return result;
    }

    /**
     * This is an internal class and should not be used directly.
     *
     * @author Glavo
     * @since 1.27.1-0
     */
    public abstract static class BuiltinCompressor {

        private final String name;
        private final String unavailablePrompt;

        protected BuiltinCompressor(String name) {
            this.name = name;
            this.unavailablePrompt = "";
        }

        protected BuiltinCompressor(String name, String dependencyName, String url) {
            this.name = name;
            this.unavailablePrompt = " In addition to Apache Commons Compress you need the " + dependencyName + " library - see " + url;
        }

        public String getName() {
            return name;
        }

        @SuppressWarnings("BooleanMethodIsAlwaysInverted")
        public boolean isCompressionAvailable() {
            return true;
        }

        public boolean isOutputAvailable() {
            return false;
        }

        public boolean matches(final byte[] signature, final int length) {
            return false;
        }

        protected abstract CompressorInputStream createCompressorInputStreamImpl(InputStream in, boolean decompressUntilEOF, int memoryLimitInKb) throws IOException, CompressorException;

        protected CompressorOutputStream<?> createCompressorOutputImpl(OutputStream out) throws IOException {
            throw new CompressorException("Currently " + this + " does not support compression");
        }

        public final CompressorInputStream createCompressorInputStream(InputStream in, boolean decompressUntilEOF, int memoryLimitInKb) throws CompressorException {
            if (!isCompressionAvailable()) {
                throw new CompressorException(this + " is not available." + unavailablePrompt);
            }
            try {
                return createCompressorInputStreamImpl(in, decompressUntilEOF, memoryLimitInKb);
            } catch (IOException e) {
                throw new CompressorException("Could not create CompressorInputStream", e);
            }
        }

        public final CompressorOutputStream<?> createCompressorOutputStream(OutputStream out) throws CompressorException {
            if (!isCompressionAvailable()) {
                throw new CompressorException(this + " is not available." + unavailablePrompt);
            }
            try {
                return createCompressorOutputImpl(out);
            } catch (IOException e) {
                throw new CompressorException("Could not create CompressorOutputStream", e);
            }
        }

        @Override
        public String toString() {
            return name + " compressor";
        }
    }
}

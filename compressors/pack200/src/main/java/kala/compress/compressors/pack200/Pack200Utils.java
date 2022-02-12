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

package kala.compress.compressors.pack200;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;

import kala.compress.compressors.CompressorException;

/**
 * Utility methods for Pack200.
 *
 * @ThreadSafe
 * @since 1.3
 */
public class Pack200Utils {
    private Pack200Utils() { }

    public static final String JDK_PACK200 = "java.util.jar.Pack200";
    public static final String GLAVO_PACK200 = "org.glavo.pack200.Pack200";
    public static final String COMMONS_COMPRESS_PACK200 = "org.apache.commons.compress.java.util.jar.Pack200";
    public static final String IO_PACK200 = "io.pack200.Pack200";

    private static final String[] BUILTIN_PROVIDERS = {
        JDK_PACK200, GLAVO_PACK200, COMMONS_COMPRESS_PACK200, IO_PACK200
    };

    enum CachedAvailability {
        DONT_CACHE, CACHED_AVAILABLE, CACHED_UNAVAILABLE
    }

    private static volatile Pack200Impl pack200Impl = null;
    private static volatile String pack200Provider;

    /**
     * Whether to cache the result of the Pack200 check.
     *
     * <p>This defaults to {@code true}.</p>
     * @param doCache whether to cache the result
     * @since  1.21.0.1
     */
    public static synchronized void setCachePack200Availablity(final boolean doCache) {
        if (doCache) {
            if (pack200Impl == Pack200Impl.DONT_CACHE) {
                pack200Impl = null;
            }
        } else {
            pack200Impl = Pack200Impl.DONT_CACHE;
        }
    }

    /**
     * @since 1.21.0.1
     */
    public static synchronized void setPack200Provider(String provider) {
        if (Objects.equals(provider, pack200Provider)) {
            return;
        }

        if (provider != null && pack200Impl != null && pack200Impl != Pack200Impl.DONT_CACHE && !pack200Impl.provider.equals(provider)) {
            pack200Impl = null;
        }
        pack200Provider = provider;
    }

    /**
     * Are the classes required to support Pack200 compression available?
     *
     * @return true if the classes required to support Pack200 compression are available
     * @since  1.21.0.1
     */
    public static boolean isPack200Available() {
        return Pack200Impl.isAvailable(getPack200Impl());
    }

    static Pack200Impl getPack200ImplChecked() {
        Pack200Impl impl = getPack200Impl();
        if (!Pack200Impl.isAvailable(impl)) {
            throw new CompressorException("Pack200 compression is not available");
        }
        return impl;
    }

    private static Pack200Impl getPack200Impl() {
        Pack200Impl impl;
        if (Pack200Impl.isAvailable(impl = Pack200Utils.pack200Impl)) {
            return impl;
        }

        synchronized (Pack200Utils.class) {
            if (Pack200Impl.isAvailable(impl = Pack200Utils.pack200Impl)) {
                return impl;
            }

            impl = internalSearchPack200();
            if (impl != null && Pack200Utils.pack200Impl != Pack200Impl.DONT_CACHE) {
                Pack200Utils.pack200Impl = impl;
            }
            return impl;
        }
    }

    private static Pack200Impl internalSearchPack200() {
        if (pack200Provider != null) {
            return internalSearchPack200(pack200Provider);
        }

        for (String provider : BUILTIN_PROVIDERS) {
            Pack200Impl impl = internalSearchPack200(provider);
            if (impl != null) {
                return impl;
            }
        }
        return null;
    }

    private static Pack200Impl internalSearchPack200(String provider) {
        try {
            Class<?> pack200Class = Class.forName(provider);
            Class<?> packerClass = Class.forName(provider + "$Packer");
            Class<?> unpackerClass = Class.forName(provider + "$Unpacker");

            final MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            final MethodType propertiesType = MethodType.methodType(SortedMap.class);

            MethodHandle newPackerHandle = lookup.findStatic(pack200Class, "newPacker", MethodType.methodType(packerClass));
            MethodHandle packerPackHandle = lookup.findVirtual(packerClass, "pack", MethodType.methodType(void.class, JarInputStream.class, OutputStream.class));
            MethodHandle packerPropertiesHandle = lookup.findVirtual(packerClass, "properties", propertiesType);

            MethodHandle newUnpackerHandle = lookup.findStatic(pack200Class, "newUnpacker", MethodType.methodType(unpackerClass));
            MethodHandle unpackerUnpackHandle = lookup.findVirtual(unpackerClass, "unpack", MethodType.methodType(void.class, InputStream.class, JarOutputStream.class));
            MethodHandle unpackerPropertiesHandle = lookup.findVirtual(unpackerClass, "properties", propertiesType);

            return new Pack200Impl(provider, newPackerHandle, packerPackHandle, packerPropertiesHandle, newUnpackerHandle, unpackerUnpackHandle, unpackerPropertiesHandle);
        } catch (Throwable ignored) {
        }

        return null;
    }

    /**
     * Normalizes a JAR archive in-place so it can be safely signed
     * and packed.
     *
     * <p>As stated in <a
     * href="https://download.oracle.com/javase/1.5.0/docs/api/java/util/jar/Pack200.Packer.html">Pack200.Packer's</a>
     * javadocs applying a Pack200 compression to a JAR archive will
     * in general make its signatures invalid.  In order to prepare a
     * JAR for signing it should be "normalized" by packing and
     * unpacking it.  This is what this method does.</p>
     *
     * <p>Note this methods implicitly sets the segment length to
     * -1.</p>
     *
     * @param jar the JAR archive to normalize
     * @throws IOException if reading or writing fails
     */
    public static void normalize(final File jar)
        throws IOException {
        normalize(jar, jar, null);
    }

    /**
     * Normalizes a JAR archive in-place so it can be safely signed
     * and packed.
     *
     * <p>As stated in <a
     * href="https://download.oracle.com/javase/1.5.0/docs/api/java/util/jar/Pack200.Packer.html">Pack200.Packer's</a>
     * javadocs applying a Pack200 compression to a JAR archive will
     * in general make its signatures invalid.  In order to prepare a
     * JAR for signing it should be "normalized" by packing and
     * unpacking it.  This is what this method does.</p>
     *
     * <p>Note this methods implicitly sets the segment length to
     * -1.</p>
     *
     * @param jar the JAR archive to normalize
     * @throws IOException if reading or writing fails
     * @since 1.21.0.1
     */
    public static void normalize(final Path jar)
            throws IOException {
        normalize(jar, jar, null);
    }

    /**
     * Normalizes a JAR archive in-place so it can be safely signed
     * and packed.
     *
     * <p>As stated in <a
     * href="https://download.oracle.com/javase/1.5.0/docs/api/java/util/jar/Pack200.Packer.html">Pack200.Packer's</a>
     * javadocs applying a Pack200 compression to a JAR archive will
     * in general make its signatures invalid.  In order to prepare a
     * JAR for signing it should be "normalized" by packing and
     * unpacking it.  This is what this method does.</p>
     *
     * @param jar the JAR archive to normalize
     * @param props properties to set for the pack operation.  This
     * method will implicitly set the segment limit to -1.
     * @throws IOException if reading or writing fails
     */
    public static void normalize(final File jar, final Map<String, String> props)
        throws IOException {
        normalize(jar, jar, props);
    }

    /**
     * Normalizes a JAR archive in-place so it can be safely signed
     * and packed.
     *
     * <p>As stated in <a
     * href="https://download.oracle.com/javase/1.5.0/docs/api/java/util/jar/Pack200.Packer.html">Pack200.Packer's</a>
     * javadocs applying a Pack200 compression to a JAR archive will
     * in general make its signatures invalid.  In order to prepare a
     * JAR for signing it should be "normalized" by packing and
     * unpacking it.  This is what this method does.</p>
     *
     * @param jar the JAR archive to normalize
     * @param props properties to set for the pack operation.  This
     * method will implicitly set the segment limit to -1.
     * @throws IOException if reading or writing fails
     * @since 1.21.0.1
     */
    public static void normalize(final Path jar, final Map<String, String> props)
            throws IOException {
        normalize(jar, jar, props);
    }

    /**
     * Normalizes a JAR archive so it can be safely signed and packed.
     *
     * <p>As stated in <a
     * href="https://download.oracle.com/javase/1.5.0/docs/api/java/util/jar/Pack200.Packer.html">Pack200.Packer's</a>
     * javadocs applying a Pack200 compression to a JAR archive will
     * in general make its signatures invalid.  In order to prepare a
     * JAR for signing it should be "normalized" by packing and
     * unpacking it.  This is what this method does.</p>
     *
     * <p>This method does not replace the existing archive but creates
     * a new one.</p>
     *
     * <p>Note this methods implicitly sets the segment length to
     * -1.</p>
     *
     * @param from the JAR archive to normalize
     * @param to the normalized archive
     * @throws IOException if reading or writing fails
     */
    public static void normalize(final File from, final File to)
        throws IOException {
        normalize(from.toPath(), to.toPath(), null);
    }

    /**
     * Normalizes a JAR archive so it can be safely signed and packed.
     *
     * <p>As stated in <a
     * href="https://download.oracle.com/javase/1.5.0/docs/api/java/util/jar/Pack200.Packer.html">Pack200.Packer's</a>
     * javadocs applying a Pack200 compression to a JAR archive will
     * in general make its signatures invalid.  In order to prepare a
     * JAR for signing it should be "normalized" by packing and
     * unpacking it.  This is what this method does.</p>
     *
     * <p>This method does not replace the existing archive but creates
     * a new one.</p>
     *
     * <p>Note this methods implicitly sets the segment length to
     * -1.</p>
     *
     * @param from the JAR archive to normalize
     * @param to the normalized archive
     * @throws IOException if reading or writing fails
     * @since 1.21.0.1
     */
    public static void normalize(final Path from, final Path to)
            throws IOException {
        normalize(from, to, null);
    }

    /**
     * Normalizes a JAR archive so it can be safely signed and packed.
     *
     * <p>As stated in <a
     * href="https://download.oracle.com/javase/1.5.0/docs/api/java/util/jar/Pack200.Packer.html">Pack200.Packer's</a>
     * javadocs applying a Pack200 compression to a JAR archive will
     * in general make its signatures invalid.  In order to prepare a
     * JAR for signing it should be "normalized" by packing and
     * unpacking it.  This is what this method does.</p>
     *
     * <p>This method does not replace the existing archive but creates
     * a new one.</p>
     *
     * @param from the JAR archive to normalize
     * @param to the normalized archive
     * @param props properties to set for the pack operation.  This
     * method will implicitly set the segment limit to -1.
     * @throws IOException if reading or writing fails
     */
    public static void normalize(final File from, final File to, Map<String, String> props)
            throws IOException {
        normalize(from.toPath(), to.toPath(), props);
    }

    /**
     * Normalizes a JAR archive so it can be safely signed and packed.
     *
     * <p>As stated in <a
     * href="https://download.oracle.com/javase/1.5.0/docs/api/java/util/jar/Pack200.Packer.html">Pack200.Packer's</a>
     * javadocs applying a Pack200 compression to a JAR archive will
     * in general make its signatures invalid.  In order to prepare a
     * JAR for signing it should be "normalized" by packing and
     * unpacking it.  This is what this method does.</p>
     *
     * <p>This method does not replace the existing archive but creates
     * a new one.</p>
     *
     * @param from the JAR archive to normalize
     * @param to the normalized archive
     * @param props properties to set for the pack operation.  This
     * method will implicitly set the segment limit to -1.
     * @throws IOException if reading or writing fails
     * @since 1.21.0.1
     */
    public static void normalize(final Path from, final Path to, Map<String, String> props)
            throws IOException {
        Pack200Impl pack200 = getPack200ImplChecked();
        if (props == null) {
            props = new HashMap<>();
        }
        props.put(Pack200Constants.Packer.SEGMENT_LIMIT, "-1");
        final Path tempFile = Files.createTempFile("commons-compress", "pack200normalize");
        try {
            try (OutputStream fos = Files.newOutputStream(tempFile);
                 JarInputStream jarFile = new JarInputStream(Files.newInputStream(from))) {

                final Object packer = pack200.newPacker();
                pack200.getPackerProperties(packer).putAll(props);
                pack200.pack(packer, jarFile, fos);
            }

            final Object unpacker = pack200.newUnpacker();
            try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(to));
                 InputStream fis = Files.newInputStream(tempFile)) {
                pack200.unpack(unpacker, fis, jos);
            }
        } finally {
            try {
                Files.deleteIfExists(tempFile);
            } catch (Throwable ignored) {
                try {
                    tempFile.toFile().deleteOnExit();
                } catch (Throwable ignored1) {
                }
            }
        }
    }
}

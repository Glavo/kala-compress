package kala.compress.compressors.pack200;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.invoke.MethodHandle;
import java.util.SortedMap;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;

@SuppressWarnings("unchecked")
final class Pack200Impl {
    static final Pack200Impl DONT_CACHE =
            new Pack200Impl(null, null, null, null, null, null, null);

    final String provider;

    final MethodHandle newPackerHandle;
    final MethodHandle packerPackHandle;
    final MethodHandle packerPropertiesHandle;

    final MethodHandle newUnpackerHandle;
    final MethodHandle unpackerUnpackHandle;
    final MethodHandle unpackerPropertiesHandle;

    Pack200Impl(String provider,
                MethodHandle newPackerHandle, MethodHandle packerPackHandle, MethodHandle packerPropertiesHandle,
                MethodHandle newUnpackerHandle, MethodHandle unpackerUnpackHandle, MethodHandle unpackerPropertiesHandle) {
        this.provider = provider;

        this.newPackerHandle = newPackerHandle;
        this.packerPackHandle = packerPackHandle;
        this.packerPropertiesHandle = packerPropertiesHandle;

        this.newUnpackerHandle = newUnpackerHandle;
        this.unpackerUnpackHandle = unpackerUnpackHandle;
        this.unpackerPropertiesHandle = unpackerPropertiesHandle;
    }

    Object newPacker() {
        try {
            return newPackerHandle.invoke();
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    SortedMap<String, String> getPackerProperties(Object packer) {
        try {
            return (SortedMap<String, String>) packerPropertiesHandle.invoke(packer);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    void pack(Object packer, JarInputStream in, OutputStream out) throws IOException {
        try {
            packerPackHandle.invoke(packer, in, out);
        } catch (IOException | RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    Object newUnpacker() {
        try {
            return newUnpackerHandle.invoke();
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    SortedMap<String, String> getUnpackerProperties(Object unpacker) {
        try {
            return (SortedMap<String, String>) unpackerPropertiesHandle.invoke(unpacker);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    void unpack(Object unpacker, InputStream in, JarOutputStream out) throws IOException {
        try {
            unpackerUnpackHandle.invoke(unpacker, in, out);
        } catch (IOException | RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    static boolean isAvailable(Pack200Impl impl) {
        return impl != null && impl != DONT_CACHE;
    }
}

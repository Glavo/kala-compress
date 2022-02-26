package kala.compress.filesystems;

import java.nio.charset.Charset;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.util.*;

public class ArchiveFileSystemOptions {
    protected final Map<String, ?> env;

    protected ArchiveFileSystemOptions(Map<String, ?> env) {
        this.env = env;
    }

    public static final String CREATE = "create";
    public static final String ENABLE_POSIX_FILE_ATTRIBUTES = "enablePosixFileAttributes";
    public static final String DEFAULT_OWNER = "defaultOwner";
    public static final String DEFAULT_GROUP = "defaultGroup";
    public static final String DEFAULT_PERMISSIONS = "defaultPermissions";
    public static final String COMPRESSION_METHOD = "compressionMethod";
    public static final String ENCODING = "encoding";
    public static final String USE_TEMP_FILE = "useTempFile";

    public static Map<String, ?> empty() {
        return Collections.emptyMap();
    }

    public static Map<String, ?> of() {
        return empty();
    }

    public static Map<String, ?> of(String k1, Object v1) {
        return Collections.singletonMap(k1, v1);
    }

    public static Map<String, ?> of(Object... kv) {
        final int length = kv.length;
        if (length % 2 != 0) {
            throw new IllegalArgumentException();
        }

        if (length == 0) {
            return empty();
        }

        if (length == 2) {
            return of((String) kv[0], kv[1]);
        }

        Map<String, Object> res = new TreeMap<>();

        for (int i = 0; i < length; ) {
            String key = Objects.requireNonNull((String) kv[i++]);
            Object value = kv[i++];

            res.put(key, value);
        }

        return Collections.unmodifiableMap(res);
    }

    public boolean getBoolean(String key) {
        Object value = env.get(key);
        return "true".equals(value) || Boolean.TRUE.equals(value);
    }

    public Charset getEncoding() {
        return getEncoding(null);
    }

    public Charset getEncoding(Charset defaultValue) {
        return getEncoding(ENCODING, defaultValue);
    }

    public Charset getEncoding(String key, Charset defaultValue) {
        Object value = env.get(key);
        if (value == null) {
            return defaultValue;
        }

        if (value instanceof Charset) {
            return ((Charset) value);
        }

        if (value instanceof String) {
            return Charset.forName((String) value);
        }

        throw new IllegalArgumentException("Value for property " + key + " must be of type " + String.class + " or " + Charset.class);
    }

    public UserPrincipal getDefaultOwner() {
        Object owner = env.get(DEFAULT_OWNER);
        if (owner == null) {
            return null;
        }

        if (owner instanceof String) {
            String o = (String) owner;

            if (o.isEmpty()) {
                throw new IllegalArgumentException("Value for property " + DEFAULT_OWNER + " must not be empty.");
            }
            return () -> o;
        }
        if (owner instanceof UserPrincipal) {
            return (UserPrincipal) owner;
        }

        throw new IllegalArgumentException("Value for property " + DEFAULT_OWNER + " must be of type " + String.class + " or " + UserPrincipal.class);
    }

    public GroupPrincipal getDefaultGroup() {
        Object group = env.get(DEFAULT_GROUP);
        if (group == null) {
            return null;
        }

        if (group instanceof String) {
            String g = (String) group;

            if (g.isEmpty()) {
                throw new IllegalArgumentException("Value for property " + DEFAULT_GROUP + " must not be empty.");
            }
            return () -> g;
        }
        if (group instanceof GroupPrincipal) {
            return (GroupPrincipal) group;
        }

        throw new IllegalArgumentException("Value for property " + DEFAULT_GROUP + " must be of type " + String.class + " or " + GroupPrincipal.class);
    }

    public Set<PosixFilePermission> getDefaultPermissions() {
        Object permissions = env.get(DEFAULT_PERMISSIONS);

        if (permissions == null) {
            return null;
        }

        if (permissions instanceof String) {
            return PosixFilePermissions.fromString((String) permissions);
        }
        if (!(permissions instanceof Set)) {
            throw new IllegalArgumentException("Value for property " + DEFAULT_PERMISSIONS + " must be of type " + String.class + " or " + Set.class);
        }
        Set<PosixFilePermission> perms = new HashSet<>();
        for (Object permission : (Set<?>) permissions) {
            if (permission instanceof PosixFilePermission) {
                perms.add((PosixFilePermission) permission);
            } else {
                throw new IllegalArgumentException(DEFAULT_PERMISSIONS + " must only contain objects of type " + PosixFilePermission.class);
            }
        }
        return perms;
    }
}

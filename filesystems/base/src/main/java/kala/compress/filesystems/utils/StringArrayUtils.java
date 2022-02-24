package kala.compress.filesystems.utils;

public class StringArrayUtils {
    public static final String[] EMPTY_STRING_ARRAY = new String[0];

    public static String[] single(String value) {
        return new String[]{value};
    }

    public static String[] concat(String[] arr1, String[] arr2) {
        String[] res = new String[arr1.length + arr2.length];
        System.arraycopy(arr1, 0, res, 0 , arr1.length);
        System.arraycopy(arr2, 0, res, arr1.length , arr2.length);
        return res;
    }
}

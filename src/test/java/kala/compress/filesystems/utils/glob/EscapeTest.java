package kala.compress.filesystems.utils.glob;

import org.junit.Test;

import static org.junit.Assert.*;

public class EscapeTest {

    @Test
    public void testUnicode() {
        String pattern = "foo\\u0010bar";

        MatchingEngine matchingEngine = GlobPattern.compile(pattern, '%', '_', GlobPattern.CASE_INSENSITIVE | GlobPattern.HANDLE_ESCAPES);
        assertTrue(matchingEngine.matches("foo\u0010bar"));
    }

    @Test
    public void testBadUnicode() {
        String pattern = "foo\\u001zbar";

        assertThrows(RuntimeException.class, () -> GlobPattern.compile(pattern, '%', '_', GlobPattern.CASE_INSENSITIVE | GlobPattern.HANDLE_ESCAPES));
    }

    @Test
    public void testBadUnicodeAtEnd() {
        String pattern = "foo\\u001";

        assertThrows(RuntimeException.class, () -> GlobPattern.compile(pattern, '%', '_', GlobPattern.CASE_INSENSITIVE | GlobPattern.HANDLE_ESCAPES));
    }

    @Test
    public void testBadEscape() {
        String pattern = "foo\\e";

        assertThrows(RuntimeException.class, () -> GlobPattern.compile(pattern, '%', '_', GlobPattern.CASE_INSENSITIVE | GlobPattern.HANDLE_ESCAPES));
    }

}

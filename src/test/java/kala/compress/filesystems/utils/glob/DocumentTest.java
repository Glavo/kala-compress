package kala.compress.filesystems.utils.glob;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests to keep us honest with our examples in the README.md
 */
public class DocumentTest {

    @Test
    public void example1() {
        MatchingEngine m = GlobPattern.compile("dog*cat\\*goat??");

        assertTrue(m.matches("dog horse cat*goat!~"));
        assertTrue(m.matches("dogcat*goat.."));
        assertFalse(m.matches("dog catgoat!/"));
    }

    @Test
    public void example2() {
        MatchingEngine m = GlobPattern.compile("dog%cat\\%goat__", '%', '_', GlobPattern.HANDLE_ESCAPES);

        assertTrue(m.matches("dog horse cat%goat!~"));
        assertTrue(m.matches("dogcat%goat.."));
        assertFalse(m.matches("dog catgoat!/"));
    }
}

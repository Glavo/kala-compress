package kala.compress.filesystems.utils.glob;

import org.junit.Before;
import org.junit.Test;

/**
 * Test compiling the pattern '' into a EmptyEngine
 */
public class EmptyEngineTest {

    private TestUtils testUtils;

    @Before
    public void init() {
        testUtils = new TestUtils('%', '_', GlobPattern.CASE_INSENSITIVE | GlobPattern.HANDLE_ESCAPES);
    }

    @Test
    public void null_noMatch() {
        testUtils.matches(EmptyOnlyEngine.class, "", null, false);
    }

    @Test
    public void empty_match() {
        testUtils.matches(EmptyOnlyEngine.class, "", "", true);
    }

    @Test
    public void length1_noMatch() {
        testUtils.matches(EmptyOnlyEngine.class, "", "a", false);
    }

    @Test
    public void length2_noMatch() {
        testUtils.matches(EmptyOnlyEngine.class, "", "ab", false);
    }

}

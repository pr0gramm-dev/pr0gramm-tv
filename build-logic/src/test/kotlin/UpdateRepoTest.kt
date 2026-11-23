import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UpdateRepoTest {
    @Test fun `owner and repo in GitHub form are valid`() {
        listOf(UpdateRepo.DEFAULT, "a/b", "a/b.c_d-e", "Owner9/.repo", "o-w-n/r..x").forEach {
            assertTrue(UpdateRepo.valid(it), it)
        }
    }

    @Test fun `anything else is refused`() {
        listOf(
            "", "owner", "owner/", "/repo", "-x/y", "x-/y", "x--y/z", "o/r/x", "o/..", "o/.", "o/r\"", "o/r x", "o/r?x",
            "o/r#x", "o/r\n",
        ).forEach { assertFalse(UpdateRepo.valid(it), it) }
    }
}

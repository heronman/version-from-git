import net.agl.gradle.Version
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertFalse

class VersionTest {
    @Test
    fun `test from string to string (clean)`() {
        val source = "1.2.3"
        val fromString = Version.parse(source)
        val toString = fromString.toString()
        assert(toString == source)
    }

    @Test
    fun `test from string to string (prefixed)`() {
        val source = "v1.2.3-SNAPSHOT"
        val fromString = Version.parse(source)
        val toString = fromString.toString()
        assert(toString == source)
    }

    @Test
    fun `test from string to string (not prefixed)`() {
        val source = "1.2.3-SNAPSHOT"
        val fromString = Version.parse(source)
        val toString = fromString.toString()
        assert(toString == source)
    }

    @Test
    fun `correctly parsed version with 10 digit bocks`() {
        val source = "v1.2.3.4.5.6.7.8.9.0-SNAPSHOT"
        val fromString = Version.parse(source)
        val toString = fromString.toString()
        assert(toString == source)
    }

    @Test
    fun `empty digital block results in exception`() {
        val source = "1.2.-SNAPSHOT"
        val ex = assertThrows<IllegalArgumentException> { Version.parse(source) }
        assert(ex.message == "Unexpected character '-' at position '4'")
    }

    @Test
    fun `unsupported character results in exception`() {
        val source = "1.2@-SNAPSHOT"
        val ex = assertThrows<IllegalArgumentException> { Version.parse(source) }
        assert(ex.message == "Unexpected character '@' at position '3'")
    }

    @Test
    fun `digital part absence results in exception`() {
        val source = "SNAPSHOT"
        val ex = assertThrows<IllegalArgumentException> { Version.parse(source) }
        assert(ex.message == "Unexpected character 'S' at position '0'")
    }

    @Test
    fun `empty text results in empty version`() {
        val source = ""
        val version = Version.parse(source)
        assert(version.parts.isEmpty() && version.suffix == null && version.version == "")
    }

    @Test
    fun `compares correctly`() {
        assert(Version.parse("1.2.3") == Version.parse("1.2.3"))
        assert(Version.parse("1.2.3") > Version.parse(""))
        assert(Version.parse("1.2.3") > Version.parse("v"))

        // contra-intuitive behavior:
        assert(Version.parse("1.2.3") != Version.parse("v1.2.3"))
        assertFalse(Version.parse("1.2.3") > Version.parse("v1.2.3"))
        assertFalse(Version.parse("1.2.3") < Version.parse("v1.2.3"))

        assert(Version.parse("0") > Version.parse(""))
        assert(Version.parse("1.2.0") > Version.parse("1.2"))

        assert(Version.parse("1.2.3") < Version.parse("1.2.4"))
        assert(Version.parse("1.2.3") < Version.parse("1.3.3"))
        assert(Version.parse("1.2.3") < Version.parse("2.2.3"))
        assert(Version.parse("1.2.3") < Version.parse("1.2.3-RELEASE"))
        assert(Version.parse("1.2.3") > Version.parse("1.2.3-SNAPSHOT"))
        assert(Version.parse("1.2.3-RELEASE") > Version.parse("1.2.3-SNAPSHOT"))
        assert(Version.parse("1.2.3") > Version.parse("1.2.3-FOO"))
        assert(Version.parse("1.2.3-ABC") < Version.parse("1.2.3-ABD"))
    }

}

import net.agl.gradle.DEFAULT_FALLBACK_VERSION
import net.agl.gradle.versionFromGit
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path

class VersionFromGitTest {
    val tmp: Path

    init {
        tmp = Files.createTempDirectory("version-from-git-test-")
        Runtime.getRuntime().addShutdownHook(Thread { cleanup() })
    }

    fun cleanup() {
        tmp.toFile().deleteRecursively()
    }

    fun runCommand(
        cmd: List<String>,
        cwd: File = File("./"),
        out: OutputStream? = null,
        err: OutputStream? = null,
        inp: InputStream? = null
    ): Int {
        val pb = ProcessBuilder(cmd).directory(cwd)
        if (out == null) {
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD)
        }
        if (err == null) {
            pb.redirectError(ProcessBuilder.Redirect.DISCARD)
        }
        val proc = pb.start()
        val threads = arrayOf(
            out?.let { Thread { proc.inputStream.transferTo(it) }.apply { start() } },
            err?.let { Thread { proc.errorStream.transferTo(it) }.apply { start() } },
            inp?.let { Thread { it.transferTo(proc.outputStream) }.apply { start() } }
        )
        val result = proc.waitFor()
        threads.forEach { it?.join() }
        return result
    }

    //

    @Test
    fun `with no git repository, the version falls back to the default one`() {
        val dir = Files.createTempDirectory(tmp, "test-git-repo").toFile()

        val version = versionFromGit(dir.absolutePath)
        assert(version == DEFAULT_FALLBACK_VERSION)
    }

    @Test
    fun `on an empty repository, the version falls back to the default one`() {
        val dir = Files.createTempDirectory(tmp, "test-git-repo").toFile()
        runCommand(listOf("git", "init"), dir)

        val version = versionFromGit(dir.absolutePath)
        assert(version == DEFAULT_FALLBACK_VERSION)
    }

    @Test
    fun `with no tags, the version falls back to the default one`() {
        val dir = Files.createTempDirectory(tmp, "test-git-repo").toFile()
        runCommand(listOf("git", "init"), dir)
        runCommand(listOf("git", "branch", "-m", "master"), dir)
        File(dir, "test.txt").writeText("some-text")
        runCommand(listOf("git", "add", "test.txt"), dir)
        runCommand(listOf("git", "commit", "-m", "initial commit"), dir)

        val version = versionFromGit(dir.absolutePath)
        assert(version == DEFAULT_FALLBACK_VERSION)
    }

    //

    @Test
    fun `version tag is on HEAD`() {
        val dir = Files.createTempDirectory(tmp, "test-git-repo").toFile()
        runCommand(listOf("git", "init"), dir)
        runCommand(listOf("git", "branch", "-m", "master"), dir)
        File(dir, "test.txt").writeText("some-text")
        runCommand(listOf("git", "add", "test.txt"), dir)
        runCommand(listOf("git", "commit", "-m", "initial commit"), dir)
        runCommand(listOf("git", "tag", "-a", "-m", "version tag", "1.1.1"), dir)

        val version = versionFromGit(dir.absolutePath)
        assert(version == "1.1.1")
    }

    @Test
    fun `version tag is before HEAD, commits number is correct`() {
        val dir = Files.createTempDirectory(tmp, "test-git-repo").toFile()
        runCommand(listOf("git", "init"), dir)
        runCommand(listOf("git", "branch", "-m", "master"), dir)
        File(dir, "test.txt").writeText("some-text")
        runCommand(listOf("git", "add", "test.txt"), dir)
        runCommand(listOf("git", "commit", "-m", "initial commit"), dir)
        runCommand(listOf("git", "tag", "-a", "-m", "version tag", "1.1.1"), dir)

        for (i in 1..2) {
            File(dir, "test${i}.txt").writeText("some-text")
            runCommand(listOf("git", "add", "test${i}.txt"), dir)
            runCommand(listOf("git", "commit", "-m", "add test${i}.txt"), dir)

            val version = versionFromGit(dir.absolutePath)
            assert(version.startsWith("1.1.1-${i}-g"))
        }
    }

    //

    @Test
    fun `multiple version tags, straight order`() {
        val dir = Files.createTempDirectory(tmp, "test-git-repo").toFile()
        runCommand(listOf("git", "init"), dir)
        runCommand(listOf("git", "branch", "-m", "master"), dir)
        File(dir, "test.txt").writeText("some-text")
        runCommand(listOf("git", "add", "test.txt"), dir)
        runCommand(listOf("git", "commit", "-m", "initial commit"), dir)
        runCommand(listOf("git", "tag", "-a", "-m", "version tag", "1.1.1"), dir)

        File(dir, "test2.txt").writeText("some-text")
        runCommand(listOf("git", "add", "test2.txt"), dir)
        runCommand(listOf("git", "commit", "-m", "add test2.txt"), dir)
        runCommand(listOf("git", "tag", "-a", "-m", "version tag", "1.1.2"), dir)

        val version = versionFromGit(dir.absolutePath)
        assert(version.startsWith("1.1.2"))
    }

    @Test
    fun `version tag is selected by maximum version number, not by commits order`() {
        val dir = Files.createTempDirectory(tmp, "test-git-repo").toFile()
        runCommand(listOf("git", "init"), dir)
        runCommand(listOf("git", "branch", "-m", "master"), dir)
        File(dir, "test.txt").writeText("some-text")
        runCommand(listOf("git", "add", "test.txt"), dir)
        runCommand(listOf("git", "commit", "-m", "initial commit"), dir)
        runCommand(listOf("git", "tag", "-a", "-m", "version tag", "1.1.1"), dir)

        File(dir, "test2.txt").writeText("some-text")
        runCommand(listOf("git", "add", "test2.txt"), dir)
        runCommand(listOf("git", "commit", "-m", "add test2.txt"), dir)
        runCommand(listOf("git", "tag", "-a", "-m", "version tag", "1.0.1"), dir)

        val version = versionFromGit(dir.absolutePath)
        assert(version.startsWith("1.1.1-1-g"))
    }

    @Test
    fun `only tags on commits in the current branch are eligible for version building`() {
        val dir = Files.createTempDirectory(tmp, "test-git-repo").toFile()
        runCommand(listOf("git", "init"), dir)
        runCommand(listOf("git", "branch", "-m", "master"), dir)
        File(dir, "test.txt").writeText("some-text")
        runCommand(listOf("git", "add", "test.txt"), dir)
        runCommand(listOf("git", "commit", "-m", "initial commit"), dir)
        runCommand(listOf("git", "tag", "-a", "-m", "version tag", "1.1.1"), dir)

        runCommand(listOf("git", "checkout", "-b", "alt"), dir)

        val versionAlt = versionFromGit(dir.absolutePath)
        assert(versionAlt == "1.1.1")

        File(dir, "test2.txt").writeText("some-text")
        runCommand(listOf("git", "add", "test2.txt"), dir)
        runCommand(listOf("git", "commit", "-m", "add test2.txt"), dir)
        runCommand(listOf("git", "tag", "-a", "-m", "version tag", "2.2.2"), dir)

        val versionAlt2 = versionFromGit(dir.absolutePath)
        assert(versionAlt2 == "2.2.2")

        runCommand(listOf("git", "checkout", "master"), dir)

        val version = versionFromGit(dir.absolutePath)
        assert(version == "1.1.1")
    }

    @Test
    fun `correctly detect the version if an older version is back-merged`() {
        val dir = Files.createTempDirectory(tmp, "test-git-repo").toFile()
        runCommand(listOf("git", "init"), dir)
        runCommand(listOf("git", "branch", "-m", "master"), dir)
        File(dir, "test.txt").writeText("some-text")
        runCommand(listOf("git", "add", "test.txt"), dir)
        runCommand(listOf("git", "commit", "-m", "initial commit"), dir)
        runCommand(listOf("git", "tag", "-a", "-m", "version tag", "0.0.2"), dir)
        Thread.sleep(1000)
        runCommand(listOf("git", "checkout", "-b", "alt"), dir)
        File(dir, "test2.txt").writeText("some-other-text")
        runCommand(listOf("git", "add", "test2.txt"), dir)
        runCommand(listOf("git", "commit", "-m", "add test2.txt"), dir)
        runCommand(listOf("git", "tag", "-a", "-m", "version tag", "0.0.1"), dir)

        runCommand(listOf("git", "checkout", "master"), dir)
        runCommand(listOf("git", "merge", "alt"), dir)

        val version = versionFromGit(dir.absolutePath)
        assert(version.startsWith("0.0.2-1-g"))
    }

}
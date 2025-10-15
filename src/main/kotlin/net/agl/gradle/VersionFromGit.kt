package net.agl.gradle

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.diff.RawTextComparator
import org.eclipse.jgit.dircache.DirCacheIterator
import org.eclipse.jgit.errors.RepositoryNotFoundException
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.Constants.R_TAGS
import org.eclipse.jgit.lib.IndexDiff
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.treewalk.FileTreeIterator
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.DigestOutputStream
import java.security.MessageDigest

// ANSI colors
private const val RESET: String = "\u001b[0m"
private const val RED: String = "\u001b[0;31m"
private const val GREEN: String = "\u001b[0;32m"
private const val YELLOW: String = "\u001b[0;33m"

private fun isDirty(git: Git): Boolean {
    val status = git.status().call()
    return status.untracked.isNotEmpty() ||
            status.missing.isNotEmpty() ||
            status.changed.isNotEmpty() ||
            status.modified.isNotEmpty()
}

private fun dirtyHash(repo: Repository): String {
    // Force a rescan of working tree vs. index
    val diff = IndexDiff(repo, Constants.HEAD, FileTreeIterator(repo))
    diff.diff() // compute

    if (
        diff.modified.isEmpty() &&
        diff.changed.isEmpty() &&
        diff.missing.isEmpty() &&
        diff.untracked.isEmpty()
    ) {
        return ""
    }

    val digest = MessageDigest.getInstance("MD5")
    DigestOutputStream(ByteArrayOutputStream(), digest).use { out ->
        // 🔹 Now also show line diffs for modified files
        DiffFormatter(out).use { formatter ->
            formatter.setRepository(repo)
            formatter.setDiffComparator(RawTextComparator.DEFAULT)
            formatter.isDetectRenames = true

            val indexIter = DirCacheIterator(repo.readDirCache())
            val workIter = FileTreeIterator(repo)

            formatter.format(indexIter, workIter)
        }
    }

    return digest.digest().joinToString("") { "%02x".format(it) }
}

fun versionFromGit(
    gitRoot: String,
    dirtyDetect: Boolean = true,
    dirtyHash: Boolean = true,
    commitsNo: Boolean = true,
    fallbackVersion: String = "0.0.0-SNAPSHOT"
): String {
    try {
        Git.open(File(gitRoot)).use { git ->
            val repo = git.repository
            val walk = RevWalk(repo)

            val lastTag = repo.refDatabase.getRefsByPrefix(R_TAGS)
                .map { repo.refDatabase.peel(it) }
                .filter { it.peeledObjectId != null }
                .takeIf { it.isNotEmpty() }
                ?.last()
            if (lastTag == null) {
                System.err.println("${RED}No tags found, falling back to default version ${GREEN}${fallbackVersion}${RESET}")
                return fallbackVersion
            }

            val version = StringBuilder(lastTag.name.substring(R_TAGS.length))
            if (version.endsWith("-SNAPSHOT")) {
                System.err.println("${YELLOW}SNAPSHOT version found. Skipping further calculations${RESET}")
                println("Version calculated: ${GREEN}$version${RESET}")
                return version.toString()
            }

            if (commitsNo) {
                val lastTagObj = lastTag.peeledObjectId ?: lastTag.objectId
                val lastTagCommit = walk.parseCommit(lastTagObj)
                val headId = repo.resolve("HEAD")
                val headCommit = walk.parseCommit(headId)
                walk.reset()
                walk.markStart(headCommit)
                walk.markUninteresting(lastTagCommit)
                val tagToHeadCommits = walk.count()
                val headHash = headId.name.substring(0, 7)

                if (tagToHeadCommits > 0) {
                    version.append('-').append(tagToHeadCommits).append("-g").append(headHash)
                }
            }

            if (dirtyDetect) {
                if (dirtyHash) {
                    dirtyHash(repo).takeIf { it.isNotBlank() }?.substring(0, 8)?.let {
                        version.append("-DIRTY-").append(it)
                    }
                } else if (isDirty(git)) {
                    version.append("-DIRTY")
                }
            }

            println("Version calculated: ${GREEN}$version${RESET}")
            return version.toString()
        }
    } catch (e: RepositoryNotFoundException) {
        System.err.println("${RED}No GIT repository found in [${gitRoot}], falling back to default version ${GREEN}${fallbackVersion}${RESET}")
        return fallbackVersion
    }
}

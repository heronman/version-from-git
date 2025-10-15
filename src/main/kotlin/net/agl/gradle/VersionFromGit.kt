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
import org.eclipse.jgit.revwalk.RevTag
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.treewalk.FileTreeIterator
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.DigestOutputStream
import java.security.MessageDigest
import kotlin.use

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

fun getLastTag(repo: Repository): Pair<RevTag?, Int?> {
    RevWalk(repo).use { walk ->
        val tags = repo.refDatabase.getRefsByPrefix(R_TAGS)
            .map { walk.parseAny(it.objectId) }
            // Filter out non-annotated tags (which are represented as RevCommit)
            .filterIsInstance<RevTag>()
            // Group by commit hash
            .groupBy { tag -> tag.`object`.name }

        // No tags found
        if (tags.isEmpty()) {
            return Pair(null, null)
        }

        // Walk from the HEAD commit to the first tagged commit, to find the last tag
        walk.markStart(walk.parseCommit(repo.resolve(Constants.HEAD)))
        var ncommits = 0
        for (commit in walk) {
            if (!tags.containsKey(commit.name)) {
                ncommits++
            } else {
                // Take the chronologically last tag found for the commit
                return Pair(tags[commit.name]!!.maxBy { tag -> tag.taggerIdent.whenAsInstant }, ncommits)
            }
        }
    }
    return Pair(null, null)
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

            val (lastTag, ncommits) = getLastTag(repo)
            if (lastTag == null) {
                System.err.println("${RED}No tags found, falling back to default version ${GREEN}${fallbackVersion}${RESET}")
                return fallbackVersion
            }
            val version = StringBuilder(lastTag.tagName)
            if (version.endsWith("-SNAPSHOT")) {
                System.err.println("${YELLOW}SNAPSHOT version found. Skipping further calculations${RESET}")
                println("Version calculated: ${GREEN}$version${RESET}")
                return version.toString()
            }

            ncommits?.takeIf { commitsNo && it > 0 }?.let {
                version.append('-')
                    .append(it)
                    .append("-g")
                    .append(
                        repo.refDatabase.exactRef("HEAD")
                            .objectId.name.substring(0, 7)
                    )
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
    } catch (_: RepositoryNotFoundException) {
        System.err.println("${RED}No GIT repository found in [${gitRoot}], falling back to default version ${GREEN}${fallbackVersion}${RESET}")
        return fallbackVersion
    }
}

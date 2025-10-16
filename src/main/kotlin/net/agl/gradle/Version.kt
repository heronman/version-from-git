package net.agl.gradle

import kotlin.math.min

class Version(val parts: Array<Int>, val suffix: String? = null, prefix: Char? = null) : Comparable<Version> {
    val version: String

    init {
        version = "${prefix ?: ""}${parts.joinToString(".")}${suffix?.let { "-$it" } ?: ""}"
    }

    companion object {
        fun parse(version: String?): Version {
            if (version.isNullOrBlank()) {
                return Version(arrayOf())
            }

            if (version[0] in " \t\r\n" || version[version.length - 1] in " \t\r\n") {
                return parse(version.trim())
            }

            var i = if (version[0] == 'v') 1 else 0
            var j = i
            val parts = mutableListOf<Int>()
            var suffix: String? = null
            while (i < version.length) {
                if (version[i] !in '0'..'9') {
                    if (i == j) {
                        throw IllegalArgumentException("Unexpected character '${version[i]}' at position '$i'")
                    }
                    parts.add(version.substring(j, i).toInt())
                    j = i + 1
                    if (version[i] == '-') {
                        break
                    }
                    if (version[i] != '.') {
                        throw IllegalArgumentException("Unexpected character '${version[i]}' at position '$i'")
                    }
                }
                i++
            }
            if (j < i) {
                parts.add(version.substring(j, i).toInt())
            }
            if (i + 1 < version.length) {
                suffix = version.substring(i + 1)
            }
            return Version(parts.toTypedArray(), suffix, if (version[0] == 'v') 'v' else null)
        }
    }

    override fun equals(other: Any?): Boolean {
        return other != null && other is Version && version == other.version
    }

    override fun hashCode(): Int {
        return toString().hashCode()
    }

    override fun toString(): String {
        return version
    }

    override operator fun compareTo(other: Version): Int {
        if (this == other) {
            return 0
        }

        var cmp: Int
        for (i in 0 until min(this.parts.size, other.parts.size)) {
            cmp = this.parts[i].compareTo(other.parts[i])
            if (cmp != 0) {
                return cmp
            }
        }
        cmp = this.parts.size.compareTo(other.parts.size)
        if (cmp != 0) {
            return cmp
        }
        if (this.suffix == other.suffix) {
            return 0
        }
        if (this.suffix == "RELEASE") {
            return 1
        }
        if (other.suffix == "RELEASE") {
            return -1
        }
        if (this.suffix == null) {
            return 1
        }
        if (other.suffix == null) {
            return -1
        }
        return this.suffix.compareTo(other.suffix)
    }
}
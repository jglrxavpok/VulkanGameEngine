package org.jglrxavpok.engine

/**
 * Data class representing a Major.Minor.Patch version
 */
data class Version(val major: Int, val minor: Int, val patch: Int, val nickname: String = "v$major.$minor.$patch")

/**
 * Information about a game
 */
data class GameInformation(val name: String, val version: Version)
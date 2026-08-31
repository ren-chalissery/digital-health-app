package io.simplicity.training.assistant

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The assistant must never be able to read a clinician's journal.
 *
 * That is a privacy property rather than a matter of taste: reflections are promised to be private
 * to their author, and an assistant that could reach them would quietly break the promise however
 * carefully it behaved today. A comment saying "do not depend on :reflect" is not enforcement, so
 * this reads the build file.
 *
 * Deliberately a build-file assertion rather than a runtime one. By the time a dependency exists,
 * any test using it has already been written.
 */
class ModuleBoundaryTest {

    @Test
    fun `assistant does not depend on reflect`() {
        val buildFile = File("build.gradle.kts")
        assertTrue("expected to find the module's own build file", buildFile.exists())

        val declared = buildFile.readText()

        assertTrue(
            "':assistant' must not depend on ':reflect' — the assistant reads training content, " +
                "never a clinician's private journal",
            !declared.contains("\":reflect\""),
        )
    }
}

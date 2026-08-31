package io.simplicity.training.reflect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The same cases the iOS and web versions assert.
 *
 * The false-positive tests matter as much as the detections. A warning that fires on ordinary
 * clinical writing gets ignored, and an ignored warning is worse than none — it trains people to
 * dismiss the one that was right.
 */
class IdentifiersTest {

    private fun kinds(text: String) = Identifiers.find(text).map { it.kind }

    @Test
    fun `an NHI number is spotted`() {
        assertTrue("an NHI number" in kinds("Discussed with ABC1234 today"))
    }

    @Test
    fun `a Medicare number is spotted`() {
        assertTrue("a Medicare number" in kinds("Card 2123 45670 1"))
    }

    @Test
    fun `a date of birth is spotted`() {
        assertTrue("a date of birth" in kinds("Born 12/03/1985"))
    }

    @Test
    fun `an email address is spotted`() {
        assertTrue("an email address" in kinds("Contact them at a.person@example.org"))
    }

    @Test
    fun `a phone number is spotted`() {
        assertTrue("a phone number" in kinds("Rang 021 234 5678"))
    }

    @Test
    fun `ordinary reflective writing produces no warnings`() {
        assertEquals(
            emptyList<String>(),
            kinds("I noticed I was rushing the introduction and slowed down for the second half."),
        )
    }

    /** A time is not a phone number, and treating it as one would fire on half the entries. */
    @Test
    fun `a time of day is not a phone number`() {
        assertEquals(emptyList<String>(), kinds("The session ran from 14:30 to 15:15."))
    }

    @Test
    fun `an ordinary word is not an NHI number`() {
        assertEquals(emptyList<String>(), kinds("The plan was reviewed."))
    }

    @Test
    fun `several identifiers are each reported`() {
        val found = kinds("ABC1234, born 12/03/1985, at a.person@example.org")

        assertTrue(found.containsAll(listOf("an NHI number", "a date of birth", "an email address")))
    }

    @Test
    fun `each warning explains why it was raised`() {
        val warning = Identifiers.find("ABC1234").single()

        assertTrue(warning.explanation.isNotBlank())
    }
}

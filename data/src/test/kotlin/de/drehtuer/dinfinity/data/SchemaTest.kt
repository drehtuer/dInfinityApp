package de.drehtuer.dinfinity.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.drehtuer.dinfinity.data.db.DInfinityDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * That the database's schema is written down, and that every version can be
 * reached from the one before it.
 *
 * "Migrations from day one" (`docs/TODO.md`) only means anything if something
 * fails when they are missing. This is that something: at version 1 it asserts
 * the schema is checked in and the migration list is complete, and the day
 * somebody bumps the version without writing a migration it is what stops
 * them — rather than a player's phone, six weeks later, with their
 * natural-20 count in it.
 */
@RunWith(RobolectricTestRunner::class)
class SchemaTest {
  private val context = ApplicationProvider.getApplicationContext<Context>()

  @Test
  fun `the current version's schema is checked in`() {
    assertNotNull("no schema exported for version ${DInfinityDatabase.VERSION}", schema(DInfinityDatabase.VERSION))
  }

  @Test
  fun `every version from the first has a schema`() {
    (1..DInfinityDatabase.VERSION).forEach { version ->
      assertNotNull("no schema exported for version $version", schema(version))
    }
  }

  @Test
  fun `the exported schema says the version it is`() {
    val exported = schema(DInfinityDatabase.VERSION)!!
    assertTrue(
      "the schema does not declare version ${DInfinityDatabase.VERSION}",
      exported.contains("\"version\": ${DInfinityDatabase.VERSION}"),
    )
  }

  @Test
  fun `the exported schema has the three tables docs statistics names`() {
    val exported = schema(DInfinityDatabase.VERSION)!!
    listOf("roll_history", "die_stats", "die_summary").forEach { table ->
      assertTrue("the schema has no $table", exported.contains("\"tableName\": \"$table\""))
    }
  }

  @Test
  fun `there is a migration for every step between versions`() {
    val steps = DInfinityDatabase.MIGRATIONS.map { it.startVersion to it.endVersion }.toSet()
    (1 until DInfinityDatabase.VERSION).forEach { from ->
      assertTrue("nothing migrates $from to ${from + 1}", (from to from + 1) in steps)
    }
  }

  @Test
  fun `no migration claims to go backwards or nowhere`() {
    DInfinityDatabase.MIGRATIONS.forEach { migration ->
      assertTrue("$migration goes nowhere", migration.endVersion > migration.startVersion)
    }
  }

  @Test
  fun `the database opens and closes`() {
    val database =
      Room
        .inMemoryDatabaseBuilder(context, DInfinityDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    assertNotNull(database.rollHistory())
    assertNotNull(database.dieStats())
    assertNotNull(database.dieSummary())
    database.close()
  }

  @Test
  fun `the app opens one file, named where the architecture says`() {
    assertEquals("dinfinity.db", DInfinityDatabase.NAME)
  }

  /** The schema file as it is checked in, or `null` when there is none. */
  private fun schema(version: Int): String? {
    val root = requireNotNull(System.getProperty("dinfinity.schemas")) { "the build did not say where the schemas are" }
    val file = java.io.File(root, "${DInfinityDatabase::class.java.name}/$version.json")
    return if (file.isFile) file.readText() else null
  }
}

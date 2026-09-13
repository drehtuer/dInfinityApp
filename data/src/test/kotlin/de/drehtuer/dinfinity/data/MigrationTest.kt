package de.drehtuer.dinfinity.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.drehtuer.dinfinity.data.db.DInfinityDatabase
import de.drehtuer.dinfinity.data.db.SavedRollGroupRow
import de.drehtuer.dinfinity.data.db.SavedRollRow
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * A version-1 database, migrated for real, with what was in it still in it.
 *
 * `SchemaTest` says a migration *exists* for every step between versions. This
 * one runs it: it builds a database out of **version 1's own exported schema**
 * — the artifact that says what shipped — puts a roll in it, then opens it
 * through `Room.databaseBuilder` exactly as the app does.
 *
 * Opening it that way is what makes this a real check rather than a smoke
 * test. Room validates the migrated schema against the current entities on
 * open and refuses a database that does not match, so a migration that creates
 * the wrong column type, forgets an index or misspells a table fails here.
 *
 * And the roll put in beforehand is the other half. A migration that dropped
 * every table would satisfy any structural check ever written while losing a
 * player's natural-20 count, which is the one thing the database exists to
 * keep (`docs/statistics.md`).
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {
  private val context = ApplicationProvider.getApplicationContext<Context>()

  @Before
  fun startFromNothing() {
    context.getDatabasePath(NAME).also { it.parentFile?.mkdirs() }.delete()
  }

  @Test
  fun `a version 1 database opens at the current version`() =
    runTest {
      writeVersion1()

      withDatabase { database ->
        assertEquals(DInfinityDatabase.VERSION, database.openHelper.readableDatabase.version)
      }
    }

  @Test
  fun `what was rolled before the migration is still there after it`() =
    runTest {
      writeVersion1 { db ->
        db.execSQL(
          """
          INSERT INTO roll_history
            (timestamp, session_id, formula, total, seed, breakdown_json, anomalies)
          VALUES (1, 'unfiled', '1d20', 20, 7, '{}', 0)
          """.trimIndent(),
        )
      }

      withDatabase { database ->
        assertEquals(1L, database.rollHistory().count())
      }
    }

  @Test
  fun `the new tables are usable the moment the migration has run`() =
    runTest {
      writeVersion1()

      withDatabase { database ->
        database.savedRollGroups().upsert(SavedRollGroupRow(id = "thorin", name = "Thorin"))
        database.savedRolls().upsert(
          SavedRollRow(id = "fireball", groupId = "thorin", name = "Fireball", formula = "8d6 [Fire]"),
        )

        assertEquals("Fireball", database.savedRolls().byId("fireball")?.name)
      }
    }

  @Test
  fun `deleting a group takes its rolls with it rather than leaving them nowhere`() =
    runTest {
      writeVersion1()

      withDatabase { database ->
        database.savedRollGroups().upsert(SavedRollGroupRow(id = "thorin", name = "Thorin"))
        database.savedRolls().upsert(
          SavedRollRow(id = "fireball", groupId = "thorin", name = "Fireball", formula = "8d6"),
        )

        database.savedRollGroups().delete("thorin")

        // The screens move rolls to Unfiled instead and only delete an empty
        // group; the cascade is the floor under that, not the behaviour.
        assertEquals(null, database.savedRolls().byId("fireball"))
      }
    }

  /**
   * The app's own way of opening it: the real builder, the real migrations.
   *
   * Closed afterwards however [block] ends, because a Robolectric test that
   * leaves a database open leaves the file locked for the next one.
   */
  private suspend fun <R> withDatabase(block: suspend (DInfinityDatabase) -> R): R {
    val database =
      Room
        .databaseBuilder(context, DInfinityDatabase::class.java, NAME)
        .also { builder -> DInfinityDatabase.MIGRATIONS.forEach(builder::addMigrations) }
        .allowMainThreadQueries()
        .build()
    return try {
      block(database)
    } finally {
      database.close()
    }
  }

  /**
   * A database exactly as version 1 left it, built from version 1's schema.
   *
   * Not hand-written DDL: a copy of the old schema in a test is a copy that
   * drifts, and the one in `data/schemas/` is what actually shipped.
   */
  private fun writeVersion1(fill: (SQLiteDatabase) -> Unit = {}) {
    val schema = JSONObject(schemaFile(1).readText()).getJSONObject("database")
    val database = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(NAME), null)
    val entities = schema.getJSONArray("entities")
    repeat(entities.length()) { index ->
      val entity = entities.getJSONObject(index)
      val table = entity.getString("tableName")
      database.execSQL(entity.getString("createSql").replace(TABLE_NAME, table))
      val indices = entity.optJSONArray("indices") ?: return@repeat
      repeat(indices.length()) { at ->
        database.execSQL(indices.getJSONObject(at).getString("createSql").replace(TABLE_NAME, table))
      }
    }
    // The identity hash among them, without which Room refuses to open a file
    // it did not create itself.
    val setup = schema.getJSONArray("setupQueries")
    repeat(setup.length()) { index -> database.execSQL(setup.getString(index)) }
    database.version = schema.getInt("version")
    fill(database)
    database.close()
  }

  private fun schemaFile(version: Int): File {
    val root = requireNotNull(System.getProperty("dinfinity.schemas")) { "the build did not say where the schemas are" }
    return File(root, "${DInfinityDatabase::class.java.name}/$version.json")
  }

  private companion object {
    const val NAME = "migration-test.db"
    const val TABLE_NAME = "\${TABLE_NAME}"
  }
}

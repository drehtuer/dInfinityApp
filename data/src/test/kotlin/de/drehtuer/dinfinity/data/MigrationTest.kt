package de.drehtuer.dinfinity.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.drehtuer.dinfinity.data.db.DInfinityDatabase
import de.drehtuer.dinfinity.data.db.SavedRollGroupRow
import de.drehtuer.dinfinity.data.db.SavedRollRow
import kotlinx.coroutines.flow.first
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
  fun `the registry arrives empty, and works`() =
    runTest {
      // Unlike the session table, nothing is inserted here. A set with no row
      // is enabled, so a player upgrading from version 1 keeps every package
      // they had switched on — which is all of them — without the migration
      // reading the disk to find out what they are.
      writeVersion1()

      withDatabase { database ->
        val registry = InstalledSetRepository(database)
        assertEquals("the migration invented an opinion", emptySet<String>(), registry.disabled())

        registry.setEnabled("brass", enabled = false)

        assertEquals(setOf("brass"), registry.disabled())
      }
    }

  @Test
  fun `the rolls made before sessions existed belong to the first session`() =
    runTest {
      // The session column has carried a value since version 1, which is why
      // this is a rename rather than a migration: the migration gives the id
      // those rows already point at a name (`docs/statistics.md`).
      writeVersion1 { db ->
        db.execSQL(
          """
          INSERT INTO roll_history
            (timestamp, session_id, formula, total, seed, breakdown_json, anomalies)
          VALUES (1, 'default', '1d20', 20, 7, '{}', 0)
          """.trimIndent(),
        )
      }

      withDatabase { database ->
        val first = database.sessions().byId("default")
        assertEquals("First rolls", first?.name)
        assertEquals(
          1,
          database
            .rollHistory()
            .inSession("default", 10)
            .first()
            .size,
        )
      }
    }

  @Test
  fun `the face counts kept before sessions existed become the first session's`() =
    runTest {
      // Version 5 reshapes a table somebody already has rows in, which no
      // earlier migration did. What must survive is the count itself and the
      // agreement between the two aggregates: if these rows landed under any
      // other session, or under none, the sum of the sessions would stop
      // matching the all-time totals the first time the screen drew.
      writeVersion1 { db ->
        db.execSQL(
          "INSERT INTO die_stats (set_id, die_id, sides, face_value, count, dropped_count) " +
            "VALUES ('builtin', 'd20', 20, 20, 7, 2)",
        )
      }

      withDatabase { database ->
        val counted = database.dieStats().find("builtin", "d20", SessionRepository.DEFAULT_ID, 20)
        assertEquals("the count did not survive the reshape", 7L, counted?.count)
        assertEquals("the dropped dice did not survive it either", 2L, counted?.droppedCount)
        assertEquals(
          "the all-time total has to be the sum of the sessions",
          7L,
          database
            .dieStats()
            .histogram("builtin", "d20")
            .first()
            .single()
            .count,
        )
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

  @Test
  fun `the saved rolls of a phone that had favourites come back favourites first`() =
    runTest {
      // Version 6 takes the flag out and puts the player's own order in its
      // place, so somebody upgrading has to be given an order they recognise.
      // The one the list was in was favourites first and then by recent use,
      // and that is what is written down here — once, never computed again
      // (`docs/dice-notation.md`, "Saved rolls").
      writeVersion(5) { db ->
        db.execSQL(
          "INSERT INTO saved_roll_group (id, name, icon, sort_order) VALUES ('thorin', 'Thorin', '', 0)",
        )
        savedRoll(db, id = "old", favourite = 0, lastUsedAt = "1000")
        savedRoll(db, id = "never", favourite = 0, lastUsedAt = "NULL")
        savedRoll(db, id = "recent", favourite = 0, lastUsedAt = "5000")
        savedRoll(db, id = "liked", favourite = 1, lastUsedAt = "2000")
      }

      withDatabase { database ->
        assertEquals(
          listOf("liked", "recent", "old", "never"),
          database
            .savedRolls()
            .inGroup("thorin")
            .first()
            .map { it.id },
        )
        assertEquals(
          listOf(0, 1, 2, 3),
          database
            .savedRolls()
            .inGroup("thorin")
            .first()
            .map { it.sortOrder },
        )
      }
    }

  @Test
  fun `the migration numbers each group's list of its own`() =
    runTest {
      // Two groups' lists are two lists. Numbering across the table would put
      // one group's rolls after another's for no reason anybody could see.
      writeVersion(5) { db ->
        db.execSQL("INSERT INTO saved_roll_group (id, name, icon, sort_order) VALUES ('thorin', 'Thorin', '', 0)")
        db.execSQL("INSERT INTO saved_roll_group (id, name, icon, sort_order) VALUES ('ezren', 'Ezren', '', 1)")
        savedRoll(db, id = "a", favourite = 0, lastUsedAt = "1000")
        savedRoll(db, id = "b", favourite = 0, lastUsedAt = "2000")
        savedRoll(db, id = "c", favourite = 0, lastUsedAt = "3000", group = "ezren")
      }

      withDatabase { database ->
        assertEquals(
          listOf(0, 1),
          database
            .savedRolls()
            .inGroup("thorin")
            .first()
            .map { it.sortOrder },
        )
        assertEquals(
          listOf(0),
          database
            .savedRolls()
            .inGroup("ezren")
            .first()
            .map { it.sortOrder },
        )
      }
    }

  @Test
  fun `what a saved roll carried besides the flag survives version 6`() =
    runTest {
      // The whole row is copied into the new table, so a migration that got
      // the column list wrong would lose a formula or a table pin quietly.
      writeVersion(5) { db ->
        db.execSQL("INSERT INTO saved_roll_group (id, name, icon, sort_order) VALUES ('thorin', 'Thorin', '', 0)")
        db.execSQL(
          """
          INSERT INTO saved_roll
            (id, group_id, name, formula, icon, colour_argb, favourite,
             table_set_id, table_id, created_at, last_used_at, use_count)
          VALUES ('fireball', 'thorin', 'Fireball', '8d6 [Fire]', '🔥', 255, 1,
             'brass', 'oak', 900, 1000, 47)
          """.trimIndent(),
        )
      }

      withDatabase { database ->
        val roll = requireNotNull(database.savedRolls().byId("fireball"))
        assertEquals("8d6 [Fire]", roll.formula)
        assertEquals("🔥", roll.icon)
        assertEquals(255, roll.colourArgb)
        assertEquals("brass", roll.tableSetId)
        assertEquals("oak", roll.tableId)
        assertEquals(47, roll.useCount)
      }
    }

  /** One version-5 saved roll, written the way version 5 spelled one. */
  private fun savedRoll(
    db: SQLiteDatabase,
    id: String,
    favourite: Int,
    lastUsedAt: String,
    group: String = "thorin",
  ) {
    db.execSQL(
      "INSERT INTO saved_roll (id, group_id, name, formula, icon, colour_argb, favourite, " +
        "table_set_id, table_id, created_at, last_used_at, use_count) VALUES " +
        "('$id', '$group', '$id', '1d20', '', NULL, $favourite, NULL, NULL, 0, $lastUsedAt, 0)",
    )
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

  /** A database exactly as version 1 left it. */
  private fun writeVersion1(fill: (SQLiteDatabase) -> Unit = {}) = writeVersion(1, fill)

  /**
   * A database exactly as [version] left it, built from that version's schema.
   *
   * Not hand-written DDL: a copy of an old schema in a test is a copy that
   * drifts, and the one in `data/schemas/` is what actually shipped.
   */
  private fun writeVersion(
    version: Int,
    fill: (SQLiteDatabase) -> Unit = {},
  ) {
    val schema = JSONObject(schemaFile(version).readText()).getJSONObject("database")
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

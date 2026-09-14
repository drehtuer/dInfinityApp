package de.drehtuer.dinfinity.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The app's database (`docs/statistics.md`, "Storage").
 *
 * Version 1 is the three statistics tables. Version 2 adds saved rolls and
 * their groups. Version 3 adds sessions. Version 4 adds the installed-set
 * registry, which arrived with the screen that needed it (`docs/TODO.md`,
 * Step 4.4) — which is the point of writing migrations from day one rather
 * than from the first release. A database that has only ever been created,
 * never migrated, is a database whose first migration is written under
 * pressure.
 *
 * Every version's schema is exported to `data/schemas/` and checked in.
 * `SchemaTest` walks them, so a version bump without a migration fails the
 * build rather than the phone.
 *
 * Nothing here is uploaded anywhere. The statistics in this document are the
 * player's, on the player's phone (`docs/statistics.md`).
 */
@Database(
  entities = [
    RollHistoryRow::class,
    DieStatsRow::class,
    DieSummaryRow::class,
    SavedRollGroupRow::class,
    SavedRollRow::class,
    SessionRow::class,
    InstalledSetRow::class,
  ],
  version = DInfinityDatabase.VERSION,
  exportSchema = true,
)
abstract class DInfinityDatabase : RoomDatabase() {
  abstract fun rollHistory(): RollHistoryDao

  abstract fun dieStats(): DieStatsDao

  abstract fun dieSummary(): DieSummaryDao

  abstract fun savedRollGroups(): SavedRollGroupDao

  abstract fun savedRolls(): SavedRollDao

  abstract fun sessions(): SessionDao

  abstract fun installedSets(): InstalledSetDao

  companion object {
    /** Bumping this needs a migration and a checked-in schema. Both are enforced. */
    const val VERSION: Int = 4

    /** The file the app opens (`docs/architecture.md`, "Storage layout"). */
    const val NAME: String = "dinfinity.db"

    /**
     * Every migration there is, in order.
     *
     * The list was wired up while it was empty, which is why adding the first
     * entry was one line rather than a change to how the database opens.
     */
    val MIGRATIONS: List<Migration> = listOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)

    /** Opens the database, migrating it if it is older. */
    fun open(
      context: Context,
      name: String = NAME,
    ): DInfinityDatabase =
      Room
        .databaseBuilder(context.applicationContext, DInfinityDatabase::class.java, name)
        .also { builder -> MIGRATIONS.forEach(builder::addMigrations) }
        // No fallbackToDestructiveMigration, ever. A player's natural-20 count
        // is not something to throw away because a schema moved.
        .build()
  }
}

/**
 * Version 1 → 2: saved rolls and the groups they live in
 * (`docs/dice-notation.md`, "Saved rolls").
 *
 * Two new tables and nothing touched. Statistics, history and their aggregates
 * are exactly as they were, which is the whole point of arriving as a
 * migration rather than as a bigger version 1: a player who has been rolling
 * dice since the first release keeps every number they have collected.
 *
 * Written by hand rather than generated, and checked against a database
 * actually migrated from version 1 — a migration that agrees with the entities
 * is the only kind worth having, and the only way to know is to run it.
 */
internal val MIGRATION_1_2: Migration =
  object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
      db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `saved_roll_group` (
          `id` TEXT NOT NULL,
          `name` TEXT NOT NULL,
          `icon` TEXT NOT NULL,
          `parent_id` TEXT,
          `sort_order` INTEGER NOT NULL,
          `table_set_id` TEXT,
          `table_id` TEXT,
          PRIMARY KEY(`id`)
        )
        """.trimIndent(),
      )
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_saved_roll_group_parent_id` ON `saved_roll_group` (`parent_id`)")
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_saved_roll_group_sort_order` ON `saved_roll_group` (`sort_order`)")

      db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `saved_roll` (
          `id` TEXT NOT NULL,
          `group_id` TEXT NOT NULL,
          `name` TEXT NOT NULL,
          `formula` TEXT NOT NULL,
          `icon` TEXT NOT NULL,
          `colour_argb` INTEGER,
          `favourite` INTEGER NOT NULL,
          `table_set_id` TEXT,
          `table_id` TEXT,
          `created_at` INTEGER NOT NULL,
          `last_used_at` INTEGER,
          `use_count` INTEGER NOT NULL,
          PRIMARY KEY(`id`),
          FOREIGN KEY(`group_id`) REFERENCES `saved_roll_group`(`id`)
            ON UPDATE NO ACTION ON DELETE CASCADE
        )
        """.trimIndent(),
      )
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_saved_roll_group_id` ON `saved_roll` (`group_id`)")
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_saved_roll_favourite` ON `saved_roll` (`favourite`)")
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_saved_roll_last_used_at` ON `saved_roll` (`last_used_at`)")
    }
  }

/**
 * Version 2 → 3: sessions (`docs/statistics.md`, per session).
 *
 * One new table and nothing touched. `roll_history.session_id` has been there
 * since version 1 and every row already carries a value, so the rolls somebody
 * made before sessions existed do not become orphans the moment sessions do —
 * they belong to a session that can now be named, which is why that column was
 * never left empty (`RollRecording.NO_SESSION`).
 *
 * The default session is inserted here rather than by the first screen to want
 * one: a history full of rows pointing at a session that does not exist is a
 * join that quietly drops them.
 */
internal val MIGRATION_2_3: Migration =
  object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
      db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `session` (
          `id` TEXT NOT NULL,
          `name` TEXT NOT NULL,
          `started_at` INTEGER NOT NULL,
          PRIMARY KEY(`id`)
        )
        """.trimIndent(),
      )
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_session_started_at` ON `session` (`started_at`)")
      // The one every roll made before this migration already belongs to.
      db.execSQL(
        "INSERT OR IGNORE INTO `session` (`id`, `name`, `started_at`) VALUES " +
          "('default', 'First rolls', 0)",
      )
    }
  }

/**
 * Version 3 → 4: the installed-set registry (`docs/dice-sets.md`, design `5a`).
 *
 * One new table and nothing touched. **Nothing is inserted into it**, which is
 * the difference between this migration and the one before it: a session had
 * to exist because every history row already pointed at one, whereas a set with
 * no row is simply enabled. Writing a row per installed folder here would mean
 * reading the disk from inside a migration, and would say nothing that the
 * absence of a row does not already say.
 */
internal val MIGRATION_3_4: Migration =
  object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
      db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `installed_set` (
          `id` TEXT NOT NULL,
          `enabled` INTEGER NOT NULL,
          PRIMARY KEY(`id`)
        )
        """.trimIndent(),
      )
    }
  }

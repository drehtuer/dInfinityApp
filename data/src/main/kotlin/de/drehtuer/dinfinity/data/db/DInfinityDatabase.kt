package de.drehtuer.dinfinity.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The app's database (`docs/statistics.md`, "Storage").
 *
 * Version 1 is the three statistics tables and nothing else. Saved rolls,
 * sessions and the installed-set registry arrive with the screens that need
 * them (`docs/TODO.md`, Step 4), each as a migration — which is the point of
 * writing migrations from day one rather than from the first release. A
 * database that has only ever been created, never migrated, is a database
 * whose first migration is written under pressure.
 *
 * Every version's schema is exported to `data/schemas/` and checked in.
 * `MigrationTest` walks them, so a version bump without a migration fails the
 * build rather than the phone.
 *
 * Nothing here is uploaded anywhere. The statistics in this document are the
 * player's, on the player's phone (`docs/statistics.md`).
 */
@Database(
  entities = [RollHistoryRow::class, DieStatsRow::class, DieSummaryRow::class],
  version = DInfinityDatabase.VERSION,
  exportSchema = true,
)
abstract class DInfinityDatabase : RoomDatabase() {
  abstract fun rollHistory(): RollHistoryDao

  abstract fun dieStats(): DieStatsDao

  abstract fun dieSummary(): DieSummaryDao

  companion object {
    /** Bumping this needs a migration and a checked-in schema. Both are enforced. */
    const val VERSION: Int = 1

    /** The file the app opens (`docs/architecture.md`, "Storage layout"). */
    const val NAME: String = "dinfinity.db"

    /**
     * Every migration there is, in order.
     *
     * Empty at version 1, and deliberately wired up anyway: the day this list
     * matters is the day somebody adds the first entry, and a builder that had
     * to be changed then is a builder that would have shipped without it.
     */
    val MIGRATIONS: List<androidx.room.migration.Migration> = emptyList()

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

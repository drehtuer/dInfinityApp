package de.drehtuer.dinfinity

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.drehtuer.dinfinity.core.model.DiceSet
import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.data.InstalledSetRepository
import de.drehtuer.dinfinity.data.db.DInfinityDatabase
import de.drehtuer.dinfinity.designer.BitmapAtlas
import de.drehtuer.dinfinity.designer.Dot
import de.drehtuer.dinfinity.designer.Draft
import de.drehtuer.dinfinity.designer.DraftStore
import de.drehtuer.dinfinity.designer.MinePackage
import de.drehtuer.dinfinity.designer.MineSets
import de.drehtuer.dinfinity.designer.PhotoStore
import de.drehtuer.dinfinity.designer.Stroke
import de.drehtuer.dinfinity.dicesets.builtin.BuiltinDiceSet
import de.drehtuer.dinfinity.dicesets.install.InstalledSets
import de.drehtuer.dinfinity.dicesets.install.PackageInstaller
import de.drehtuer.dinfinity.feature.designer.SaveResult
import de.drehtuer.dinfinity.feature.sets.SetLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.nio.file.Files

/**
 * From a stroke on the canvas to a formula the tray can throw
 * (`docs/face-designer.md`, "Save to set" and "Flow", step 4).
 *
 * This is the whole of the first piece of device feedback on v0.1.1 — "the
 * face colour is not visible when rolling the dice, a default dice is used"
 * — and every part of it is real here: the real draft store, the real
 * rasteriser, the real validator and the real `SetLibrary`. What was broken
 * was never a stub. It was that nothing outside the sets screen ever built
 * `dicesets/mine/`, and that the formula named a set the drawing was not in;
 * both of those are joins, and a join is only testable joined up.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DrawnSetsTest {
  private val temporary: File = Files.createTempDirectory("dinfinity-drawn").toFile()
  private val root = File(temporary, "dicesets")
  private val drafts = DraftStore(File(temporary, "drafts"))
  private val photos = PhotoStore(File(temporary, "table-photos"))
  private lateinit var database: DInfinityDatabase
  private lateinit var registry: InstalledSetRepository

  @Before
  fun open() {
    database =
      Room
        .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), DInfinityDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    registry = InstalledSetRepository(database)
  }

  @After
  fun close() {
    database.close()
    temporary.deleteRecursively()
  }

  @Test
  fun `saving writes the package and names the die in the set that now carries it`() =
    runBlocking {
      val library = library()
      val sets = drawn(library)

      val outcome = sets.save(DiceSet.PERSONAL_ID, drawing())

      assertEquals(
        SaveResult.Saved(sets.writable.single(), rollable = "mine:1d6"),
        outcome,
      )
      assertTrue("no package was written", File(root, "mine/diceset.toml").isFile)
    }

  @Test
  fun `the die the formula names is the one with the drawing on it`() =
    runBlocking {
      // The point of naming the set: a bare `1d6` resolves to the default
      // set, whose d6 has no atlas. The whole chain only pays off if the die
      // that comes back carries one.
      val library = library()

      drawn(library).save(DiceSet.PERSONAL_ID, drawing())

      val die = library.catalogue.set(DiceSet.PERSONAL_ID)?.die("d6")
      assertNotNull("the personal set is not in the catalogue", die)
      assertEquals("textures/d6.png", die?.texturePath)
    }

  @Test
  fun `the catalogue knows the personal set without anybody visiting the sets list`() =
    runBlocking {
      // Cause (c) of the bug: `bringUpToDate` was only ever called from the
      // sets screen's own path, so a player who drew and pressed Roll it
      // rolled against a catalogue in which `mine` did not exist.
      val library = library()
      assertEquals("the personal set was there before anything saved", null, library.catalogue.set(DiceSet.PERSONAL_ID))

      drawn(library).save(DiceSet.PERSONAL_ID, drawing())

      assertNotNull(library.catalogue.set(DiceSet.PERSONAL_ID))
    }

  @Test
  fun `the drawing on the canvas is written before the package is built`() =
    runBlocking {
      // Every other write goes to a background scope and is not waited for
      // (`SavedDrafts`). A package built from the files a moment before the
      // last stroke reached them is a package missing that stroke, so the
      // draft goes in with the call and is written where the caller waits.
      val library = library()

      drawn(library).save(DiceSet.PERSONAL_ID, drawing())

      assertTrue("the draft never reached the disk", drafts.known().contains("d6"))
    }

  @Test
  fun `a phone with nothing drawn on it is not a failure`() =
    runBlocking {
      val outcome = drawn(library()).save(DiceSet.PERSONAL_ID, Draft(die = d6))

      assertEquals(SaveResult.Blank, outcome)
    }

  @Test
  fun `a drawing on a die nothing defines any more is a refusal, not a blank phone`() =
    runBlocking {
      // A draft whose die is not installed is not in the package and its file
      // is kept (`docs/face-designer.md`, "My dice") — so a phone where the
      // only drawing is on a removed set's die builds no package at all.
      // That is a refusal: there *is* something drawn, and it did not get in.
      val library = library(dice = emptyList())

      val outcome = drawn(library).save(DiceSet.PERSONAL_ID, drawing())

      assertEquals(SaveResult.Refused, outcome)
      assertTrue("the drawing was thrown away with the refusal", drafts.known().contains("d6"))
    }

  @Test
  fun `a set nobody can write to is refused rather than attempted`() =
    runBlocking {
      val outcome = drawn(library()).save(DiceSet.BUILTIN_ID, drawing())

      assertEquals(SaveResult.Refused, outcome)
      assertTrue("somebody else's set was written to", !File(root, "builtin").exists())
    }

  @Test
  fun `the one writable set is the personal one, under the name the sets list shows`() {
    val writable = drawn(library()).writable.single()

    assertEquals(DiceSet.PERSONAL_ID, writable.id)
    assertEquals(MinePackage.NAME, writable.name)
  }

  private fun drawn(library: SetLibrary) =
    DrawnSets(
      library = library,
      store = drafts,
      catalogue = { library.catalogue },
      io = Dispatchers.Unconfined,
    )

  private fun library(dice: List<Die> = listOf(d6)) =
    SetLibrary(
      bundled = BuiltinDiceSet.set,
      installed = InstalledSets(root),
      registry = registry,
      io = Dispatchers.Unconfined,
      installer = PackageInstaller(root),
      defaultSetId = { DiceSet.BUILTIN_ID },
      personal =
        MineSets(
          drafts = drafts,
          root = root,
          painter = BitmapAtlas(),
          // Every die a draft may name. Empty is a phone whose packages have
          // all been removed, which is what leaves a drawing with no die.
          dice = { dice },
          photos = photos,
        ),
    )

  /** A d6 with a line on its first face, which is the smallest real drawing. */
  private fun drawing(): Draft =
    Draft(die = d6).onFace(0) {
      it.draw(Stroke(dots = listOf(Dot(0.2f, 0.2f), Dot(0.8f, 0.8f)), colorArgb = INK, width = 0.05f))
    }

  private val d6 = BuiltinDiceSet.set.dice.first { it.shape == DieShape.Cube }

  private companion object {
    const val INK = 0xFF000000.toInt()
  }
}

package de.drehtuer.dinfinity.simulation.harness

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The document a device writes and a person reads.
 *
 * The device is never watched writing it, so the only thing that makes the
 * file trustworthy is that the code which builds it is exercised here, on
 * every pull request, with no phone in sight.
 */
class HarnessJsonTest {
  @Test
  fun `a report survives the trip to text and back`() {
    val report = report()

    assertEquals(report, HarnessJson.decode(HarnessJson.encode(report)))
  }

  @Test
  fun `a run of no rolls survives it too, which is the file a failed run leaves`() {
    val report = HarnessReport.of(facts().copy(rolls = 0), emptyList())

    assertEquals(report, HarnessJson.decode(HarnessJson.encode(report)))
  }

  @Test
  fun `the document names the run, the device and the verdict where a reader will find them`() {
    val text = HarnessJson.encode(report())

    assertTrue(text.contains("\"label\": \"20d20\""), text)
    assertTrue(text.contains("\"model\": \"Pixel 10a\""), text)
    assertTrue(text.contains("\"abi\": \"arm64-v8a\""), text)
    assertTrue(text.contains("\"schema\": ${HarnessReport.SCHEMA}"), text)
  }

  @Test
  fun `the two shares are written out, so nobody reading the file has to divide`() {
    val text = HarnessJson.encode(report())

    assertTrue(text.contains("correctedShare"), text)
    assertTrue(text.contains("rethrownShare"), text)
  }

  @Test
  fun `a soak survives the trip, and says it was one rather than a count of rolls`() {
    val report =
      HarnessReport.of(
        facts().copy(label = "20d20-soak", length = RunLength.Soak(300.0), rolls = 143, framePaced = true),
        emptyList(),
        FrameTimes(millis = listOf(3.0, 12.0), droppedSteps = 2, drawn = false),
      )
    val text = HarnessJson.encode(report)

    assertEquals(report, HarnessJson.decode(text))
    assertTrue(text.contains("\"kind\": \"soak\""), text)
    assertTrue(text.contains("\"seconds\": 300.0"), text)
  }

  @Test
  fun `a run's frames are in the document when there were any, and absent when there were none`() {
    val paced =
      HarnessJson.encode(
        HarnessReport.of(facts(), emptyList(), FrameTimes(millis = listOf(9.5), droppedSteps = 0, drawn = true)),
      )

    assertTrue(paced.contains("\"drawn\": true"), paced)
    // A headless run measured no frame times, so there is no field for one to
    // be misread out of.
    assertFalse(HarnessJson.encode(report()).contains("\"drawn\""))
  }

  @Test
  fun `a target nothing was measured for is written as that rather than as a pass`() {
    val text = HarnessJson.encode(report())

    assertTrue(text.contains("\"result\": \"not-measured\""), text)
    assertTrue(text.contains("\"result\": \"pass\"") || text.contains("\"result\": \"fail\""), text)
  }

  @Test
  fun `a verdict token the document does not use is refused rather than guessed`() {
    val failure = assertFailsWith<IllegalArgumentException> { TargetOutcome.ofToken("maybe") }

    assertTrue(failure.message.orEmpty().contains("not-measured"), failure.message.orEmpty())
  }

  @Test
  fun `a kind of run the document does not know is refused where it is read`() {
    val json = HarnessJson.toJson(report())
    val facts = json["facts"] as JsonObject
    val length = facts["length"] as JsonObject
    val broken =
      JsonObject(
        json.toMutableMap().apply {
          put(
            "facts",
            JsonObject(
              facts.toMutableMap().apply {
                put("length", JsonObject(length.toMutableMap().apply { put("kind", JsonPrimitive("forever")) }))
              },
            ),
          )
        },
      )

    val failure = assertFailsWith<IllegalArgumentException> { HarnessJson.fromJson(broken) }
    assertTrue(failure.message.orEmpty().contains("forever"), failure.message.orEmpty())
  }

  @Test
  fun `a document from another schema is refused rather than misread`() {
    val json = HarnessJson.toJson(report())
    val altered = json.toMutableMap().apply { put("schema", JsonPrimitive(SOME_OTHER_SCHEMA)) }

    val failure = assertFailsWith<IllegalArgumentException> { HarnessJson.fromJson(JsonObject(altered)) }
    assertTrue(failure.message.orEmpty().contains("schema $SOME_OTHER_SCHEMA"), failure.message.orEmpty())
  }

  @Test
  fun `a missing field is named rather than thrown as a stack trace`() {
    val json = HarnessJson.toJson(report())
    val without = JsonObject(json.toMutableMap().apply { remove("facts") })

    val failure = assertFailsWith<IllegalArgumentException> { HarnessJson.fromJson(without) }
    assertTrue(failure.message.orEmpty().contains("\"facts\""), failure.message.orEmpty())
  }

  @Test
  fun `a list that is not one is named where it is read`() {
    val json = HarnessJson.toJson(report())
    val broken = JsonObject(json.toMutableMap().apply { put("rolls", JsonObject(emptyMap())) })

    val failure = assertFailsWith<IllegalArgumentException> { HarnessJson.fromJson(broken) }
    assertTrue(failure.message.orEmpty().contains("\"rolls\""), failure.message.orEmpty())
  }

  @Test
  fun `a whole number that is not one is named too`() {
    val failure = assertFailsWith<IllegalArgumentException> { HarnessJson.decode("""{"schema":"one"}""") }

    assertTrue(failure.message.orEmpty().contains("\"schema\""), failure.message.orEmpty())
  }

  @Test
  fun `a number that is not one is refused where it is read`() {
    val json = HarnessJson.toJson(report())
    val facts = json["facts"] as JsonObject
    val broken =
      JsonObject(
        json.toMutableMap().apply {
          put("facts", JsonObject(facts.toMutableMap().apply { put("dieScale", JsonPrimitive("big")) }))
        },
      )

    val failure = assertFailsWith<IllegalArgumentException> { HarnessJson.fromJson(broken) }
    assertTrue(failure.message.orEmpty().contains("\"dieScale\""), failure.message.orEmpty())
  }

  @Test
  fun `a flag that is not true or false is refused`() {
    val json = HarnessJson.toJson(report())
    val facts = json["facts"] as JsonObject
    val device = facts["device"] as JsonObject
    val brokenDevice = JsonObject(device.toMutableMap().apply { put("emulator", JsonPrimitive("yes")) })
    val broken =
      JsonObject(
        json.toMutableMap().apply {
          put("facts", JsonObject(facts.toMutableMap().apply { put("device", brokenDevice) }))
        },
      )

    val failure = assertFailsWith<IllegalArgumentException> { HarnessJson.fromJson(broken) }
    assertTrue(failure.message.orEmpty().contains("\"emulator\""), failure.message.orEmpty())
  }

  private fun report(): HarnessReport =
    HarnessReport.of(
      facts(),
      listOf(
        RollRecord(
          index = 0,
          seed = -4_611_686_018_427_387_904L,
          steps = 143,
          wallMillis = 18.25,
          corrections = 9,
          postRestCorrections = 0,
          rethrows = 1,
          forcedSettles = 0,
          stackedAtRest = 0,
          deepestDiePenetrationMm = 0.031_25,
        ),
        RollRecord(
          index = 1,
          seed = 12L,
          steps = 1_440,
          wallMillis = 180.5,
          corrections = 11,
          postRestCorrections = 0,
          rethrows = 3,
          forcedSettles = 2,
          stackedAtRest = 1,
          deepestDiePenetrationMm = 0.5,
        ),
      ),
    )

  private fun facts(): RunFacts =
    RunFacts(
      label = "20d20",
      shapeId = "d20",
      diceCount = 20,
      dieScale = 0.75,
      length = RunLength.Rolls(2),
      rolls = 2,
      framePaced = false,
      seed = 1L,
      device = DeviceFacts(model = "Pixel 10a", abi = "arm64-v8a", androidApi = 37, emulator = false),
      startedAtEpochMs = 1_700_000_000_000L,
    )

  private companion object {
    /** Any schema this harness does not write, which is what the check is for. */
    const val SOME_OTHER_SCHEMA = 99
  }
}

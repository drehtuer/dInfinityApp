package de.drehtuer.dinfinity.simulation.harness

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The harness document, written and read.
 *
 * Through the JSON DOM rather than a deserializer, which is this project's
 * standing habit (see the note in `gradle/libs.versions.toml`) and here has a
 * second reason: the document is the artefact somebody opens after a run that
 * went wrong, so every field is written by name, read by name, and a field that
 * is missing or the wrong shape is named in the failure rather than appearing
 * as a stack trace inside a generated decoder. It also needs no compiler
 * plugin, so the module stays a plain Kotlin library.
 *
 * There are two functions here per part of the document, one each way, and
 * that is the whole of it — hence the suppression rather than a second object
 * holding the same halves under another name.
 *
 * [encode] and [decode] are each other's inverse, and a JVM test asserts that
 * on a whole report — which is the only reason the device is trusted to write
 * a file nobody watched it write.
 */
@Suppress("TooManyFunctions")
object HarnessJson {
  private val format =
    Json {
      prettyPrint = true
      prettyPrintIndent = "  "
    }

  /** [report] as the text written to the device's external files directory. */
  fun encode(report: HarnessReport): String = format.encodeToString(JsonObject.serializer(), toJson(report))

  /** And back again, from the text pulled off it. */
  fun decode(text: String): HarnessReport = fromJson(format.parseToJsonElement(text) as JsonObject)

  /** [report] as a JSON object. */
  fun toJson(report: HarnessReport): JsonObject =
    buildJsonObject {
      put("schema", HarnessReport.SCHEMA)
      put("facts", facts(report.facts))
      put("summary", summary(report.summary))
      put("scorecard", scorecard(report.scorecard))
      put(
        "rolls",
        buildJsonArray {
          report.rolls.forEach { add(roll(it)) }
        },
      )
    }

  /** And a JSON object read back as a report. */
  fun fromJson(json: JsonObject): HarnessReport {
    val schema = json.wholeNumber("schema").toInt()
    require(schema == HarnessReport.SCHEMA) {
      "this run was written by harness schema $schema, and this one reads ${HarnessReport.SCHEMA}"
    }
    return HarnessReport(
      facts = readFacts(json.obj("facts")),
      rolls = json.array("rolls").map { readRoll(it as JsonObject) },
      summary = readSummary(json.obj("summary")),
      scorecard = readScorecard(json.obj("scorecard")),
    )
  }

  private fun facts(facts: RunFacts): JsonObject =
    buildJsonObject {
      put("label", facts.label)
      put("shape", facts.shapeId)
      put("diceCount", facts.diceCount)
      put("dieScale", facts.dieScale)
      put("length", length(facts.length))
      put("rolls", facts.rolls)
      put("framePaced", facts.framePaced)
      put("seed", facts.seed)
      put("startedAtEpochMs", facts.startedAtEpochMs)
      put("device", device(facts.device))
    }

  private fun readFacts(json: JsonObject): RunFacts =
    RunFacts(
      label = json.text("label"),
      shapeId = json.text("shape"),
      diceCount = json.wholeNumber("diceCount").toInt(),
      dieScale = json.number("dieScale"),
      length = readLength(json.obj("length")),
      rolls = json.wholeNumber("rolls").toInt(),
      framePaced = json.flag("framePaced"),
      seed = json.wholeNumber("seed"),
      device = readDevice(json.obj("device")),
      startedAtEpochMs = json.wholeNumber("startedAtEpochMs"),
    )

  /**
   * What the run was asked for, written so that a reader can tell a soak from
   * a thousand rolls without counting the records.
   *
   * Tagged by `kind` rather than by which field happens to be there: a
   * document read by name is a document whose failures name the field, and
   * "one of these two keys is present" is not a name.
   */
  private fun length(length: RunLength): JsonObject =
    buildJsonObject {
      when (length) {
        is RunLength.Rolls -> {
          put("kind", ROLLS_KIND)
          put("rolls", length.rolls)
        }

        is RunLength.Soak -> {
          put("kind", SOAK_KIND)
          put("seconds", length.seconds)
        }
      }
    }

  private fun readLength(json: JsonObject): RunLength =
    when (val kind = json.text("kind")) {
      ROLLS_KIND -> RunLength.Rolls(json.wholeNumber("rolls").toInt())
      SOAK_KIND -> RunLength.Soak(json.number("seconds"))
      else -> throw IllegalArgumentException("the harness document's \"kind\" is $kind, which is no kind of run")
    }

  private fun device(device: DeviceFacts): JsonObject =
    buildJsonObject {
      put("model", device.model)
      put("abi", device.abi)
      put("androidApi", device.androidApi)
      put("emulator", device.emulator)
    }

  private fun readDevice(json: JsonObject): DeviceFacts =
    DeviceFacts(
      model = json.text("model"),
      abi = json.text("abi"),
      androidApi = json.wholeNumber("androidApi").toInt(),
      emulator = json.flag("emulator"),
    )

  private fun roll(record: RollRecord): JsonObject =
    buildJsonObject {
      put("index", record.index)
      put("seed", record.seed)
      put("steps", record.steps)
      put("wallMillis", record.wallMillis)
      put("corrections", record.corrections)
      put("postRestCorrections", record.postRestCorrections)
      put("rethrows", record.rethrows)
      put("forcedSettles", record.forcedSettles)
      put("stackedAtRest", record.stackedAtRest)
      put("deepestDiePenetrationMm", record.deepestDiePenetrationMm)
      put("medianTurnsAfterLanding", record.medianTurnsAfterLanding)
    }

  private fun readRoll(json: JsonObject): RollRecord =
    RollRecord(
      index = json.wholeNumber("index").toInt(),
      seed = json.wholeNumber("seed"),
      steps = json.wholeNumber("steps").toInt(),
      wallMillis = json.number("wallMillis"),
      corrections = json.wholeNumber("corrections").toInt(),
      postRestCorrections = json.wholeNumber("postRestCorrections").toInt(),
      rethrows = json.wholeNumber("rethrows").toInt(),
      forcedSettles = json.wholeNumber("forcedSettles").toInt(),
      stackedAtRest = json.wholeNumber("stackedAtRest").toInt(),
      deepestDiePenetrationMm = json.number("deepestDiePenetrationMm"),
      medianTurnsAfterLanding = json.number("medianTurnsAfterLanding"),
    )

  private fun summary(summary: HarnessSummary): JsonObject =
    buildJsonObject {
      put("rolls", summary.rolls)
      put("dice", summary.dice)
      put("settleSeconds", distribution(summary.settleSeconds))
      put("wallMillis", distribution(summary.wallMillis))
      put("stepWallMillis", distribution(summary.stepWallMillis))
      put("corrections", summary.corrections)
      put("postRestCorrections", summary.postRestCorrections)
      put("rethrows", summary.rethrows)
      put("forcedSettles", summary.forcedSettles)
      put("stackedAtRest", summary.stackedAtRest)
      put("capsReached", summary.capsReached)
      put("deepestDiePenetrationMm", summary.deepestDiePenetrationMm)
      put("turnsAfterLanding", distribution(summary.turnsAfterLanding))
      // Absent, not zero, for a run that measured no frames. A reader of the
      // file sees the same thing the scorecard says: nothing was measured.
      summary.frames?.let { put("frames", frames(it)) }
      // Derived, and written anyway: these two are the figures the targets are
      // stated in, and a reader of the file should not have to divide.
      put("correctedShare", summary.correctedShare)
      put("rethrownShare", summary.rethrownShare)
    }

  private fun readSummary(json: JsonObject): HarnessSummary =
    HarnessSummary(
      rolls = json.wholeNumber("rolls").toInt(),
      dice = json.wholeNumber("dice"),
      settleSeconds = readDistribution(json.obj("settleSeconds")),
      wallMillis = readDistribution(json.obj("wallMillis")),
      stepWallMillis = readDistribution(json.obj("stepWallMillis")),
      corrections = json.wholeNumber("corrections"),
      postRestCorrections = json.wholeNumber("postRestCorrections"),
      rethrows = json.wholeNumber("rethrows"),
      forcedSettles = json.wholeNumber("forcedSettles"),
      stackedAtRest = json.wholeNumber("stackedAtRest"),
      capsReached = json.wholeNumber("capsReached").toInt(),
      deepestDiePenetrationMm = json.number("deepestDiePenetrationMm"),
      turnsAfterLanding = readDistribution(json.obj("turnsAfterLanding")),
      frames = json["frames"]?.let { readFrames(json.obj("frames")) },
    )

  private fun frames(frames: FrameSummary): JsonObject =
    buildJsonObject {
      put("frames", frames.frames)
      put("droppedSteps", frames.droppedSteps)
      put("millis", distribution(frames.millis))
      put("drawn", frames.drawn)
    }

  private fun readFrames(json: JsonObject): FrameSummary =
    FrameSummary(
      frames = json.wholeNumber("frames"),
      droppedSteps = json.wholeNumber("droppedSteps"),
      millis = readDistribution(json.obj("millis")),
      drawn = json.flag("drawn"),
    )

  private fun distribution(distribution: Distribution): JsonObject =
    buildJsonObject {
      put("median", distribution.median)
      put("p99", distribution.p99)
      put("worst", distribution.worst)
    }

  private fun readDistribution(json: JsonObject): Distribution =
    Distribution(
      median = json.number("median"),
      p99 = json.number("p99"),
      worst = json.number("worst"),
    )

  private fun scorecard(scorecard: Scorecard): JsonObject =
    buildJsonObject {
      put("passed", scorecard.passed)
      put(
        "targets",
        buildJsonArray {
          scorecard.rows.forEach { result ->
            add(
              buildJsonObject {
                put("name", result.name)
                put("bar", result.bar)
                put("measured", result.measured)
                put("result", result.outcome.token)
              },
            )
          }
        },
      )
    }

  /** The two kinds of run, as they are written into the document. */
  private const val ROLLS_KIND = "rolls"

  private const val SOAK_KIND = "soak"

  private fun readScorecard(json: JsonObject): Scorecard =
    Scorecard(
      json.array("targets").map { element ->
        val row = element as JsonObject
        TargetResult(
          name = row.text("name"),
          bar = row.text("bar"),
          measured = row.text("measured"),
          outcome = TargetOutcome.ofToken(row.text("result")),
        )
      },
    )
}

private fun JsonObject.obj(name: String): JsonObject = this[name] as? JsonObject ?: malformed(name, "an object")

private fun JsonObject.array(name: String): JsonArray = this[name] as? JsonArray ?: malformed(name, "a list")

private fun JsonObject.primitive(name: String): JsonPrimitive =
  this[name] as? JsonPrimitive ?: malformed(name, "a value")

private fun JsonObject.text(name: String): String = primitive(name).content

private fun JsonObject.wholeNumber(name: String): Long =
  primitive(name).content.toLongOrNull() ?: malformed(name, "a whole number")

private fun JsonObject.number(name: String): Double =
  primitive(name).content.toDoubleOrNull() ?: malformed(name, "a number")

private fun JsonObject.flag(name: String): Boolean =
  primitive(name).content.toBooleanStrictOrNull() ?: malformed(name, "true or false")

/**
 * What a field that is not there, or not what it should be, comes to.
 *
 * Named rather than thrown at each call site so that every one of them says
 * the same thing in the same words, and says *which* field — which is the
 * difference between a document somebody can fix and a stack trace.
 */
private fun malformed(
  name: String,
  wanted: String,
): Nothing = throw IllegalArgumentException("the harness document's \"$name\" is not $wanted")

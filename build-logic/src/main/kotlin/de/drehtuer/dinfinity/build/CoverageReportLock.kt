package de.drehtuer.dinfinity.build

import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

/**
 * A build-wide lock that lets only one JaCoCo report be written at a time.
 *
 * JaCoCo's HTML formatter copies its static resources — stylesheets, scripts,
 * icons — out of a jar it reaches through the class loader. When two report
 * tasks run at once in the same daemon they share that open archive, and
 * whichever finishes first closes it underneath the other:
 *
 *     java.util.zip.ZipException: ZipFile closed
 *         at org.jacoco.report.internal.html.resources.Resources.copyResource
 *         at org.jacoco.report.html.HTMLFormatter.createVisitor
 *
 * That failed a build on `main` with 24 modules reporting in parallel, and
 * nothing in the project had changed to cause it — which is what a race looks
 * like. The service carries no state; `maxParallelUsages = 1` is the whole
 * point of it.
 *
 * Serialising costs almost nothing: writing these reports is milliseconds of
 * work per module, against test runs measured in seconds.
 */
abstract class CoverageReportLock : BuildService<BuildServiceParameters.None>

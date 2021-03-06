// Copyright 2014 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.devtools.build.lib.runtime;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.devtools.build.lib.rules.test.TestLogHelper;
import com.google.devtools.build.lib.rules.test.TestStrategy.TestOutputFormat;
import com.google.devtools.build.lib.util.LoggingUtil;
import com.google.devtools.build.lib.util.io.AnsiTerminalPrinter;
import com.google.devtools.build.lib.util.io.AnsiTerminalPrinter.Mode;
import com.google.devtools.build.lib.vfs.Path;
import com.google.devtools.build.lib.view.test.TestStatus.BlazeTestStatus;
import com.google.devtools.build.lib.view.test.TestStatus.FailedTestCasesStatus;
import com.google.devtools.build.lib.view.test.TestStatus.TestCase;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Print test statistics in human readable form.
 */
public class TestSummaryPrinter {

  /**
   * Print the cached test log to the given printer.
   */
  public static void printCachedOutput(TestSummary summary,
      TestOutputFormat testOutput,
      AnsiTerminalPrinter printer) {

    String testName = summary.getTarget().getLabel().toString();
    List<String> allLogs = new ArrayList<>();
    for (Path path : summary.getFailedLogs()) {
      allLogs.add(path.getPathString());
    }
    for (Path path : summary.getPassedLogs()) {
      allLogs.add(path.getPathString());
    }
    printer.printLn("" + TestSummary.getStatusMode(summary.getStatus()) + summary.getStatus() + ": "
        + Mode.DEFAULT + testName + " (see " + Joiner.on(' ').join(allLogs) + ")");
    printer.printLn(Mode.INFO + "INFO: " + Mode.DEFAULT + "From Testing " + testName);

    // Whether to output the target at all was checked by the caller.
    // Now check whether to output failing shards.
    if (TestLogHelper.shouldOutputTestLog(testOutput, false)) {
      for (Path path : summary.getFailedLogs()) {
        try {
          TestLogHelper.writeTestLog(path, testName, printer.getOutputStream());
        } catch (IOException e) {
          printer.printLn("==================== Could not read test output for " + testName);
          LoggingUtil.logToRemote(Level.WARNING, "Error while reading test log", e);
        }
      }
    }

    // And passing shards, independently.
    if (TestLogHelper.shouldOutputTestLog(testOutput, true)) {
      for (Path path : summary.getPassedLogs()) {
        try {
          TestLogHelper.writeTestLog(path, testName, printer.getOutputStream());
        } catch (Exception e) {
          printer.printLn("==================== Could not read test output for " + testName);
          LoggingUtil.logToRemote(Level.WARNING, "Error while reading test log", e);
        }
      }
    }
  }

  private static String statusString(BlazeTestStatus status) {
    return status.toString().replace('_', ' ');
  }

  /**
   * Prints summary status for a single test.
   * @param terminalPrinter The printer to print to
   */
  public static void print(
      TestSummary summary,
      AnsiTerminalPrinter terminalPrinter,
      boolean verboseSummary, boolean printFailedTestCases) {
    // Skip output for tests that failed to build.
    if (summary.getStatus() == BlazeTestStatus.FAILED_TO_BUILD) {
      return;
    }
    String message = getCacheMessage(summary) + statusString(summary.getStatus());
    terminalPrinter.print(
        Strings.padEnd(summary.getTarget().getLabel().toString(), 78 - message.length(), ' ')
        + " " + TestSummary.getStatusMode(summary.getStatus()) + message + Mode.DEFAULT
        + (verboseSummary ? getAttemptSummary(summary) + getTimeSummary(summary) : "") + "\n");

    if (printFailedTestCases && summary.getStatus() == BlazeTestStatus.FAILED) {
      if (summary.getFailedTestCasesStatus() == FailedTestCasesStatus.NOT_AVAILABLE) {
        terminalPrinter.print(
            Mode.WARNING + "    (individual test case information not available) "
            + Mode.DEFAULT + "\n");
      } else {
        for (TestCase testCase : summary.getFailedTestCases()) {
          if (testCase.getStatus() != TestCase.Status.PASSED) {
            TestSummaryPrinter.printTestCase(terminalPrinter, testCase);
          }
        }

        if (summary.getFailedTestCasesStatus() != FailedTestCasesStatus.FULL) {
          terminalPrinter.print(
              Mode.WARNING
              + "    (some shards did not report details, list of failed test"
              + " cases incomplete)\n"
              + Mode.DEFAULT);
        }
      }
    }

    if (printFailedTestCases) {
      // In this mode, test output and coverage files would just clutter up
      // the output.
      return;
    }

    for (String warning : summary.getWarnings()) {
      terminalPrinter.print("  " + AnsiTerminalPrinter.Mode.WARNING + "WARNING: "
          + AnsiTerminalPrinter.Mode.DEFAULT + warning + "\n");
    }

    for (Path path : summary.getFailedLogs()) {
      if (path.exists()) {
        // Don't use getPrettyPath() here - we want to print the absolute path,
        // so that it cut and paste into a different terminal, and we don't
        // want to use the blaze-bin etc. symbolic links because they could be changed
        // by a subsequent build with different options.
        terminalPrinter.print("  " + path.getPathString() + "\n");
      }
    }
    for (Path path : summary.getCoverageFiles()) {
      // Print only non-trivial coverage files.
      try {
        if (path.exists() && path.getFileSize() > 0) {
          terminalPrinter.print("  " + path.getPathString() + "\n");
        }
      } catch (IOException e) {
        LoggingUtil.logToRemote(Level.WARNING, "Error while reading coverage data file size",
            e);
      }
    }
  }

  /**
   * Prints the result of an individual test case. It is assumed not to have
   * passed, since passed test cases are not reported.
   */
  static void printTestCase(
      AnsiTerminalPrinter terminalPrinter, TestCase testCase) {
    String timeSummary;
    if (testCase.hasRunDurationMillis()) {
      timeSummary = " ("
          + timeInSec(testCase.getRunDurationMillis(), TimeUnit.MILLISECONDS)
          + ")";
    } else {
      timeSummary = "";
    }

    terminalPrinter.print(
        "    "
        + Mode.ERROR
        + Strings.padEnd(testCase.getStatus().toString(), 8, ' ')
        + Mode.DEFAULT
        + testCase.getClassName()
        + "."
        + testCase.getName()
        + timeSummary
        + "\n");
  }

  /**
   * Return the given time in seconds, to 1 decimal place,
   * i.e. "32.1s".
   */
  static String timeInSec(long time, TimeUnit unit) {
    double ms = TimeUnit.MILLISECONDS.convert(time, unit);
    return String.format("%.1fs", ms / 1000.0);
  }

  static String getAttemptSummary(TestSummary summary) {
    int attempts = summary.getPassedLogs().size() + summary.getFailedLogs().size();
    if (attempts > 1) {
      // Print number of failed runs for failed tests if testing was completed.
      if (summary.getStatus() == BlazeTestStatus.FLAKY) {
        return ", failed in " + summary.getFailedLogs().size() + " out of " + attempts;
      }
      if (summary.getStatus() == BlazeTestStatus.TIMEOUT
          || summary.getStatus() == BlazeTestStatus.FAILED) {
        return " in " + summary.getFailedLogs().size() + " out of " + attempts;
      }
    }
    return "";
  }

  static String getCacheMessage(TestSummary summary) {
    if (summary.getNumCached() == 0 || summary.getStatus() == BlazeTestStatus.INCOMPLETE) {
      return "";
    } else if (summary.getNumCached() == summary.totalRuns()) {
      return "(cached) ";
    } else {
      return String.format("(%d/%d cached) ", summary.getNumCached(), summary.totalRuns());
    }
  }

  static String getTimeSummary(TestSummary summary) {
    if (summary.getTestTimes().isEmpty()) {
      return "";
    } else if (summary.getTestTimes().size() == 1) {
      return " in " + timeInSec(summary.getTestTimes().get(0), TimeUnit.MILLISECONDS);
    } else {
      // We previously used com.google.math for this, which added about 1 MB of deps to the total
      // size. If we re-introduce a dependency on that package, we could revert this change.
      long min = summary.getTestTimes().get(0).longValue(), max = min, sum = 0;
      double sumOfSquares = 0.0;
      for (Long l : summary.getTestTimes()) {
        long value = l.longValue();
        min = Math.min(value, min);
        max = Math.max(value, max);
        sum += value;
        sumOfSquares += ((double) value) * (double) value;
      }
      double mean = ((double) sum) / summary.getTestTimes().size();
      double stddev = Math.sqrt((sumOfSquares - sum * mean) / summary.getTestTimes().size());
      // For sharded tests, we print the max time on the same line as
      // the test, and then print more detailed info about the
      // distribution of times on the next line.
      String maxTime = timeInSec(max, TimeUnit.MILLISECONDS);
      return String.format(
          " in %s\n  Stats over %d runs: max = %s, min = %s, avg = %s, dev = %s",
          maxTime,
          summary.getTestTimes().size(),
          maxTime,
          timeInSec(min, TimeUnit.MILLISECONDS),
          timeInSec((long) mean, TimeUnit.MILLISECONDS),
          timeInSec((long) stddev, TimeUnit.MILLISECONDS));
    }
  }
}

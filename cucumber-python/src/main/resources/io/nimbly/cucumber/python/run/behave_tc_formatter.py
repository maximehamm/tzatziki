# -*- coding: utf-8 -*-
#
# Cucumber for Python
# behave formatter that emits TeamCity service messages so IntelliJ IDEA's
# SMTRunner builds a live test tree (Feature -> Scenario -> Step).
#
# Loaded by behave via:  behave -f behave_tc_formatter:TeamcityFormatter
# (the directory holding this file is added to PYTHONPATH by the plugin).
#
# Tree model:  Feature = test-suite, Scenario = test-suite, Step = test.

import os
import sys

from behave.formatter.base import Formatter


def _loc(filename, line):
    # IntelliJ's FileUrlProvider needs an ABSOLUTE path to resolve the
    # locationHint back to the Gherkin element (for gutter test progression).
    try:
        path = os.path.abspath(filename)
    except Exception:
        path = filename
    return "file://%s:%s" % (path, line)


def _ts():
    # ISO-8601 with millis, matching what SMTRunner expects (optional but nice).
    import datetime
    return datetime.datetime.now().strftime("%Y-%m-%dT%H:%M:%S.%f")[:-3]


def _esc(value):
    if value is None:
        return ""
    s = value if isinstance(value, str) else str(value)
    return (s.replace("|", "||")
             .replace("'", "|'")
             .replace("\n", "|n")
             .replace("\r", "|r")
             .replace("[", "|[")
             .replace("]", "|]"))


class TeamcityFormatter(Formatter):
    name = "teamcity"
    description = "TeamCity service messages (IntelliJ SMTRunner)"

    def __init__(self, stream_opener, config):
        super(TeamcityFormatter, self).__init__(stream_opener, config)
        self._feature_open = False
        self._scenario_open = False
        # Lazy scenario suite: emitted on the FIRST executed step only, so behave's
        # skipped scenarios (e.g. the non-matching rows when filtering with `-n`)
        # produce no tree node at all.
        self._pending_scenario = None  # (name, locationHint) or None
        # Step bookkeeping: testStarted is emitted in match() — i.e. BEFORE the step
        # runs — so the gutter progression bar (and a debugger paused inside the step)
        # covers the CURRENT step line, not the previous one.
        self._steps = []
        self._match_index = 0
        self._open_step = None  # name of the step started but not yet finished

    # -- low level -----------------------------------------------------------
    def _msg(self, _kind, **attrs):
        parts = ["##teamcity[", _kind]
        for key, val in attrs.items():
            parts.append(" %s='%s'" % (key, _esc(val)))
        parts.append("]\n")
        sys.stdout.write("".join(parts))
        sys.stdout.flush()

    def _close_scenario(self):
        if self._scenario_open:
            self._msg("testSuiteFinished", name=self._scenario_name)
            self._scenario_open = False
        # Drop a pending scenario that never executed a step (skipped / filtered out).
        self._pending_scenario = None

    def _open_pending_scenario(self):
        if self._pending_scenario is not None and not self._scenario_open:
            name, loc = self._pending_scenario
            self._scenario_name = name
            self._msg("testSuiteStarted", name=name, locationHint=loc)
            self._scenario_open = True
            self._pending_scenario = None

    def _close_feature(self):
        self._close_scenario()
        if self._feature_open:
            self._msg("testSuiteFinished", name=self._feature_name)
            self._feature_open = False

    # -- behave Formatter API ------------------------------------------------
    def feature(self, feature):
        self._close_feature()
        self._feature_name = "Feature: %s" % feature.name
        self._msg("testSuiteStarted", name=self._feature_name,
                  locationHint=_loc(feature.filename, feature.line))
        self._feature_open = True

    def scenario(self, scenario):
        self._close_scenario()
        # behave appends a " -- @<row>.<col> <example>" suffix to Scenario Outline
        # rows; strip it so the test tree shows clean scenario names.
        name = scenario.name
        marker = name.find(" -- @")
        if marker != -1:
            name = name[:marker]
        # Defer emission until the first executed step (see _open_pending_scenario).
        self._pending_scenario = (
            "%s: %s" % (scenario.keyword, name),
            _loc(scenario.filename, scenario.line),
        )
        self._steps = []
        self._match_index = 0
        self._open_step = None

    def _step_name(self, step):
        name = ("%s %s" % (getattr(step, "keyword", ""), getattr(step, "name", ""))).strip()
        return name or "step"

    def step(self, step):
        # Buffered when the scenario's steps are registered (before the run).
        self._steps.append(step)

    def match(self, match):
        # A step is about to EXECUTE → start it now so the progression bar reaches
        # this line even while the debugger is paused inside it.
        self._open_pending_scenario()
        if self._match_index >= len(self._steps):
            return
        step = self._steps[self._match_index]
        self._match_index += 1
        name = self._step_name(step)
        self._msg("testStarted", name=name,
                  locationHint=_loc(getattr(step, "filename", ""), getattr(step, "line", "")))
        self._open_step = name

    def result(self, step):
        # behave >= 1.2.6 passes the executed step; older versions passed a
        # result object that still exposes .status/.name/.duration.
        self._open_pending_scenario()
        step_name = self._step_name(step)
        status = getattr(getattr(step, "status", None), "name", None) \
            or str(getattr(step, "status", "unknown"))
        # Undefined / unmatched steps get no match() call — start them here.
        if self._open_step != step_name:
            self._msg("testStarted", name=step_name,
                      locationHint=_loc(getattr(step, "filename", ""), getattr(step, "line", "")))
        self._open_step = None

        if status in ("failed", "error"):
            details = getattr(step, "error_message", None) or ""
            exc = getattr(step, "exception", None)
            self._msg("testFailed", name=step_name,
                      message=str(exc) if exc else "Step failed",
                      details=details)
        elif status in ("undefined",):
            self._msg("testFailed", name=step_name,
                      message="Undefined step", details="No matching step definition")
        elif status in ("skipped", "untested"):
            self._msg("testIgnored", name=step_name, message=status)

        duration_ms = int(round((getattr(step, "duration", 0) or 0) * 1000))
        self._msg("testFinished", name=step_name, duration=duration_ms)

    def eof(self):
        # End of a feature file.
        self._close_feature()

    def close(self):
        self._close_feature()
        try:
            super(TeamcityFormatter, self).close()
        except Exception:
            pass

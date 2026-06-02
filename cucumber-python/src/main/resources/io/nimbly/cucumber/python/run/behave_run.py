# -*- coding: utf-8 -*-
#
# Cucumber for Python
# Tiny entry point that runs behave as a *script* (not `-m behave`), so the
# IntelliJ Python debugger (pydevd) can wrap it cleanly with `--file`.
# All behave arguments are forwarded via sys.argv.

import sys

from behave.__main__ import main

if __name__ == "__main__":
    sys.exit(main())

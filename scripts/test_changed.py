"""Runs only the app's unit tests that touch what changed (Devin's choice, 2026-10-28: keep every test, but don't
run all of them on every edit). The full set still runs once before shipping (BUILD.md).

    python scripts/test_changed.py              # changes since the last commit (staged, unstaged, new files)
    python scripts/test_changed.py --since main~3
    python scripts/test_changed.py --dry-run    # only say which tests it would run

A test class runs when its source mentions a changed Kotlin file's name or one of its top-level declarations
(a changed MarkdownMedia.kt runs MarkdownMediaParseTest; a changed ChatMessage.kt runs every test using it).
A changed test runs itself. Build files, resources and the manifest run everything. When one test fails, rerun
just that class: gradle testDebugUnitTest --tests 'com.example.ui.DesignTokensTest'
"""
import argparse
import os
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MAIN = ROOT / "app" / "src" / "main" / "java"
TESTS = ROOT / "app" / "src" / "test" / "java"
GRADLE = os.environ.get("GRADLE", r"C:\Users\devin\.tools\gradle-9.3.1\bin\gradle.bat")
# Top-level declarations a test can use (private ones can't, and their names, like open or download, matched
# unrelated tests)
DECL = re.compile(r"^(?:(?:internal|public|data|sealed|enum|abstract|open|inline|value|annotation)\s+)*"
                  r"(?:fun|class|object|interface|val|var|typealias)\s+(?:<[^>]*>\s*)?(?:[\w.]+\.)?(\w+)", re.M)
EVERYTHING = ("app/build.gradle", "build.gradle", "settings.gradle", "gradle/", "gradle.properties",
              "app/src/main/res/", "app/src/main/AndroidManifest.xml")


def changed_files(since):
    def git(*args):
        return subprocess.run(["git", *args], cwd=ROOT, capture_output=True, text=True, check=True).stdout.split()
    files = set(git("diff", "--name-only", since)) | set(git("ls-files", "--others", "--exclude-standard"))
    return sorted(f.replace("\\", "/") for f in files)


def test_classes():
    """{fully qualified test class: its source}"""
    found = {}
    for f in TESTS.rglob("*.kt"):
        src = f.read_text(encoding="utf-8")
        pkg = re.search(r"^package\s+([\w.]+)", src, re.M)
        found[f"{pkg.group(1)}.{f.stem}" if pkg else f.stem] = src
    return found


def pick(files, tests):
    if any(f.startswith(EVERYTHING) for f in files):
        return None                                                  # None = all of them
    names = set()
    chosen = set()
    for f in files:
        path = ROOT / f
        if f.startswith("app/src/test/") and f.endswith(".kt"):
            chosen.update(t for t in tests if t.endswith("." + path.stem) or t == path.stem)
        elif f.startswith("app/src/main/java/") and f.endswith(".kt") and path.is_file():
            names.add(path.stem)
            names.update(DECL.findall(path.read_text(encoding="utf-8")))
    names = {n for n in names if len(n) > 2}
    if names:
        word = re.compile(r"\b(?:" + "|".join(map(re.escape, sorted(names))) + r")\b")
        chosen.update(t for t, src in tests.items() if word.search(src))
    return sorted(chosen)


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("--since", default="HEAD", help="compare with this commit (default: the last commit)")
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()

    files = changed_files(args.since)
    tests = test_classes()
    chosen = pick(files, tests)
    print(f"{len(files)} changed file(s)")
    if chosen is None:
        print("build files or resources changed: all tests")
        cmd = [GRADLE, "-q", "testDebugUnitTest"]
    elif not chosen:
        print("no test touches these changes")
        return 0
    else:
        print("tests:", ", ".join(t.rsplit(".", 1)[-1] for t in chosen))
        cmd = [GRADLE, "-q", "testDebugUnitTest"] + [a for t in chosen for a in ("--tests", t)]
    if args.dry_run:
        return 0
    return subprocess.run(cmd, cwd=ROOT).returncode


if __name__ == "__main__":
    sys.exit(main())

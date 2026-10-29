#!/bin/sh
# Make sure the tracked pre-commit gate (scripts/hooks/pre-commit) is what git
# runs, wherever git looks for hooks.
#
# Three layouts are accepted: a symlink in the hooks dir git resolves; the
# standard's pre-commit runner at that path with the gate as its .legacy hook
# (pre-commit runs the legacy hook first, then its own); or, where
# core.hooksPath still points at bd's directory, a PROJECT GATE block in bd's
# pre-commit. Idempotent; refuses to clobber a hook it does not recognise.
set -e
root=$(git rev-parse --show-toplevel)
cd "$root"
chmod +x scripts/hooks/pre-commit
hooks_dir=$(git rev-parse --git-path hooks)
dest="$hooks_dir/pre-commit"

if grep -q 'BEGIN PROJECT GATE' "$dest" 2>/dev/null; then
    echo "ok: $dest calls scripts/hooks/pre-commit (core.hooksPath=$(git config core.hooksPath))"
    exit 0
fi
if [ -L "$dest" ] && [ "$(readlink "$dest")" = "$root/scripts/hooks/pre-commit" ]; then
    echo "ok: $dest -> scripts/hooks/pre-commit"
    exit 0
fi
# pre-commit (the standard's hook runner) owns $dest and runs the gate through
# the .legacy file it moved aside: both layers run on every commit.
legacy_target=""
[ -L "$dest.legacy" ] && legacy_target=$(readlink "$dest.legacy")
if grep -q 'pre-commit' "$dest" 2>/dev/null \
    && { [ "$legacy_target" = "$root/scripts/hooks/pre-commit" ] || [ "$legacy_target" = "../../scripts/hooks/pre-commit" ]; }; then
    echo "ok: $dest is pre-commit's runner; it calls scripts/hooks/pre-commit via $dest.legacy"
    exit 0
fi
if grep -q 'pre-commit' "$dest" 2>/dev/null; then
    ln -s "$root/scripts/hooks/pre-commit" "$dest.legacy"
    echo "installed: $dest.legacy -> scripts/hooks/pre-commit (pre-commit's runner at $dest calls it first)"
    exit 0
fi
if [ -e "$dest" ]; then
    echo "error: $dest exists and is not the project gate. If it is bd's hook, run" >&2
    echo "       'bd hooks install' then re-check; otherwise move it aside and re-run." >&2
    exit 1
fi
mkdir -p "$hooks_dir"
ln -s "$root/scripts/hooks/pre-commit" "$dest"
echo "installed: $dest -> scripts/hooks/pre-commit"

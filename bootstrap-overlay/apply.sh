#!/bin/sh
# Repack an official Termux aarch64 bootstrap with this fork's customizations.
#
# Usage: ./apply.sh <official-bootstrap-aarch64.zip> <out.zip>
set -eu
[ $# -eq 2 ] || { echo "usage: $0 <official-bootstrap.zip> <out.zip>" >&2; exit 1; }

src=$1
out=$2
[ -f "$src" ] || { echo "missing: $src" >&2; exit 1; }

dir=$(mktemp -d)
trap 'rm -rf "$dir"' EXIT

unzip -q "$src" -d "$dir"
cp -a etc/. "$dir"/etc/
chmod 600 "$dir"/etc/motd "$dir"/etc/apt/sources.list "$dir"/etc/termux-login.sh
(cd "$dir" && zip -q -r -X "$out" ./*)

echo "wrote $out"

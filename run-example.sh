#!/usr/bin/env sh
set -eu

classes="${TMPDIR:-/tmp}/fintech-job-alert-classes"
mkdir -p "$classes"
javac -d "$classes" $(find src/main/java -name '*.java')
java -cp "$classes" example.fintech.SettlementReconciliationJob

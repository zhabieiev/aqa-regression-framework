#!/usr/bin/env bash

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REACTOR_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
REACTOR_POM="$REACTOR_DIR/pom.xml"

ALLURE_RESULTS="$SCRIPT_DIR/target/allure-results"
ALLURE_REPORT="$SCRIPT_DIR/target/site/allure-maven-plugin"
LOCAL_HISTORY="$SCRIPT_DIR/local-allure-history"
RUN_COUNTER_FILE="$LOCAL_HISTORY/run-counter.txt"

MODULE="regression-petstore-api"
PORT="${ALLURE_PORT:-8000}"
REPORT_URL="http://localhost:${PORT}/"

TEST_EXIT_CODE=0
REPORT_EXIT_CODE=0
SERVER_PID=""

print_section() {
    echo
    echo "========================================"
    echo "$1"
    echo "========================================"
}

open_browser() {
    local url="$1"

    if command -v cmd.exe >/dev/null 2>&1; then
        cmd.exe /c start "" "$url" >/dev/null 2>&1
    elif command -v xdg-open >/dev/null 2>&1; then
        xdg-open "$url" >/dev/null 2>&1
    elif command -v open >/dev/null 2>&1; then
        open "$url" >/dev/null 2>&1
    else
        echo "Browser could not be opened automatically."
        echo "Open manually: $url"
    fi
}

cleanup_server() {
    if [ -n "$SERVER_PID" ] && kill -0 "$SERVER_PID" >/dev/null 2>&1; then
        kill "$SERVER_PID" >/dev/null 2>&1 || true
    fi
}

trap cleanup_server EXIT INT TERM

print_section "Preparing execution"

if [ ! -f "$REACTOR_POM" ]; then
    echo "ERROR: Parent pom.xml was not found: $REACTOR_POM"
    exit 1
fi

mkdir -p "$LOCAL_HISTORY"

if [ -f "$RUN_COUNTER_FILE" ]; then
    RUN_NUMBER="$(tr -dc '0-9' < "$RUN_COUNTER_FILE")"
else
    RUN_NUMBER=0
fi

RUN_NUMBER="${RUN_NUMBER:-0}"
RUN_NUMBER=$((RUN_NUMBER + 1))
printf "%s" "$RUN_NUMBER" > "$RUN_COUNTER_FILE"

RUN_DATE="$(date +"%Y-%m-%d")"
RUN_TIME="$(date +"%H:%M:%S")"
RUN_NAME="Local Petstore API run #${RUN_NUMBER} - ${RUN_DATE} ${RUN_TIME}"

echo "Module:       $MODULE"
echo "Run number:   $RUN_NUMBER"
echo "Run name:     $RUN_NAME"

if [ "$#" -gt 0 ]; then
    echo "Maven args:   $*"
fi

print_section "Cleaning previous build"

mvn -f "$REACTOR_POM" -pl ":$MODULE" -am clean
CLEAN_EXIT_CODE=$?

if [ "$CLEAN_EXIT_CODE" -ne 0 ]; then
    echo "ERROR: Maven clean failed with exit code $CLEAN_EXIT_CODE."
    exit "$CLEAN_EXIT_CODE"
fi

mkdir -p "$ALLURE_RESULTS/history"

print_section "Restoring Allure history"

if find "$LOCAL_HISTORY" -maxdepth 1 -type f -name '*.json' -print -quit 2>/dev/null | grep -q .; then
    cp "$LOCAL_HISTORY"/*.json "$ALLURE_RESULTS/history/"
    echo "Previous Allure history restored."
else
    echo "No previous Allure history found."
fi

print_section "Preparing Allure metadata"

cat > "$ALLURE_RESULTS/executor.json" <<EOF
{
  "name": "Local Maven",
  "type": "local",
  "buildOrder": ${RUN_NUMBER},
  "buildName": "${RUN_NAME}",
  "reportName": "${RUN_NAME}"
}
EOF

JAVA_VERSION="$(java -version 2>&1 | head -n 1)"
OS_NAME="$(uname -s 2>/dev/null || echo "Unknown")"

cat > "$ALLURE_RESULTS/environment.properties" <<EOF
Execution.Type=Local
Execution.Number=${RUN_NUMBER}
Execution.Date=${RUN_DATE}
Execution.Time=${RUN_TIME}
Java.Version=${JAVA_VERSION}
Operating.System=${OS_NAME}
EOF

echo "Allure metadata created."

print_section "Building project dependencies"

mvn -f "$REACTOR_POM" \
    -pl :regression-core \
    -am \
    install \
    -Dmaven.test.skip=true

DEPENDENCIES_EXIT_CODE=$?

if [ "$DEPENDENCIES_EXIT_CODE" -ne 0 ]; then
    echo "ERROR: Project dependencies build failed with exit code $DEPENDENCIES_EXIT_CODE."
    exit "$DEPENDENCIES_EXIT_CODE"
fi

echo "Project dependencies built without running their tests."

print_section "Running tests"

mvn -f "$REACTOR_POM" \
    -pl ":$MODULE" \
    test \
    "$@"

TEST_EXIT_CODE=$?

if [ "$TEST_EXIT_CODE" -eq 0 ]; then
    echo "Tests completed successfully."
else
    echo "Tests failed with exit code $TEST_EXIT_CODE."
    echo "The Allure report will still be generated."
fi

print_section "Generating Allure report"

mvn -f "$REACTOR_POM" -pl ":$MODULE" allure:report
REPORT_EXIT_CODE=$?

if [ "$REPORT_EXIT_CODE" -ne 0 ]; then
    echo "ERROR: Allure report generation failed with exit code $REPORT_EXIT_CODE."
    exit "$REPORT_EXIT_CODE"
fi

echo "Allure report generated successfully."

print_section "Saving Allure Trend history"

if [ -d "$ALLURE_REPORT/history" ]; then
    find "$LOCAL_HISTORY" -maxdepth 1 -type f -name '*.json' -delete
    cp "$ALLURE_REPORT/history"/*.json "$LOCAL_HISTORY/"
    echo "Allure Trend history saved: $LOCAL_HISTORY"
else
    echo "ERROR: Allure history was not generated: $ALLURE_REPORT/history"
    exit 1
fi

print_section "Execution summary"

echo "Run:             $RUN_NUMBER"
echo "Tests exit code: $TEST_EXIT_CODE"
echo "Report:          $ALLURE_REPORT"
echo "History:         $LOCAL_HISTORY"

print_section "Opening Allure report"

if [ ! -f "$ALLURE_REPORT/index.html" ]; then
    echo "ERROR: Allure report index.html was not generated."
    exit 1
fi

if ! command -v jwebserver >/dev/null 2>&1; then
    echo "jwebserver was not found."
    echo "Open the report with another static server: $ALLURE_REPORT"
    exit "$TEST_EXIT_CODE"
fi

echo "Report URL: $REPORT_URL"
echo "Press Ctrl+C to stop the local report server."

jwebserver -p "$PORT" -d "$ALLURE_REPORT" >/dev/null 2>&1 &
SERVER_PID=$!

sleep 2

if ! kill -0 "$SERVER_PID" >/dev/null 2>&1; then
    echo "ERROR: Local report server could not be started."
    echo "Port $PORT may already be in use."
    exit 1
fi

open_browser "$REPORT_URL"
wait "$SERVER_PID" || true

exit "$TEST_EXIT_CODE"
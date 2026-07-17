#!/usr/bin/env bash
#
# Test runner for the AUJava -> C transpiler.

set -u

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
TESTS_DIR="$SCRIPT_DIR"
TMP_DIR="${TMPDIR:-/tmp}"

JAR_ASSEMBLY="$REPO_ROOT/target/AUJava-1.0-SNAPSHOT-jar-with-dependencies.jar"
JAR_PLAIN="$REPO_ROOT/target/AUJava-1.0-SNAPSHOT.jar"

RUN_JAR=""
RUN_CP=""

if [ -f "$JAR_ASSEMBLY" ]; then
    RUN_JAR="$JAR_ASSEMBLY"
elif [ -f "$JAR_PLAIN" ]; then
    RUN_JAR="$JAR_PLAIN"
    if [ -d "$REPO_ROOT/target/dependency" ]; then
        RUN_CP="$REPO_ROOT/target/dependency/*"
    elif [ -d "$REPO_ROOT/target/lib" ]; then
        RUN_CP="$REPO_ROOT/target/lib/*"
    fi
else
    echo "ERROR: could not find compiler jar at:"
    echo "  $JAR_ASSEMBLY"
    echo "  $JAR_PLAIN"
    echo "Build the project first (e.g. 'mvn clean package')."
    exit 1
fi

run_compiler() {
    if [ -n "$RUN_CP" ]; then
        java -cp "$RUN_JAR:$RUN_CP" -jar "$RUN_JAR" "$1" "$2" 2>"$3"
    else
        java -jar "$RUN_JAR" "$1" "$2" 2>"$3"
    fi
}

PASS=0
FAIL=0
TOTAL=0

echo "Using compiler jar: $RUN_JAR"
echo "================================================================"

for src in "$TESTS_DIR"/*.aujava; do
    [ -e "$src" ] || continue
    name="$(basename "$src" .aujava)"
    expected_file="$TESTS_DIR/$name.expected"
    error_file="$TESTS_DIR/$name.error"

    out_c="$TMP_DIR/$name.c"
    out_bin="$TMP_DIR/$name"
    stderr_file="$TMP_DIR/$name.stderr"

    rm -f "$out_c" "$out_bin" "$out_bin.exe" "$stderr_file"

    TOTAL=$((TOTAL + 1))

    if [ -f "$expected_file" ]; then
        # ---------- POSITIVE TEST ----------
        run_compiler "$src" "$out_c" "$stderr_file"
        compiler_exit=$?

        if [ "$compiler_exit" -ne 0 ] || [ ! -f "$out_c" ]; then
            echo "FAIL: $name (compiler failed or produced no .c file; exit=$compiler_exit)"
            [ -s "$stderr_file" ] && sed 's/^/    stderr: /' "$stderr_file"
            FAIL=$((FAIL + 1))
            continue
        fi

        if ! gcc "$out_c" -o "$out_bin" 2>"$TMP_DIR/$name.gcc_stderr"; then
            echo "FAIL: $name (gcc failed to compile generated C code)"
            sed 's/^/    gcc: /' "$TMP_DIR/$name.gcc_stderr"
            FAIL=$((FAIL + 1))
            continue
        fi

        # اعمال تغییرات مخصوص ویندوز (حذف کاراکتر \r)
        actual_out="$("$out_bin" 2>&1 | tr -d '\r')"
        expected_out="$(cat "$expected_file" | tr -d '\r')"

        if [ "$actual_out" == "$expected_out" ]; then
            echo "PASS: $name"
            PASS=$((PASS + 1))
        else
            echo "FAIL: $name (stdout mismatch)"
            echo "    --- expected ---"
            echo "$expected_out" | sed 's/^/    /'
            echo "    --- actual ---"
            echo "$actual_out" | sed 's/^/    /'
            FAIL=$((FAIL + 1))
        fi

    elif [ -f "$error_file" ]; then
        # ---------- NEGATIVE TEST ----------
        rm -f "$out_c"
        run_compiler "$src" "$out_c" "$stderr_file"
        compiler_exit=$?

        reason="$(cat "$error_file" | tr -d '\r')"

        if [ "$compiler_exit" -ne 0 ] && [ ! -s "$out_c" ]; then
            echo "PASS: $name (compiler correctly rejected; reason: $reason)"
            PASS=$((PASS + 1))
        elif [ "$compiler_exit" -eq 0 ]; then
            echo "FAIL: $name (compiler exited 0 but should have rejected input; reason: $reason)"
            FAIL=$((FAIL + 1))
        else
            echo "FAIL: $name (rejected input but still produced a non-empty .c file, per spec it must not; reason: $reason)"
            FAIL=$((FAIL + 1))
        fi
    else
        echo "SKIP: $name (no .expected or .error file found)"
        TOTAL=$((TOTAL - 1))
    fi
done

echo "================================================================"
echo "Summary: $PASS/$TOTAL passed"


read -p "Press [Enter] key to exit..."

if [ "$FAIL" -gt 0 ]; then
    exit 1
fi
exit 0
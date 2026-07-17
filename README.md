# AUJava to C Transpiler / Compiler

A robust, enterprise-grade **3-Pass Transpiler** built in Java using **ANTLR4** and **Maven** that compiles **AUJava** (a strict object-oriented subset of Java) into optimized, clean, and executable **C source code**.

This project covers the full compilation pipeline, including Lexical/Syntax analysis, strict Static Semantic Type-checking, Variable Shadowing, Lexical Scoping, and advanced Object-Oriented features shied into standard C memory layouts.

---

## Compiler Architecture (3-Pass Pipeline)

To handle forward references, deep inheritance graph validation, and linear Three-Address Code (TAC) generation, the compiler architecture is strictly decoupled into three distinct passes over the Abstract Syntax Tree (AST):

    Source Code (.aujava) 
           │
           ▼
     [ ANTLR4 Lexer & Parser ] ────> Syntax Error Interception
           │
           ▼
     [ Pass 1: ClassCollectorVisitor ]
           ├── Registers all class definitions & structural bounds
           └── Validates the single entry-point ('public static void main') constraint
           │
           ▼
     [ Pass 2: MemberCollectorVisitor ]
           ├── Resolves inheritance pointer linkages
           ├── Executes graph cycle detection (Cyclic Inheritance prevention)
           ├── Collects instance/static field and method signatures
           └── Enforces the static-method override safety clause
           │
           ▼
     [ Pass 3: CodeGeneratorVisitor ]
           ├── Performs vertical Contextual Scoping & Type checking
           ├── Flattens mathematical/logical expressions into Three-Address Code (TAC)
           ├── Emits virtual method dispatch tables (VTables) inside C structs
           └── Generates final C code mapped to standard output (`printf`)

---

## Key Features Implemented

* **Syntax & Error Tracking:** Exact line and character position mapping for both syntactic errors and complex semantic type clashes.
* **Three-Address Code (TAC):** Linearizes deep mathematical priorities and boolean matrices using dynamically managed temporal addresses (`_t_X`).
* **Object-Oriented C Engine (Bonus Feature - 50 pts):** Implements dynamic dispatch (polymorphism) via custom-built runtime virtual tables (VTables) inside sequential struct layouts, resolving constructor allocations securely via explicit `malloc` layouts.
* **Static Context Isolation (Bonus Feature - 25 pts):** Fully decouples global static fields and classes from instanced scopes, safeguarding illegal modifications across parent chains.
* **Strict Scoping Control:** Prevents runtime leaking via parent-linked lexical environments, implementing exact variable shadowing and boundary checking for loop-restricted keywords (`break`, `continue`).

---

## Getting Started

### Prerequisites
Make sure you have the following installed on your machine:
* **JDK 21** or higher
* **Apache Maven 3.x**
* **GCC Compiler** (MinGW for Windows or native GCC for Linux/macOS)

### 1. Build & Package the Project
To compile the source code, trigger the ANTLR4 parser generator, and package the transpiler into an executable fat JAR, execute:

    mvn clean package

This builds the target binary with dependencies at:
`target/AUJava-1.0-SNAPSHOT-jar-with-dependencies.jar`

### 2. Transpile a Single File
To convert an `AUJava` source file into a `C` file, feed the source file as the first argument to the compiler target:

    java -jar target/AUJava-1.0-SNAPSHOT-jar-with-dependencies.jar path/to/input.aujava output.c

### 3. Compile the Generated C Output
Compile the resulting output with any standard C compiler:

    gcc output.c -o program
    ./program

---

## Automated Testing Framework

The repository is equipped with an advanced testing matrix containing **27 test cases** checking both runtime correctness (positive integration tests) and boundary error handling (negative semantic tests).

To launch the automated verification suite, run the test runner shell script in Git Bash:

    ./tests/run_tests.sh

### Example Output:

    Using compiler jar: /target/AUJava-1.0-SNAPSHOT-jar-with-dependencies.jar
    ================================================================
    PASS: arithmetic
    PASS: boolean_ops
    PASS: error_cyclic_inheritance (compiler correctly rejected)
    PASS: error_type_mismatch (compiler correctly rejected)
    PASS: inheritance_override
    PASS: while_break_continue
    ================================================================
    Summary: 27/27 passed
    Press [Enter] key to exit...

---

## 📁 Project Structure

    .
    ├── pom.xml                        # Maven dependency and plugins setup
    ├── src/
    │   ├── main/
    │   │   ├── antlr4/                # Custom CFG Grammar rules (.g4)
    │   │   └── java/org/example/      # Transpiler Core Engine, Passes and Drivers
    │   └── test/                      # Java unit testing logic
    └── tests/                         # Production test suites (.aujava, .expected, .error)
        └── run_tests.sh               # Shell script automation test runner
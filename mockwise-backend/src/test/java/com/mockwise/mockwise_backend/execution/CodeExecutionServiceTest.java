package com.mockwise.mockwise_backend.execution;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CodeExecutionServiceTest {

    private CodeExecutionService service;

    @BeforeEach
    void setUp() {
        List<LanguageExecutor> executors = List.of(
                new JavaLanguageExecutor(),
                new PythonLanguageExecutor(),
                new CppLanguageExecutor()
        );
        service = new LocalCodeExecutionService(executors);
    }

    // ======================================================================
    // 1. Successful compilation and execution for each language
    // ======================================================================
    @Nested
    @DisplayName("Successful Execution")
    class SuccessfulExecution {

        @Test
        @DisplayName("Java: simple program compiles and runs")
        void javaHelloWorld() {
            String code = """
                    public class Main {
                        public static void main(String[] args) {
                            System.out.println("Hello from Java");
                        }
                    }
                    """;
            ExecutionResult result = service.execute(code, SupportedLanguage.JAVA);

            assertEquals(ExecutionStatus.SUCCESS, result.getStatus());
            assertTrue(result.getStdout().contains("Hello from Java"));
            assertEquals(0, result.getExitCode());
            assertTrue(result.getExecutionTimeMs() > 0);
        }

        @Test
        @DisplayName("Python: simple program runs")
        void pythonHelloWorld() {
            String code = """
                    print("Hello from Python")
                    """;
            ExecutionResult result = service.execute(code, SupportedLanguage.PYTHON);

            assertEquals(ExecutionStatus.SUCCESS, result.getStatus());
            assertTrue(result.getStdout().contains("Hello from Python"));
            assertEquals(0, result.getExitCode());
            assertTrue(result.getExecutionTimeMs() > 0);
        }

        @Test
        @DisplayName("C++: simple program compiles and runs")
        void cppHelloWorld() {
            String code = """
                    #include <iostream>
                    int main() {
                        std::cout << "Hello from C++" << std::endl;
                        return 0;
                    }
                    """;
            ExecutionResult result = service.execute(code, SupportedLanguage.CPP);

            assertEquals(ExecutionStatus.SUCCESS, result.getStatus());
            assertTrue(result.getStdout().contains("Hello from C++"));
            assertEquals(0, result.getExitCode());
            assertTrue(result.getExecutionTimeMs() > 0);
        }

        @Test
        @DisplayName("Java: program with computation and multiple outputs")
        void javaWithComputation() {
            String code = """
                    public class Main {
                        public static void main(String[] args) {
                            int sum = 0;
                            for (int i = 1; i <= 100; i++) {
                                sum += i;
                            }
                            System.out.println("Sum: " + sum);
                        }
                    }
                    """;
            ExecutionResult result = service.execute(code, SupportedLanguage.JAVA);

            assertEquals(ExecutionStatus.SUCCESS, result.getStatus());
            assertTrue(result.getStdout().contains("Sum: 5050"));
        }

        @Test
        @DisplayName("Python: program with computation")
        void pythonWithComputation() {
            String code = """
                    result = sum(range(1, 101))
                    print(f"Sum: {result}")
                    """;
            ExecutionResult result = service.execute(code, SupportedLanguage.PYTHON);

            assertEquals(ExecutionStatus.SUCCESS, result.getStatus());
            assertTrue(result.getStdout().contains("Sum: 5050"));
        }

        @Test
        @DisplayName("C++: program with computation")
        void cppWithComputation() {
            String code = """
                    #include <iostream>
                    int main() {
                        int sum = 0;
                        for (int i = 1; i <= 100; i++) sum += i;
                        std::cout << "Sum: " << sum << std::endl;
                        return 0;
                    }
                    """;
            ExecutionResult result = service.execute(code, SupportedLanguage.CPP);

            assertEquals(ExecutionStatus.SUCCESS, result.getStatus());
            assertTrue(result.getStdout().contains("Sum: 5050"));
        }
    }

    // ======================================================================
    // 2. Timeout — infinite loop stops after 5 seconds
    // ======================================================================
    @Nested
    @DisplayName("Timeout Enforcement")
    class TimeoutEnforcement {

        @Test
        @DisplayName("Java: infinite loop times out")
        void javaInfiniteLoop() {
            String code = """
                    public class Main {
                        public static void main(String[] args) {
                            while (true) { }
                        }
                    }
                    """;
            ExecutionResult result = service.execute(code, SupportedLanguage.JAVA);

            assertEquals(ExecutionStatus.TIMEOUT, result.getStatus());
            assertNotNull(result.getErrorMessage());
            assertTrue(result.getErrorMessage().contains("timed out"));
            // Should complete within ~6s (5s timeout + overhead)
            assertTrue(result.getExecutionTimeMs() >= 4000,
                    "Should run for at least 4 seconds before timeout");
        }

        @Test
        @DisplayName("Python: infinite loop times out")
        void pythonInfiniteLoop() {
            String code = """
                    while True:
                        pass
                    """;
            ExecutionResult result = service.execute(code, SupportedLanguage.PYTHON);

            assertEquals(ExecutionStatus.TIMEOUT, result.getStatus());
            assertNotNull(result.getErrorMessage());
            assertTrue(result.getErrorMessage().contains("timed out"));
        }

        @Test
        @DisplayName("C++: infinite loop times out")
        void cppInfiniteLoop() {
            String code = """
                    int main() {
                        while (true) { }
                        return 0;
                    }
                    """;
            ExecutionResult result = service.execute(code, SupportedLanguage.CPP);

            assertEquals(ExecutionStatus.TIMEOUT, result.getStatus());
            assertNotNull(result.getErrorMessage());
            assertTrue(result.getErrorMessage().contains("timed out"));
        }

        @Test
        @DisplayName("Fast code finishes well within timeout")
        void fastCodeDoesNotTimeout() {
            String code = """
                    public class Main {
                        public static void main(String[] args) {
                            System.out.println("Done");
                        }
                    }
                    """;
            ExecutionResult result = service.execute(code, SupportedLanguage.JAVA);

            assertEquals(ExecutionStatus.SUCCESS, result.getStatus());
            assertTrue(result.getExecutionTimeMs() < 5000,
                    "Fast program should finish well before the 5-second limit");
        }
    }

    // ======================================================================
    // 3. Memory limit — cannot exceed 256 MB, resources freed afterwards
    // ======================================================================
    @Nested
    @DisplayName("Memory Limit Enforcement")
    class MemoryLimitEnforcement {

        @Test
        @DisplayName("Java: large heap allocation exceeds memory limit")
        void javaMemoryExceeded() {
            String code = """
                    public class Main {
                        public static void main(String[] args) {
                            byte[] arr = new byte[512 * 1024 * 1024];
                            System.out.println("Allocated " + arr.length + " bytes");
                        }
                    }
                    """;
            ExecutionResult result = service.execute(code, SupportedLanguage.JAVA);

            assertTrue(
                    result.getStatus() == ExecutionStatus.MEMORY_LIMIT_EXCEEDED
                    || result.getStatus() == ExecutionStatus.RUNTIME_ERROR,
                    "Should fail with memory limit exceeded or runtime error, got: " + result.getStatus()
            );
        }

        @Test
        @DisplayName("Python: large allocation exceeds memory limit")
        void pythonMemoryExceeded() {
            String code = """
                    a = bytearray(512 * 1024 * 1024)
                    print(len(a))
                    """;
            ExecutionResult result = service.execute(code, SupportedLanguage.PYTHON);

            assertTrue(
                    result.getStatus() == ExecutionStatus.MEMORY_LIMIT_EXCEEDED
                    || result.getStatus() == ExecutionStatus.RUNTIME_ERROR,
                    "Should fail with memory limit exceeded or runtime error, got: " + result.getStatus()
            );
        }

        @Test
        @DisplayName("C++: large allocation exceeds memory limit")
        void cppMemoryExceeded() {
            String code = """
                    #include <vector>
                    int main() {
                        std::vector<char> v(512 * 1024 * 1024, 'A');
                        return 0;
                    }
                    """;
            ExecutionResult result = service.execute(code, SupportedLanguage.CPP);

            assertTrue(
                    result.getStatus() == ExecutionStatus.MEMORY_LIMIT_EXCEEDED
                    || result.getStatus() == ExecutionStatus.RUNTIME_ERROR,
                    "Should fail with memory limit exceeded or runtime error, got: " + result.getStatus()
            );
        }

        @Test
        @DisplayName("Memory is freed after failed execution — subsequent run succeeds")
        void memoryFreedAfterExecution() {
            // Run a program that exceeds memory
            String memHog = """
                    public class Main {
                        public static void main(String[] args) {
                            byte[] arr = new byte[512 * 1024 * 1024];
                        }
                    }
                    """;
            ExecutionResult failedResult = service.execute(memHog, SupportedLanguage.JAVA);
            assertTrue(
                    failedResult.getStatus() == ExecutionStatus.MEMORY_LIMIT_EXCEEDED
                    || failedResult.getStatus() == ExecutionStatus.RUNTIME_ERROR
            );

            // Now run a normal program — should succeed, proving resources were freed
            String normal = """
                    public class Main {
                        public static void main(String[] args) {
                            System.out.println("Still working after memory failure");
                        }
                    }
                    """;
            ExecutionResult okResult = service.execute(normal, SupportedLanguage.JAVA);

            assertEquals(ExecutionStatus.SUCCESS, okResult.getStatus());
            assertTrue(okResult.getStdout().contains("Still working after memory failure"));
        }
    }

    // ======================================================================
    // 4. Security — file access, network, system exec all blocked
    // ======================================================================
    @Nested
    @DisplayName("Security — File Access Blocked")
    class FileAccessBlocked {

        @Test
        @DisplayName("Java: File creation blocked")
        void javaFileAccess() {
            String code = """
                    import java.io.File;
                    public class Main {
                        public static void main(String[] args) {
                            File f = new File("/tmp/hack.txt");
                        }
                    }
                    """;
            ExecutionResult result = service.execute(code, SupportedLanguage.JAVA);

            assertEquals(ExecutionStatus.SECURITY_VIOLATION, result.getStatus());
            assertNotNull(result.getErrorMessage());
            assertTrue(result.getErrorMessage().toLowerCase().contains("file"));
        }

        @Test
        @DisplayName("Java: FileWriter blocked")
        void javaFileWriter() {
            String code = """
                    import java.io.FileWriter;
                    public class Main {
                        public static void main(String[] args) throws Exception {
                            FileWriter fw = new FileWriter("/tmp/hack.txt");
                            fw.write("hacked");
                            fw.close();
                        }
                    }
                    """;
            ExecutionResult result = service.execute(code, SupportedLanguage.JAVA);

            assertEquals(ExecutionStatus.SECURITY_VIOLATION, result.getStatus());
        }

        @Test
        @DisplayName("Python: open() blocked")
        void pythonFileAccess() {
            String code = """
                    f = open("/tmp/hack.txt", "w")
                    f.write("hacked")
                    f.close()
                    """;
            ExecutionResult result = service.execute(code, SupportedLanguage.PYTHON);

            assertEquals(ExecutionStatus.SECURITY_VIOLATION, result.getStatus());
            assertNotNull(result.getErrorMessage());
        }

        @Test
        @DisplayName("C++: fopen blocked")
        void cppFileAccess() {
            String code = """
                    #include <cstdio>
                    int main() {
                        FILE* f = fopen("/tmp/hack.txt", "w");
                        return 0;
                    }
                    """;
            ExecutionResult result = service.execute(code, SupportedLanguage.CPP);

            assertEquals(ExecutionStatus.SECURITY_VIOLATION, result.getStatus());
        }

        @Test
        @DisplayName("C++: fstream include blocked")
        void cppFstreamBlocked() {
            String code = """
                    #include <fstream>
                    int main() {
                        std::ofstream f("hack.txt");
                        f << "hacked";
                        return 0;
                    }
                    """;
            ExecutionResult result = service.execute(code, SupportedLanguage.CPP);

            assertEquals(ExecutionStatus.SECURITY_VIOLATION, result.getStatus());
        }
    }

    @Nested
    @DisplayName("Security — Network Access Blocked")
    class NetworkAccessBlocked {

        @Test
        @DisplayName("Java: Socket blocked")
        void javaSocket() {
            String code = """
                    import java.net.Socket;
                    public class Main {
                        public static void main(String[] args) throws Exception {
                            Socket s = new Socket("google.com", 80);
                        }
                    }
                    """;
            ExecutionResult result = service.execute(code, SupportedLanguage.JAVA);

            assertEquals(ExecutionStatus.SECURITY_VIOLATION, result.getStatus());
            assertTrue(result.getErrorMessage().toLowerCase().contains("network"));
        }

        @Test
        @DisplayName("Java: HttpURLConnection blocked")
        void javaHttp() {
            String code = """
                    import java.net.HttpURLConnection;
                    import java.net.URL;
                    public class Main {
                        public static void main(String[] args) throws Exception {
                            HttpURLConnection conn = (HttpURLConnection) new URL("http://google.com").openConnection();
                        }
                    }
                    """;
            ExecutionResult result = service.execute(code, SupportedLanguage.JAVA);

            assertEquals(ExecutionStatus.SECURITY_VIOLATION, result.getStatus());
        }

        @Test
        @DisplayName("Python: socket import blocked")
        void pythonSocket() {
            String code = """
                    import socket
                    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                    s.connect(("google.com", 80))
                    """;
            ExecutionResult result = service.execute(code, SupportedLanguage.PYTHON);

            assertEquals(ExecutionStatus.SECURITY_VIOLATION, result.getStatus());
        }

        @Test
        @DisplayName("Python: urllib blocked")
        void pythonUrllib() {
            String code = """
                    import urllib.request
                    response = urllib.request.urlopen("http://google.com")
                    """;
            ExecutionResult result = service.execute(code, SupportedLanguage.PYTHON);

            assertEquals(ExecutionStatus.SECURITY_VIOLATION, result.getStatus());
        }

        @Test
        @DisplayName("Python: requests blocked")
        void pythonRequests() {
            String code = """
                    import requests
                    r = requests.get("http://google.com")
                    """;
            ExecutionResult result = service.execute(code, SupportedLanguage.PYTHON);

            assertEquals(ExecutionStatus.SECURITY_VIOLATION, result.getStatus());
        }

        @Test
        @DisplayName("C++: socket header blocked")
        void cppSocket() {
            String code = """
                    #include <sys/socket.h>
                    int main() {
                        int fd = socket(AF_INET, SOCK_STREAM, 0);
                        return 0;
                    }
                    """;
            ExecutionResult result = service.execute(code, SupportedLanguage.CPP);

            assertEquals(ExecutionStatus.SECURITY_VIOLATION, result.getStatus());
        }

        @Test
        @DisplayName("C++: socket() call blocked")
        void cppSocketCall() {
            String code = """
                    #include <iostream>
                    int main() {
                        int fd = socket(2, 1, 0);
                        return 0;
                    }
                    """;
            ExecutionResult result = service.execute(code, SupportedLanguage.CPP);

            assertEquals(ExecutionStatus.SECURITY_VIOLATION, result.getStatus());
        }
    }

    @Nested
    @DisplayName("Security — System Command Execution Blocked")
    class SystemExecBlocked {

        @Test
        @DisplayName("Java: Runtime.exec blocked")
        void javaRuntimeExec() {
            String code = """
                    public class Main {
                        public static void main(String[] args) throws Exception {
                            Runtime.getRuntime().exec("ls");
                        }
                    }
                    """;
            ExecutionResult result = service.execute(code, SupportedLanguage.JAVA);

            assertEquals(ExecutionStatus.SECURITY_VIOLATION, result.getStatus());
        }

        @Test
        @DisplayName("Java: ProcessBuilder blocked")
        void javaProcessBuilder() {
            String code = """
                    public class Main {
                        public static void main(String[] args) throws Exception {
                            new ProcessBuilder("ls").start();
                        }
                    }
                    """;
            ExecutionResult result = service.execute(code, SupportedLanguage.JAVA);

            assertEquals(ExecutionStatus.SECURITY_VIOLATION, result.getStatus());
        }

        @Test
        @DisplayName("Java: System.exit blocked")
        void javaSystemExit() {
            String code = """
                    public class Main {
                        public static void main(String[] args) {
                            System.exit(0);
                        }
                    }
                    """;
            ExecutionResult result = service.execute(code, SupportedLanguage.JAVA);

            assertEquals(ExecutionStatus.SECURITY_VIOLATION, result.getStatus());
        }

        @Test
        @DisplayName("Python: os module blocked")
        void pythonOs() {
            String code = """
                    import os
                    os.system("ls")
                    """;
            ExecutionResult result = service.execute(code, SupportedLanguage.PYTHON);

            assertEquals(ExecutionStatus.SECURITY_VIOLATION, result.getStatus());
        }

        @Test
        @DisplayName("Python: subprocess blocked")
        void pythonSubprocess() {
            String code = """
                    import subprocess
                    subprocess.run(["ls"])
                    """;
            ExecutionResult result = service.execute(code, SupportedLanguage.PYTHON);

            assertEquals(ExecutionStatus.SECURITY_VIOLATION, result.getStatus());
        }

        @Test
        @DisplayName("Python: exec() blocked")
        void pythonExec() {
            String code = """
                    exec("print('hacked')")
                    """;
            ExecutionResult result = service.execute(code, SupportedLanguage.PYTHON);

            assertEquals(ExecutionStatus.SECURITY_VIOLATION, result.getStatus());
        }

        @Test
        @DisplayName("Python: eval() blocked")
        void pythonEval() {
            String code = """
                    result = eval("1+1")
                    print(result)
                    """;
            ExecutionResult result = service.execute(code, SupportedLanguage.PYTHON);

            assertEquals(ExecutionStatus.SECURITY_VIOLATION, result.getStatus());
        }

        @Test
        @DisplayName("Python: __import__ blocked")
        void pythonDunderImport() {
            String code = """
                    os = __import__("os")
                    os.system("ls")
                    """;
            ExecutionResult result = service.execute(code, SupportedLanguage.PYTHON);

            assertEquals(ExecutionStatus.SECURITY_VIOLATION, result.getStatus());
        }

        @Test
        @DisplayName("C++: system() blocked")
        void cppSystem() {
            String code = """
                    #include <cstdlib>
                    int main() {
                        system("ls");
                        return 0;
                    }
                    """;
            ExecutionResult result = service.execute(code, SupportedLanguage.CPP);

            assertEquals(ExecutionStatus.SECURITY_VIOLATION, result.getStatus());
        }

        @Test
        @DisplayName("C++: popen() blocked")
        void cppPopen() {
            String code = """
                    #include <cstdio>
                    int main() {
                        FILE* p = popen("ls", "r");
                        return 0;
                    }
                    """;
            ExecutionResult result = service.execute(code, SupportedLanguage.CPP);

            assertEquals(ExecutionStatus.SECURITY_VIOLATION, result.getStatus());
        }

        @Test
        @DisplayName("C++: fork() blocked")
        void cppFork() {
            String code = """
                    #include <unistd.h>
                    int main() {
                        fork();
                        return 0;
                    }
                    """;
            ExecutionResult result = service.execute(code, SupportedLanguage.CPP);

            assertEquals(ExecutionStatus.SECURITY_VIOLATION, result.getStatus());
        }
    }

    @Nested
    @DisplayName("Security — Additional Attack Vectors")
    class AdditionalSecurityTests {

        @Test
        @DisplayName("Java: Reflection blocked")
        void javaReflection() {
            String code = """
                    import java.lang.reflect.Method;
                    public class Main {
                        public static void main(String[] args) throws Exception {
                            Method m = Runtime.class.getMethod("exec", String.class);
                        }
                    }
                    """;
            ExecutionResult result = service.execute(code, SupportedLanguage.JAVA);

            assertEquals(ExecutionStatus.SECURITY_VIOLATION, result.getStatus());
        }

        @Test
        @DisplayName("Java: Class.forName blocked")
        void javaClassForName() {
            String code = """
                    public class Main {
                        public static void main(String[] args) throws Exception {
                            Class.forName("java.lang.Runtime");
                        }
                    }
                    """;
            ExecutionResult result = service.execute(code, SupportedLanguage.JAVA);

            assertEquals(ExecutionStatus.SECURITY_VIOLATION, result.getStatus());
        }

        @Test
        @DisplayName("Java: native library loading blocked")
        void javaNativeLoading() {
            String code = """
                    public class Main {
                        public static void main(String[] args) {
                            System.loadLibrary("evil");
                        }
                    }
                    """;
            ExecutionResult result = service.execute(code, SupportedLanguage.JAVA);

            assertEquals(ExecutionStatus.SECURITY_VIOLATION, result.getStatus());
        }

        @Test
        @DisplayName("Python: ctypes blocked")
        void pythonCtypes() {
            String code = """
                    import ctypes
                    libc = ctypes.CDLL("libc.so.6")
                    """;
            ExecutionResult result = service.execute(code, SupportedLanguage.PYTHON);

            assertEquals(ExecutionStatus.SECURITY_VIOLATION, result.getStatus());
        }

        @Test
        @DisplayName("Python: sys module blocked")
        void pythonSys() {
            String code = """
                    import sys
                    sys.exit(0)
                    """;
            ExecutionResult result = service.execute(code, SupportedLanguage.PYTHON);

            assertEquals(ExecutionStatus.SECURITY_VIOLATION, result.getStatus());
        }

        @Test
        @DisplayName("Python: multiprocessing blocked")
        void pythonMultiprocessing() {
            String code = """
                    import multiprocessing
                    p = multiprocessing.Process(target=lambda: None)
                    p.start()
                    """;
            ExecutionResult result = service.execute(code, SupportedLanguage.PYTHON);

            assertEquals(ExecutionStatus.SECURITY_VIOLATION, result.getStatus());
        }

        @Test
        @DisplayName("Python: shutil blocked")
        void pythonShutil() {
            String code = """
                    import shutil
                    shutil.rmtree("/tmp")
                    """;
            ExecutionResult result = service.execute(code, SupportedLanguage.PYTHON);

            assertEquals(ExecutionStatus.SECURITY_VIOLATION, result.getStatus());
        }

        @Test
        @DisplayName("C++: filesystem header blocked")
        void cppFilesystem() {
            String code = """
                    #include <filesystem>
                    int main() {
                        std::filesystem::remove_all("/tmp");
                        return 0;
                    }
                    """;
            ExecutionResult result = service.execute(code, SupportedLanguage.CPP);

            assertEquals(ExecutionStatus.SECURITY_VIOLATION, result.getStatus());
        }

        @Test
        @DisplayName("C++: exec family blocked")
        void cppExecFamily() {
            String code = """
                    #include <unistd.h>
                    int main() {
                        execl("/bin/sh", "sh", "-c", "ls", (char*)0);
                        return 0;
                    }
                    """;
            ExecutionResult result = service.execute(code, SupportedLanguage.CPP);

            assertEquals(ExecutionStatus.SECURITY_VIOLATION, result.getStatus());
        }
    }

    // ======================================================================
    // 5. Compilation errors returned properly
    // ======================================================================
    @Nested
    @DisplayName("Compilation Errors")
    class CompilationErrors {

        @Test
        @DisplayName("Java: syntax error returns COMPILATION_ERROR")
        void javaSyntaxError() {
            String code = """
                    public class Main {
                        public static void main(String[] args) {
                            System.out.println("missing semicolon")
                        }
                    }
                    """;
            ExecutionResult result = service.execute(code, SupportedLanguage.JAVA);

            assertEquals(ExecutionStatus.COMPILATION_ERROR, result.getStatus());
            assertNotNull(result.getErrorMessage());
            assertNotEquals("", result.getErrorMessage());
        }

        @Test
        @DisplayName("Python: syntax error returns COMPILATION_ERROR")
        void pythonSyntaxError() {
            String code = """
                    def foo(
                        print("missing closing paren")
                    """;
            ExecutionResult result = service.execute(code, SupportedLanguage.PYTHON);

            assertEquals(ExecutionStatus.COMPILATION_ERROR, result.getStatus());
            assertNotNull(result.getErrorMessage());
        }

        @Test
        @DisplayName("C++: syntax error returns COMPILATION_ERROR")
        void cppSyntaxError() {
            String code = """
                    #include <iostream>
                    int main() {
                        std::cout << "missing semicolon"
                        return 0;
                    }
                    """;
            ExecutionResult result = service.execute(code, SupportedLanguage.CPP);

            assertEquals(ExecutionStatus.COMPILATION_ERROR, result.getStatus());
            assertNotNull(result.getErrorMessage());
        }

        @Test
        @DisplayName("Code with compilation error is not executed")
        void compilationErrorPreventsExecution() {
            // This code has a syntax error but would also do something
            // dangerous if it ran — compilation error should prevent execution
            String code = """
                    public class Main {
                        public static void main(String[] args) {
                            int x = // missing expression
                        }
                    }
                    """;
            ExecutionResult result = service.execute(code, SupportedLanguage.JAVA);

            assertEquals(ExecutionStatus.COMPILATION_ERROR, result.getStatus());
        }
    }

    // ======================================================================
    // 6. Runtime errors
    // ======================================================================
    @Nested
    @DisplayName("Runtime Errors")
    class RuntimeErrors {

        @Test
        @DisplayName("Java: uncaught exception produces RUNTIME_ERROR")
        void javaRuntimeError() {
            String code = """
                    public class Main {
                        public static void main(String[] args) {
                            int[] arr = new int[5];
                            System.out.println(arr[10]);
                        }
                    }
                    """;
            ExecutionResult result = service.execute(code, SupportedLanguage.JAVA);

            assertEquals(ExecutionStatus.RUNTIME_ERROR, result.getStatus());
            assertNotEquals(0, result.getExitCode());
        }

        @Test
        @DisplayName("Python: uncaught exception produces RUNTIME_ERROR")
        void pythonRuntimeError() {
            String code = """
                    x = 1 / 0
                    """;
            ExecutionResult result = service.execute(code, SupportedLanguage.PYTHON);

            assertEquals(ExecutionStatus.RUNTIME_ERROR, result.getStatus());
            assertNotEquals(0, result.getExitCode());
        }

        @Test
        @DisplayName("C++: segfault-style error produces error status")
        void cppRuntimeError() {
            String code = """
                    #include <iostream>
                    #include <stdexcept>
                    int main() {
                        throw std::runtime_error("intentional crash");
                        return 0;
                    }
                    """;
            ExecutionResult result = service.execute(code, SupportedLanguage.CPP);

            assertNotEquals(ExecutionStatus.SUCCESS, result.getStatus());
            assertNotEquals(0, result.getExitCode());
        }
    }

    // ======================================================================
    // 7. Edge cases
    // ======================================================================
    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Java: custom public class name is handled correctly")
        void javaCustomClassName() {
            String code = """
                    public class Solution {
                        public static void main(String[] args) {
                            System.out.println("Custom class name works");
                        }
                    }
                    """;
            ExecutionResult result = service.execute(code, SupportedLanguage.JAVA);

            assertEquals(ExecutionStatus.SUCCESS, result.getStatus());
            assertTrue(result.getStdout().contains("Custom class name works"));
        }

        @Test
        @DisplayName("C++: bits/stdc++.h is handled transparently")
        void cppBitsStdcHeader() {
            String code = """
                    #include <bits/stdc++.h>
                    using namespace std;
                    int main() {
                        vector<int> v = {3, 1, 2};
                        sort(v.begin(), v.end());
                        for (int x : v) cout << x << " ";
                        cout << endl;
                        return 0;
                    }
                    """;
            ExecutionResult result = service.execute(code, SupportedLanguage.CPP);

            assertEquals(ExecutionStatus.SUCCESS, result.getStatus());
            assertTrue(result.getStdout().contains("1 2 3"));
        }

        @Test
        @DisplayName("Stderr output is captured alongside stdout")
        void stderrCaptured() {
            String code = """
                    import java.util.logging.Logger;
                    public class Main {
                        public static void main(String[] args) {
                            System.out.println("stdout line");
                            System.err.println("stderr line");
                        }
                    }
                    """;
            ExecutionResult result = service.execute(code, SupportedLanguage.JAVA);

            assertEquals(ExecutionStatus.SUCCESS, result.getStatus());
            assertTrue(result.getStdout().contains("stdout line"));
            assertTrue(result.getStderr().contains("stderr line"));
        }
    }
}
package com.mockwise.backend.codesyntax;

import com.mockwise.backend.codesyntax.languages.CSharpToolchain;
import com.mockwise.backend.codesyntax.languages.CppToolchain;
import com.mockwise.backend.codesyntax.languages.GoToolchain;
import com.mockwise.backend.codesyntax.languages.JavaScriptToolchain;
import com.mockwise.backend.codesyntax.languages.JavaToolchain;
import com.mockwise.backend.codesyntax.languages.PythonToolchain;
import com.mockwise.backend.codesyntax.languages.RubyToolchain;
import com.mockwise.backend.codesyntax.languages.RustToolchain;
import com.mockwise.backend.codesyntax.languages.ScalaToolchain;
import com.mockwise.backend.codesyntax.languages.TypeScriptToolchain;
import com.mockwise.backend.codesyntax.support.DefaultProcessRunner;

import java.util.List;

/** Manual wiring for unit tests (no Spring context). */
public final class ToolchainTestFactory {

    private ToolchainTestFactory() {}

    public static LanguageToolchainRegistry registry() {
        DefaultProcessRunner runner = new DefaultProcessRunner();
        return new LanguageToolchainRegistry(List.of(
                new JavaToolchain(),
                new PythonToolchain(runner),
                new CppToolchain(runner),
                new JavaScriptToolchain(runner),
                new TypeScriptToolchain(runner),
                new GoToolchain(runner),
                new RustToolchain(runner),
                new RubyToolchain(runner),
                new ScalaToolchain(runner),
                new CSharpToolchain(runner)
        ));
    }

    public static SyntaxCheckFacade facade() {
        return new SyntaxCheckFacade(registry());
    }

    public static SyntaxCheckService service() {
        return new SyntaxCheckService(facade());
    }

    public static LanguageSupportService languageSupportService() {
        return new LanguageSupportService(registry());
    }
}

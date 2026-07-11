package com.mockwise.backend.codesyntax;

import com.mockwise.backend.codesyntax.model.SupportedLanguage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LanguageSupportServiceTest {

    @Test
    void listsSortedLanguagesWithAliases() {
        LanguageSupportService service = ToolchainTestFactory.languageSupportService();
        List<SupportedLanguage> langs = service.listSupportedLanguages();
        assertEquals(10, langs.size());
        // sorted by id
        assertEquals("cpp", langs.get(0).id());
        SupportedLanguage js = langs.stream().filter(l -> l.id().equals("javascript")).findFirst().orElseThrow();
        assertTrue(js.aliases().contains("js"));
    }
}

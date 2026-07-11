package com.mockwise.backend.codesyntax;

import com.mockwise.backend.codesyntax.model.SupportedLanguage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LanguageSupportService {

    private final LanguageToolchainRegistry registry;

    public List<SupportedLanguage> listSupportedLanguages() {
        return registry.all().stream()
                .map(t -> new SupportedLanguage(
                        t.languageId(),
                        t.displayName(),
                        t.aliases().stream().sorted().toList(),
                        t.isToolchainAvailable()))
                .sorted(Comparator.comparing(SupportedLanguage::id))
                .toList();
    }
}

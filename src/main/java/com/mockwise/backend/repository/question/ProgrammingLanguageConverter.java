package com.mockwise.backend.repository.question;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

/**
 * Persists {@link ProgrammingLanguage#id()} and accepts the same spellings on request parameters.
 */
@Component
@Converter(autoApply = true)
public class ProgrammingLanguageConverter
        implements AttributeConverter<ProgrammingLanguage, String>,
        org.springframework.core.convert.converter.Converter<String, ProgrammingLanguage> {

    @Override
    public ProgrammingLanguage convert(String source) {
        return ProgrammingLanguage.parse(source);
    }

    @Override
    public String convertToDatabaseColumn(ProgrammingLanguage language) {
        return language == null ? null : language.id();
    }

    @Override
    public ProgrammingLanguage convertToEntityAttribute(String stored) {
        if (stored == null || stored.isBlank()) {
            return null;
        }
        return ProgrammingLanguage.parse(stored);
    }
}

package com.example.converter.transformer;

import java.util.Map;

/**
 * Конфигурация трансформатора {@link CanonicalConditionBuilder}.
 *
 * <p>Флаг {@link #isPreserveOriginalFieldNames()} включает строгий режим
 * обработки имён полей: значение {@code leftValuePath} из XML 1С DCS
 * (например, {@code ОбъектСтрахования.СтранаРегистрации},
 * {@code Контрагент.ЮрФизЛицо}) передаётся в канонический JSON без изменений —
 * сохраняются регистр и точки.</p>
 *
 * <p>По умолчанию ({@code false}) применяется текущая логика:
 * явный маппинг → общая snake_case-нормализация.</p>
 *
 * <p>Класс неизменяем ({@code Map} копируется при конструировании) —
 * безопасен для переиспользования одним билдером и для передачи из CLI-аргументов
 * или файла настроек в будущем.</p>
 */
public class TransformerConfig {

    /** Флаг строгого режима имён полей. */
    private final boolean preserveOriginalFieldNames;

    /** Явный маппинг 1С-имён полей → канонические имена (расширяемая точка). */
    private final Map<String, String> fieldMapping;

    /**
     * Конфигурация по умолчанию: строгий режим выключен, маппинг пуст.
     * Обратная совместимость с текущим поведением билдера.
     */
    public TransformerConfig() {
        this(false);
    }

    /**
     * Конфигурация только со строгим режимом имён полей (без маппинга).
     *
     * @param preserveOriginalFieldNames {@code true} — имена полей из XML
     *                                   передаются без изменений (регистр и точки)
     */
    public TransformerConfig(boolean preserveOriginalFieldNames) {
        this(preserveOriginalFieldNames, Map.of());
    }

    /**
     * Полный конструктор.
     *
     * @param preserveOriginalFieldNames {@code true} — имена полей из XML
     *                                   передаются без изменений (регистр и точки)
     * @param fieldMapping               соответствия «сырое имя 1С» →
     *                                   «каноническое имя»; {@code null} трактуется
     *                                   как пустой маппинг
     */
    public TransformerConfig(boolean preserveOriginalFieldNames, Map<String, String> fieldMapping) {
        this.preserveOriginalFieldNames = preserveOriginalFieldNames;
        this.fieldMapping = fieldMapping != null ? Map.copyOf(fieldMapping) : Map.of();
    }

    /**
     * @return {@code true}, если имена полей из XML должны попадать
     *         в канонический JSON без изменений (сохраняется регистр и точки)
     */
    public boolean isPreserveOriginalFieldNames() {
        return preserveOriginalFieldNames;
    }

    /**
     * @return неизменяемый маппинг «сырое имя 1С» → «каноническое имя»
     */
    public Map<String, String> getFieldMapping() {
        return fieldMapping;
    }
}
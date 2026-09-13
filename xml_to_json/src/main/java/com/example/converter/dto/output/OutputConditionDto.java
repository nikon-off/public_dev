package com.example.converter.dto.output;

import com.example.converter.dto.CanonicalConditionDto;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Выходная модель условия: повторяет структуру {@code ConditionDto},
 * но вместо сырой XML-строки {@code xmlFilter} содержит типизированное
 * каноническое представление {@link CanonicalConditionDto}.
 *
 * <p>Соответствует структуре:
 * <pre>
 * {
 *   "conditionId": "...",
 *   "conditionName": "...",
 *   "canonical": {
 *     "logic": "OR",
 *     "rules": [ ... ]
 *   }
 * }
 * </pre>
 *
 * <p>Для пустого или отсутствующего {@code xmlFilter} поле {@code canonical}
 * содержит пустую модель ({@code {"logic": null, "rules": []}}) — это штатная
 * ситуация, а не ошибка контракта.
 */
public class OutputConditionDto {

    @JsonProperty("conditionId")
    private String conditionId;

    @JsonProperty("conditionName")
    private String conditionName;

    /** Каноническое представление XML-фильтра (результат конвейера парсер → билдер). */
    @JsonProperty("canonical")
    private CanonicalConditionDto canonical;

    /** Конструктор по умолчанию (требуется Jackson для десериализации). */
    public OutputConditionDto() {
    }

    /**
     * Полный конструктор.
     *
     * @param conditionId   идентификатор условия (UUID как строка)
     * @param conditionName наименование условия
     * @param canonical     каноническая модель условия
     */
    public OutputConditionDto(String conditionId, String conditionName, CanonicalConditionDto canonical) {
        this.conditionId = conditionId;
        this.conditionName = conditionName;
        this.canonical = canonical;
    }

    public String getConditionId() {
        return conditionId;
    }

    public void setConditionId(String conditionId) {
        this.conditionId = conditionId;
    }

    public String getConditionName() {
        return conditionName;
    }

    public void setConditionName(String conditionName) {
        this.conditionName = conditionName;
    }

    public CanonicalConditionDto getCanonical() {
        return canonical;
    }

    public void setCanonical(CanonicalConditionDto canonical) {
        this.canonical = canonical;
    }
}
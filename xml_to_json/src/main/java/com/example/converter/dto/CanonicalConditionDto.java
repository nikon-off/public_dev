package com.example.converter.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Каноническая модель распарсенного условия: нормализованное представление
 * XML-фильтра 1С в виде дерева "логика + правила".
 *
 * <p>Соответствует структуре:
 * <pre>
 * {
 *   "logic": "OR",
 *   "rules": [ ... ]
 * }
 * </pre>
 *
 * <p>Пример значения {@code logic}: {@code "OR"}, {@code "AND"}.
 */
public class CanonicalConditionDto {

    @JsonProperty("logic")
    private String logic;

    @JsonProperty("rules")
    private List<RuleDto> rules = new ArrayList<>();

    /** Конструктор по умолчанию (требуется Jackson для десериализации). */
    public CanonicalConditionDto() {
    }

    /**
     * Полный конструктор.
     *
     * @param logic логическая связка правил ("OR", "AND" и т.п.)
     * @param rules список правил условия
     */
    public CanonicalConditionDto(String logic, List<RuleDto> rules) {
        this.logic = logic;
        this.rules = rules != null ? rules : new ArrayList<>();
    }

    public String getLogic() {
        return logic;
    }

    public void setLogic(String logic) {
        this.logic = logic;
    }

    public List<RuleDto> getRules() {
        return rules;
    }

    public void setRules(List<RuleDto> rules) {
        this.rules = rules != null ? rules : new ArrayList<>();
    }
}
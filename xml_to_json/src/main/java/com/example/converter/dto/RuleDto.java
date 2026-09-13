package com.example.converter.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Модель одиночного правила канонического условия.
 *
 * <p>Соответствует структуре:
 * <pre>
 * {
 *   "field": "Регион",
 *   "operator": "IN",
 *   "values": [ "Крым", "Севастополь" ]
 * }
 * </pre>
 *
 * <p>Поле {@code values} всегда представлено списком строк: для оператора
 * {@code IN} — несколько значений, для унарного оператора {@code EQ} — список
 * из одного элемента. Единый тип исключает дублирование модели (YAGNI);
 * при необходимости нормализации к скаляру это делается на уровне парсера.
 */
public class RuleDto {

    @JsonProperty("field")
    private String field;

    @JsonProperty("operator")
    private String operator;

    @JsonProperty("values")
    private List<String> values = new ArrayList<>();

    /** Конструктор по умолчанию (требуется Jackson для десериализации). */
    public RuleDto() {
    }

    /**
     * Полный конструктор.
     *
     * @param field    имя поля, к которому применяется правило (например, "Регион")
     * @param operator оператор сравнения ("IN", "EQ" и т.п.)
     * @param values   список значений для сравнения
     */
    public RuleDto(String field, String operator, List<String> values) {
        this.field = field;
        this.operator = operator;
        this.values = values != null ? values : new ArrayList<>();
    }

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public List<String> getValues() {
        return values;
    }

    public void setValues(List<String> values) {
        this.values = values != null ? values : new ArrayList<>();
    }
}
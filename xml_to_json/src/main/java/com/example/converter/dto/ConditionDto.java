package com.example.converter.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Модель условия: контейнер для сырой XML-строки настроек 1С (xmlFilter),
 * которая будет распарсена на следующем этапе конвейера.
 *
 * <p>Соответствует структуре:
 * <pre>
 * {
 *   "conditionId": "...",
 *   "conditionName": "...",
 *   "xmlFilter": "<Settings xmlns=\"http://v8.1c.ru/...\">...</Settings>"
 * }
 * </pre>
 */
public class ConditionDto {

    @JsonProperty("conditionId")
    private String conditionId;

    @JsonProperty("conditionName")
    private String conditionName;

    /** Сырая XML-строка настроек 1С (формат DCS), парсится позже через {@code XmlMapper}. */
    @JsonProperty("xmlFilter")
    private String xmlFilter;

    /** Конструктор по умолчанию (требуется Jackson для десериализации). */
    public ConditionDto() {
    }

    /**
     * Полный конструктор.
     *
     * @param conditionId   идентификатор условия (UUID как строка)
     * @param conditionName наименование условия
     * @param xmlFilter     сырая XML-строка фильтра 1С
     */
    public ConditionDto(String conditionId, String conditionName, String xmlFilter) {
        this.conditionId = conditionId;
        this.conditionName = conditionName;
        this.xmlFilter = xmlFilter;
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

    public String getXmlFilter() {
        return xmlFilter;
    }

    public void setXmlFilter(String xmlFilter) {
        this.xmlFilter = xmlFilter;
    }
}
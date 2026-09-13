package com.example.converter.dto.output;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Выходная модель группы условий: повторяет структуру {@code GroupDto},
 * но содержит список {@link OutputConditionDto} вместо {@code ConditionDto}.
 *
 * <p>Соответствует структуре:
 * <pre>
 * {
 *   "groupId": "...",
 *   "groupName": "...",
 *   "conditions": [ ... ]
 * }
 * </pre>
 */
public class OutputGroupDto {

    @JsonProperty("groupId")
    private String groupId;

    @JsonProperty("groupName")
    private String groupName;

    @JsonProperty("conditions")
    private List<OutputConditionDto> conditions = new ArrayList<>();

    /** Конструктор по умолчанию (требуется Jackson для десериализации). */
    public OutputGroupDto() {
    }

    /**
     * Полный конструктор.
     *
     * @param groupId    идентификатор группы (UUID как строка)
     * @param groupName  наименование группы
     * @param conditions список условий группы
     */
    public OutputGroupDto(String groupId, String groupName, List<OutputConditionDto> conditions) {
        this.groupId = groupId;
        this.groupName = groupName;
        this.conditions = conditions != null ? conditions : new ArrayList<>();
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public List<OutputConditionDto> getConditions() {
        return conditions;
    }

    public void setConditions(List<OutputConditionDto> conditions) {
        this.conditions = conditions != null ? conditions : new ArrayList<>();
    }
}
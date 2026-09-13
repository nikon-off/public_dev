package com.example.converter.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Модель группы условий внутри контракта.
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
public class GroupDto {

    @JsonProperty("groupId")
    private String groupId;

    @JsonProperty("groupName")
    private String groupName;

    @JsonProperty("conditions")
    private List<ConditionDto> conditions = new ArrayList<>();

    /** Конструктор по умолчанию (требуется Jackson для десериализации). */
    public GroupDto() {
    }

    /**
     * Полный конструктор.
     *
     * @param groupId    идентификатор группы (UUID как строка)
     * @param groupName  наименование группы
     * @param conditions список условий группы
     */
    public GroupDto(String groupId, String groupName, List<ConditionDto> conditions) {
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

    public List<ConditionDto> getConditions() {
        return conditions;
    }

    public void setConditions(List<ConditionDto> conditions) {
        this.conditions = conditions != null ? conditions : new ArrayList<>();
    }
}
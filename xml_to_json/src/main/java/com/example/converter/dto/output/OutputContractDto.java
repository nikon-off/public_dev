package com.example.converter.dto.output;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Выходная модель контракта: повторяет структуру {@code InputContractDto},
 * но условия внутри групп содержат каноническое представление XML-фильтра
 * ({@link OutputConditionDto#getCanonical()}) вместо сырой строки {@code xmlFilter}.
 *
 * <p>Соответствует структуре:
 * <pre>
 * {
 *   "contractId": "...",
 *   "contractName": "...",
 *   "groups": [ ... ]
 * }
 * </pre>
 */
public class OutputContractDto {

    @JsonProperty("contractId")
    private String contractId;

    @JsonProperty("contractName")
    private String contractName;

    @JsonProperty("groups")
    private List<OutputGroupDto> groups = new ArrayList<>();

    /** Конструктор по умолчанию (требуется Jackson для десериализации). */
    public OutputContractDto() {
    }

    /**
     * Полный конструктор.
     *
     * @param contractId   идентификатор контракта (UUID как строка)
     * @param contractName наименование договора
     * @param groups       список групп условий
     */
    public OutputContractDto(String contractId, String contractName, List<OutputGroupDto> groups) {
        this.contractId = contractId;
        this.contractName = contractName;
        this.groups = groups != null ? groups : new ArrayList<>();
    }

    public String getContractId() {
        return contractId;
    }

    public void setContractId(String contractId) {
        this.contractId = contractId;
    }

    public String getContractName() {
        return contractName;
    }

    public void setContractName(String contractName) {
        this.contractName = contractName;
    }

    public List<OutputGroupDto> getGroups() {
        return groups;
    }

    public void setGroups(List<OutputGroupDto> groups) {
        this.groups = groups != null ? groups : new ArrayList<>();
    }
}
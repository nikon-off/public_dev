package com.example.converter.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Входная модель контракта: верхнеуровневый объект JSON из файла-примера.
 *
 * <p>Соответствует структуре:
 * <pre>
 * {
 *   "contractId": "...",
 *   "contractName": "...",
 *   "groups": [ ... ]
 * }
 * </pre>
 * UUID контракта хранится как {@link String} для простоты (без валидации формата).
 */
public class InputContractDto {

    @JsonProperty("contractId")
    private String contractId;

    @JsonProperty("contractName")
    private String contractName;

    @JsonProperty("groups")
    private List<GroupDto> groups = new ArrayList<>();

    /** Конструктор по умолчанию (требуется Jackson для десериализации). */
    public InputContractDto() {
    }

    /**
     * Полный конструктор.
     *
     * @param contractId   идентификатор контракта (UUID как строка)
     * @param contractName наименование договора
     * @param groups       список групп условий
     */
    public InputContractDto(String contractId, String contractName, List<GroupDto> groups) {
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

    public List<GroupDto> getGroups() {
        return groups;
    }

    public void setGroups(List<GroupDto> groups) {
        this.groups = groups != null ? groups : new ArrayList<>();
    }
}
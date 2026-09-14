package com.example.converter;

import com.example.converter.dto.CanonicalConditionDto;
import com.example.converter.dto.ConditionDto;
import com.example.converter.dto.GroupDto;
import com.example.converter.dto.InputContractDto;
import com.example.converter.dto.output.OutputContractDto;
import com.example.converter.parser.DcsFilterParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Интеграционный тест полного конвейера «XML → JSON» на реальных данных.
 *
 * <p>Входной файл {@code test_to_json_condition.json} — это реальный контракт,
 * извлечённый из документа {@code test_to_json_condition.docx} (настройки 1С
 * с сырыми строками {@code xmlFilter}). Контракт прогоняется через весь конвейер
 * {@link Main#transform(InputContractDto)} (парсер {@link DcsFilterParser} →
 * билдер {@link com.example.converter.transformer.CanonicalConditionBuilder}),
 * а структура выходного JSON проверяется AssertJ-ассертами поверх дерева
 * Jackson {@link JsonNode}.
 *
 * <p>Ожидаемая схема выходного JSON:
 * <pre>
 * {
 *   "contractId": "...", "contractName": "...",
 *   "groups": [ {
 *     "groupId": "...", "groupName": "...",
 *     "conditions": [ {
 *       "conditionId": "...", "conditionName": "...",
 *       "canonical": { "logic": "OR"|null, "rules": [] }
 *     } ]
 *   } ]
 * }
 * </pre>
 *
 * <p>Реальные XML-фильтры данного контракта содержат секции {@code <filter>}
 * с группами, но без пригодных сравнений (нет {@code leftValuePath}/
 * {@code comparisonType} или нет {@code xsi:type}), поэтому канонические модели
 * имеют пустые {@code rules} и логику {@code OR} (или {@code null}, если секции
 * {@code <filter>} нет вовсе). Проверка опирается на эти наблюдаемые инварианты.
 */
class XmlToJsonPipelineIntegrationTest {

    /** Реальный входной контракт (извлечён из test_to_json_condition.docx). */
    private static final Path INPUT_FILE = Path.of("test_to_json_condition.json");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final DcsFilterParser PARSER = new DcsFilterParser();

    private static InputContractDto input;
    private static JsonNode outputTree;

    @BeforeAll
    static void runWholePipeline() throws IOException {
        if (!Files.exists(INPUT_FILE)) {
            throw new IllegalStateException("Файл с реальными данными не найден: "
                    + INPUT_FILE.toAbsolutePath()
                    + ". Интеграционный тест запускается из корня модуля (Maven basedir).");
        }
        input = MAPPER.readValue(Files.readString(INPUT_FILE), InputContractDto.class);
        OutputContractDto output = Main.transform(input);
        String outputJson = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(output);
        outputTree = MAPPER.readTree(outputJson);
    }

    // ------------------------------------------------------------------
    // Структура верхнего уровня
    // ------------------------------------------------------------------

    @Test
    void preservesContractHeaderAndGroupCount() {
        assertThat(outputTree.path("contractId").asText())
                .as("contractId сохраняется без изменений")
                .isEqualTo(input.getContractId());
        assertThat(outputTree.path("contractName").asText())
                .as("contractName сохраняется без изменений")
                .isEqualTo(input.getContractName());

        JsonNode groups = outputTree.path("groups");
        assertThat(groups.isArray()).as("groups должен быть массивом").isTrue();
        assertThat(groups.size()).as("количество групп").isEqualTo(input.getGroups().size());
    }

    @Test
    void preservesEveryGroupIdAndNameInOrder() {
        JsonNode groups = outputTree.path("groups");
        assertThat(groups.isArray()).as("groups должен быть массивом").isTrue();

        for (int i = 0; i < input.getGroups().size(); i++) {
            GroupDto expected = input.getGroups().get(i);
            JsonNode actual = groups.get(i);
            assertThat(actual.path("groupId").asText())
                    .as("groupId группы %d", i).isEqualTo(expected.getGroupId());
            assertThat(actual.path("groupName").asText())
                    .as("groupName группы %d", i).isEqualTo(expected.getGroupName());
            assertThat(actual.path("conditions").size())
                    .as("количество условий группы %d", i)
                    .isEqualTo(expected.getConditions().size());
        }
    }

    // ------------------------------------------------------------------
    // Схема каждого условия (условие → canonical{logic, rules})
    // ------------------------------------------------------------------

    @Test
    void everyConditionMatchesExpectedCanonicalSchema() {
        JsonNode groups = outputTree.path("groups");

        for (int g = 0; g < input.getGroups().size(); g++) {
            GroupDto expectedGroup = input.getGroups().get(g);
            JsonNode conditions = groups.get(g).path("conditions");

            for (int c = 0; c < expectedGroup.getConditions().size(); c++) {
                ConditionDto expectedCondition = expectedGroup.getConditions().get(c);
                JsonNode condition = conditions.get(c);

                String label = "группа[" + g + "].условие[" + c + "] (" + expectedCondition.getConditionName() + ")";

                assertThat(condition.path("conditionId").asText())
                        .as("conditionId, " + label).isEqualTo(expectedCondition.getConditionId());
                assertThat(condition.path("conditionName").asText())
                        .as("conditionName, " + label).isEqualTo(expectedCondition.getConditionName());

                // Точный набор ключей условия: никаких xmlFilter и лишних полей.
                assertThat(condition.properties())
                        .as("ключи условия, " + label)
                        .extracting(Map.Entry::getKey)
                        .containsExactlyInAnyOrder("conditionId", "conditionName", "canonical");

                JsonNode canonical = condition.path("canonical");
                assertThat(canonical.isObject())
                        .as("canonical должен быть объектом, " + label).isTrue();

                // Точный набор ключей канонической модели.
                assertThat(canonical.properties())
                        .as("ключи canonical, " + label)
                        .extracting(Map.Entry::getKey)
                        .containsExactlyInAnyOrder("logic", "rules");

                JsonNode logic = canonical.path("logic");
                assertThat(logic.isNull() || logic.isTextual())
                        .as("logic должен быть null или строкой, " + label).isTrue();

                JsonNode rules = canonical.path("rules");
                assertThat(rules.isArray())
                        .as("rules должен быть массивом, " + label).isTrue();
            }
        }
    }

    // ------------------------------------------------------------------
    // Точечные проверки на реальных условиях из docx
    // ------------------------------------------------------------------

    @Test
    void parsesRealXmlFilterFromFirstConditionOfContract() {
        String realXml = input.getGroups().get(0).getConditions().get(0).getXmlFilter();

        CanonicalConditionDto parsed = PARSER.parse(realXml);

        // «Регистрация Собственника районы Крыма»: секция <filter> с группой
        // OrGroup присутствует, но пригодных сравнений в ней нет → OR + пустые правила.
        assertThat(parsed.getLogic()).isEqualTo("OR");
        assertThat(parsed.getRules()).isEmpty();
    }

    @Test
    void producesEmptyCanonicalForConditionWithoutFilterSection() {
        // «ОСАГО Перестрахование»: в XML нет секции <filter> → пустая модель.
        JsonNode condition = findConditionById("167cc6bd-dd79-11ef-998b-86c6308b17d6");

        JsonNode canonical = condition.path("canonical");
        assertThat(canonical.path("logic").isNull())
                .as("logic должен быть null (нет секции <filter>)").isTrue();
        assertThat(canonical.path("rules").isArray() && canonical.path("rules").isEmpty())
                .as("rules должен быть пустым массивом").isTrue();
    }

    @Test
    void producesOrLogicWithEmptyRulesForGroupWithoutComparisons() {
        // «Категория договора ОСАГО - Переход из другой страховой»:
        // секция <filter> есть, группа OrGroup, но сравнения непригодны → OR + пустые правила.
        JsonNode condition = findConditionById("4b7b094f-4ed4-11f1-99ad-0050569df0b4");

        JsonNode canonical = condition.path("canonical");
        assertThat(canonical.path("logic").asText())
                .as("logic должен быть OR (OrGroup без пригодных сравнений)")
                .isEqualTo("OR");
        assertThat(canonical.path("rules").isArray() && canonical.path("rules").isEmpty())
                .as("rules должен быть пустым массивом").isTrue();
    }

    // ------------------------------------------------------------------
    // Вспомогательные операции
    // ------------------------------------------------------------------

    /** Ищет узел условия в выходном JSON по {@code conditionId}. */
    private static JsonNode findConditionById(String conditionId) {
        for (JsonNode group : outputTree.path("groups")) {
            for (JsonNode condition : group.path("conditions")) {
                if (conditionId.equals(condition.path("conditionId").asText())) {
                    return condition;
                }
            }
        }
        throw new AssertionError("Условие не найдено в выходном JSON: " + conditionId);
    }
}
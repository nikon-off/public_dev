package com.example.converter;

import com.example.converter.dto.CanonicalConditionDto;
import com.example.converter.dto.ConditionDto;
import com.example.converter.dto.GroupDto;
import com.example.converter.dto.InputContractDto;
import com.example.converter.dto.RuleDto;
import com.example.converter.dto.output.OutputContractDto;
import com.example.converter.parser.DcsFilterParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Интеграционный тест полного конвейера «XML → JSON» на реальных данных.
 *
 * <p>
 * Входной файл {@code test_to_json_condition.json} — это реальный контракт,
 * извлечённый из документа {@code test_to_json_condition.docx} (настройки 1С
 * с сырыми строками {@code xmlFilter}). Контракт прогоняется через весь
 * конвейер
 * {@link Main#transform(InputContractDto)} (парсер {@link DcsFilterParser} →
 * билдер {@link com.example.converter.transformer.CanonicalConditionBuilder}),
 * а структура выходного JSON проверяется AssertJ-ассертами поверх дерева
 * Jackson {@link JsonNode}.
 *
 * <p>
 * Ожидаемая схема выходного JSON:
 * 
 * <pre>
 * {
 *   "contractId": "...", "contractName": "...",
 *   "groups": [ {
 *     "groupId": "...", "groupName": "...",
 *     "conditions": [ {
 *       "conditionId": "...", "conditionName": "...",
 *       "canonical": { "logic": "OR"|"AND"|null, "rules": [] }
 *     } ]
 *   } ]
 * }
 * </pre>
 *
 * <p>
 * Реальные XML-фильтры контракта используют сериализацию DCS
 * ({@code left}/{@code comparisonType}/повторяющиеся {@code right}): поле — тег
 * {@code <left xsi:type="dcscor:Field">}, значения — теги
 * {@code <right xsi:type="…">}
 * (типы {@code v8:UUID}, {@code xs:boolean}, {@code d4p1:CatalogRef.*},
 * {@code d4p1:EnumRef.*}). Сравнения лежат либо внутри {@code FilterItemGroup}
 * ({@code OrGroup}), либо напрямую в секции {@code <filter>} — несколько
 * сравнений
 * без группы дают логику {@code AND} (дефолт DCS). Все 27 условий контракта
 * имеют
 * непустую секцию {@code <filter>} с пригодными сравнениями, поэтому
 * канонические
 * модели содержат непустые {@code rules} и логику {@code OR} либо {@code AND}.
 * Проверки опираются на эти наблюдаемые инварианты.
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

                                String label = "группа[" + g + "].условие[" + c + "] ("
                                                + expectedCondition.getConditionName() + ")";

                                assertThat(condition.path("conditionId").asText())
                                                .as("conditionId, " + label)
                                                .isEqualTo(expectedCondition.getConditionId());
                                assertThat(condition.path("conditionName").asText())
                                                .as("conditionName, " + label)
                                                .isEqualTo(expectedCondition.getConditionName());

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

                // «Регистрация Собственника районы Крыма»: OrGroup с двумя сравнениями
                // InList в реальном формате DCS (left/comparisonType/right) → OR + 2 правила
                // IN с 10 и 14 значениями (фактические данные контракта).
                assertThat(parsed.getLogic()).isEqualTo("OR");

                assertThat(parsed.getRules()).hasSize(2);
                RuleDto first = parsed.getRules().get(0);
                assertThat(first.getField()).isEqualTo("ГородФИАС");
                assertThat(first.getOperator()).isEqualTo("IN");
                assertThat(first.getValues())
                                .as("значения первого сравнения (10 шт.) совпадают со списком UUID из XML")
                                .containsExactlyInAnyOrderElementsOf(List.of(
                                                "b7b1ef3e-bd1c-4080-80ea-f0580b40281b",
                                                "afb681bd-4264-40ce-a676-cab8b33c7ca0",
                                                "9481ba52-663f-4cac-9ba9-67f168016eab",
                                                "e11bab0f-338d-43e0-bfa4-1d3c87759e2a",
                                                "ddc99126-8c9c-49e0-be2b-e05d2dcdf22e",
                                                "1fb30227-c758-4b1a-814b-eab9c8e7a582",
                                                "8000a81d-5035-43f1-82ab-e8be388af860",
                                                "c24797d4-43e8-470f-9b4b-de57db14cb72",
                                                "b30938ee-407c-4c56-8946-95a888735dfe",
                                                "0459ee79-6bd5-411b-8fd4-4c9864418fdc"))
                                .hasSize(10);

                RuleDto second = parsed.getRules().get(1);
                assertThat(second.getField()).isEqualTo("РайонФИАС");
                assertThat(second.getOperator()).isEqualTo("IN");
                assertThat(second.getValues())
                                .as("значения второго сравнения (14 шт.) совпадают со списком UUID из XML")
                                .containsExactlyInAnyOrderElementsOf(List.of(
                                                "b1f8603c-22c1-4c0d-9297-4d5cbef9b0ee",
                                                "5c6a204a-5468-46d8-8cf3-1121232ace22",
                                                "25002033-9830-4b3c-aa8c-3501d1a34898",
                                                "5df661a2-b724-412c-a0b4-4b70ed26e05e",
                                                "02cc296d-b15f-446b-a856-86cdc087b593",
                                                "449d1089-7126-4af6-b5de-aba83ca33db8",
                                                "9d5f8ade-6f9d-46c0-9aab-9fcae5dd5a2e",
                                                "9ebe3b6c-d859-45b2-afe1-fc97407b3a02",
                                                "0989cf50-3130-46c8-b121-c3e38e93002b",
                                                "33b76096-93f4-4709-bae2-f140830ca585",
                                                "b27a6bf8-a714-41d0-a561-cf928a7581b5",
                                                "f2832bd7-2259-4734-a5ef-152dcf0010f3",
                                                "0560cf17-9698-40ee-b462-f65afce6e1f0",
                                                "ff26b263-42b0-4bdf-8aaf-7111c281ea2c"))
                                .hasSize(14);
        }

        @Test
        void producesAndWithTwoEqualRulesForDirectComparisons() {
                // «ОСАГО Перестрахование»: два сравнения Equal напрямую в <filter>
                // (без FilterItemGroup) → логика AND по умолчанию DCS, 2 правила EQ.
                JsonNode condition = findConditionById("167cc6bd-dd79-11ef-998b-86c6308b17d6");

                JsonNode canonical = condition.path("canonical");
                assertThat(canonical.path("logic").asText())
                                .as("сравнения без группы → AND")
                                .isEqualTo("AND");

                JsonNode rules = canonical.path("rules");
                assertThat(rules).hasSize(2);

                assertThat(rules.get(0).path("field").asText()).isEqualTo("перестрахование_рса");
                assertThat(rules.get(0).path("operator").asText()).isEqualTo("EQ");
                assertThat(rules.get(0).path("values"))
                                .as("xs:boolean false → \"false\"")
                                .extracting(JsonNode::asText)
                                .containsExactly("false");

                assertThat(rules.get(1).path("field").asText()).isEqualTo("страховой_продукт");
                assertThat(rules.get(1).path("operator").asText()).isEqualTo("EQ");
                assertThat(rules.get(1).path("values"))
                                .as("CatalogRef.* значение → UUID")
                                .extracting(JsonNode::asText)
                                .containsExactly("342ec861-3f65-11e6-9e61-7824af33beda");
        }

        @Test
        void producesOrLogicWithInListRulesForOrGroup() {
                // «Категория договора ОСАГО - Переход из другой страховой»:
                // OrGroup с двумя сравнениями InList (EnumRef.* значения) → OR + 2 правила IN.
                JsonNode condition = findConditionById("4b7b094f-4ed4-11f1-99ad-0050569df0b4");

                JsonNode canonical = condition.path("canonical");
                assertThat(canonical.path("logic").asText())
                                .as("OrGroup → OR")
                                .isEqualTo("OR");

                JsonNode rules = canonical.path("rules");
                assertThat(rules).hasSize(2);

                assertThat(rules.get(0).path("field").asText()).isEqualTo("век21_категория_договора");
                assertThat(rules.get(0).path("operator").asText()).isEqualTo("IN");
                assertThat(rules.get(0).path("values"))
                                .extracting(JsonNode::asText)
                                .containsExactly("TRANSITION", "TRANSITION_EARLY");

                assertThat(rules.get(1).path("field").asText())
                                .isEqualTo("первоначальный_договор_век21_категория_договора");
                assertThat(rules.get(1).path("operator").asText()).isEqualTo("IN");
                assertThat(rules.get(1).path("values"))
                                .extracting(JsonNode::asText)
                                .containsExactly("TRANSITION", "TRANSITION_EARLY");
        }

        @Test
        void producesAndWithTwoEqualRulesForAllRussianOsago() {
                // «ОСАГО РФ все»: два сравнения Equal напрямую в <filter> → AND + 2 правила EQ.
                JsonNode condition = findConditionById("a75254ce-9cff-11f0-99ab-0050569df0b4");

                JsonNode canonical = condition.path("canonical");
                assertThat(canonical.path("logic").asText())
                                .as("сравнения без группы → AND")
                                .isEqualTo("AND");

                JsonNode rules = canonical.path("rules");
                assertThat(rules).hasSize(2);

                assertThat(rules.get(0).path("field").asText()).isEqualTo("страховой_продукт");
                assertThat(rules.get(0).path("operator").asText()).isEqualTo("EQ");
                assertThat(rules.get(0).path("values"))
                                .extracting(JsonNode::asText)
                                .containsExactly("342ec861-3f65-11e6-9e61-7824af33beda");

                assertThat(rules.get(1).path("field").asText())
                                .isEqualTo("объект_страхования_страна_регистрации");
                assertThat(rules.get(1).path("operator").asText()).isEqualTo("EQ");
                assertThat(rules.get(1).path("values"))
                                .extracting(JsonNode::asText)
                                .containsExactly("c498e3ad-4f3d-11e6-80d4-002590393a15");
        }

        @Test
        void producesAndWithTwoEqualRulesForPolicyholderIndividual() {
                // «ОСАГО Страхователь ФЛ»: два сравнения Equal → AND; второе значение —
                // EnumRef.* («ФизЛицо»), обычная строка без нормализации.
                JsonNode condition = findConditionById("edb90698-a90d-11f0-99ab-0050569df0b4");

                JsonNode canonical = condition.path("canonical");
                assertThat(canonical.path("logic").asText())
                                .as("сравнения без группы → AND")
                                .isEqualTo("AND");

                JsonNode rules = canonical.path("rules");
                assertThat(rules).hasSize(2);

                assertThat(rules.get(0).path("field").asText()).isEqualTo("страховой_продукт");
                assertThat(rules.get(0).path("operator").asText()).isEqualTo("EQ");
                assertThat(rules.get(0).path("values"))
                                .extracting(JsonNode::asText)
                                .containsExactly("342ec861-3f65-11e6-9e61-7824af33beda");

                assertThat(rules.get(1).path("field").asText()).isEqualTo("контрагент_юр_физ_лицо");
                assertThat(rules.get(1).path("operator").asText()).isEqualTo("EQ");
                assertThat(rules.get(1).path("values"))
                                .extracting(JsonNode::asText)
                                .containsExactly("ФизЛицо");
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
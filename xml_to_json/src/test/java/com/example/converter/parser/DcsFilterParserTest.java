package com.example.converter.parser;

import com.example.converter.dto.CanonicalConditionDto;
import com.example.converter.dto.RuleDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Юнит-тесты парсера {@link DcsFilterParser} на реальном фрагменте
 * XML-настроек 1С (Data Composition System) с множественными namespaces.
 *
 * <p>
 * Покрываются оба формата сериализации сравнений:
 * <ul>
 * <li>синтетический формат из документации ({@code leftValuePath}/
 * {@code rightValue}>{@code value});</li>
 * <li>реальный формат DCS из контракта ({@code left}/{@code comparisonType}/
 * повторяющиеся {@code right}, каждый с атрибутом {@code xsi:type}).</li>
 * </ul>
 */
class DcsFilterParserTest {

    private final DcsFilterParser parser = new DcsFilterParser();

    /** Общие namespace-объявления из документации 1С DCS. */
    private static final String NAMESPACES = "xmlns=\"http://v8.1c.ru/8.1/data-composition-system/settings\" "
            + "xmlns:dcscor=\"http://v8.1c.ru/8.1/data-composition-system/core\" "
            + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
            + "xmlns:v8=\"http://v8.1c.ru/8.1/data/core\" "
            + "xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"";

    @Test
    void parsesOrGroupWithInListFromDocumentationExample() {
        String xml = """
                <Settings %s>
                    <filter>
                        <item xsi:type="FilterItemGroup">
                            <groupType>OrGroup</groupType>
                            <item xsi:type="FilterItemComparison">
                                <leftValuePath>ГородФИАС</leftValuePath>
                                <comparisonType>InList</comparisonType>
                                <rightValue>
                                    <value xsi:type="v8:UUID">11111111-1111-1111-1111-111111111111</value>
                                    <value xsi:type="v8:UUID">22222222-2222-2222-2222-222222222222</value>
                                </rightValue>
                            </item>
                        </item>
                    </filter>
                    <selection><item>UI-шум, игнорируется</item></selection>
                    <title>UI-шум, игнорируется</title>
                </Settings>
                """.formatted(NAMESPACES);

        CanonicalConditionDto result = parser.parse(xml);

        assertEquals("OR", result.getLogic(), "OrGroup должен нормализоваться в OR");
        assertEquals(1, result.getRules().size(), "Должно быть ровно одно правило");

        RuleDto rule = result.getRules().get(0);
        assertEquals("ГородФИАС", rule.getField());
        assertEquals("IN", rule.getOperator(), "InList должен нормализоваться в IN");
        assertEquals(
                List.of("11111111-1111-1111-1111-111111111111",
                        "22222222-2222-2222-2222-222222222222"),
                rule.getValues(),
                "Значения всегда приводятся к списку строк, атрибуты типов игнорируются");
    }

    @Test
    void parsesOrGroupWithInListFromRealFormat() {
        // Реальный формат DCS из контракта: поле в <left xsi:type="dcscor:Field">,
        // значения — повторяющиеся <right xsi:type="v8:UUID"> (каждый = одно значение).
        String xml = """
                <Settings %s>
                    <filter>
                        <item xsi:type="FilterItemGroup">
                            <groupType>OrGroup</groupType>
                            <item xsi:type="FilterItemComparison">
                                <left xsi:type="dcscor:Field">ГородФИАС</left>
                                <comparisonType>InList</comparisonType>
                                <right xsi:type="v8:UUID">11111111-1111-1111-1111-111111111111</right>
                                <right xsi:type="v8:UUID">22222222-2222-2222-2222-222222222222</right>
                                <right xsi:type="v8:UUID">33333333-3333-3333-3333-333333333333</right>
                            </item>
                        </item>
                    </filter>
                </Settings>
                """.formatted(NAMESPACES);

        CanonicalConditionDto result = parser.parse(xml);

        assertEquals("OR", result.getLogic(), "OrGroup должен нормализоваться в OR");
        assertEquals(1, result.getRules().size(), "Должно быть ровно одно правило");

        RuleDto rule = result.getRules().get(0);
        assertEquals("ГородФИАС", rule.getField(),
                "Поле читается из тега <left> (текст из поля \"\" узла)");
        assertEquals("IN", rule.getOperator(), "InList должен нормализоваться в IN");
        assertEquals(
                List.of("11111111-1111-1111-1111-111111111111",
                        "22222222-2222-2222-2222-222222222222",
                        "33333333-3333-3333-3333-333333333333"),
                rule.getValues(),
                "Все повторяющиеся <right> собираются в список значений в порядке следования");
    }

    @Test
    void parsesComparisonsDirectlyInFilterAsAnd() {
        // Реальный контракт («ОСАГО Перестрахование»): два FilterItemComparison
        // напрямую в <filter> без FilterItemGroup. Первый — xs:boolean false,
        // второй — локальный xmlns:d4p1 + xsi:type="d4p1:CatalogRef.*" (UUID).
        String xml = """
                <Settings %s>
                    <filter>
                        <item xsi:type="FilterItemComparison">
                            <left xsi:type="dcscor:Field">ПерестрахованиеРСА</left>
                            <comparisonType>Equal</comparisonType>
                            <right xsi:type="xs:boolean">false</right>
                        </item>
                        <item xsi:type="FilterItemComparison">
                            <left xsi:type="dcscor:Field">СтраховойПродукт</left>
                            <comparisonType>Equal</comparisonType>
                            <right xmlns:d4p1="http://v8.1c.ru/8.1/data/enterprise/current-config"
                                   xsi:type="d4p1:CatalogRef.СтраховойПродукт">342ec861-3f65-11e6-9e61-7824af33beda</right>
                        </item>
                    </filter>
                </Settings>
                """
                .formatted(NAMESPACES);

        CanonicalConditionDto result = parser.parse(xml);

        assertEquals("AND", result.getLogic(),
                "Несколько сравнений напрямую в filter без группы → AND (дефолт DCS)");
        assertEquals(2, result.getRules().size());

        RuleDto first = result.getRules().get(0);
        assertEquals("ПерестрахованиеРСА", first.getField());
        assertEquals("EQ", first.getOperator(), "Equal должен нормализоваться в EQ");
        assertEquals(List.of("false"), first.getValues(),
                "xs:boolean false становится строкой \"false\"");

        RuleDto second = result.getRules().get(1);
        assertEquals("СтраховойПродукт", second.getField());
        assertEquals("EQ", second.getOperator());
        assertEquals(List.of("342ec861-3f65-11e6-9e61-7824af33beda"), second.getValues(),
                "Текст <right> с локальным xmlns:d4p1 извлекается без учёта атрибутов");
    }

    @Test
    void parsesAndGroupWithEqual() {
        String xml = """
                <Settings %s>
                    <filter>
                        <item xsi:type="FilterItemGroup">
                            <groupType>AndGroup</groupType>
                            <item xsi:type="FilterItemComparison">
                                <leftValuePath>Регион</leftValuePath>
                                <comparisonType>Equal</comparisonType>
                                <rightValue>
                                    <value xsi:type="v8:String">Крым</value>
                                </rightValue>
                            </item>
                        </item>
                    </filter>
                </Settings>
                """.formatted(NAMESPACES);

        CanonicalConditionDto result = parser.parse(xml);

        assertEquals("AND", result.getLogic());
        assertEquals(1, result.getRules().size());

        RuleDto rule = result.getRules().get(0);
        assertEquals("Регион", rule.getField());
        assertEquals("EQ", rule.getOperator(), "Equal должен нормализоваться в EQ");
        assertEquals(List.of("Крым"), rule.getValues(), "Одиночное значение приводится к списку из одного элемента");
    }

    @Test
    void normalizesNotEqualAndNotInList() {
        String xml = """
                <Settings %s>
                    <filter>
                        <item xsi:type="FilterItemGroup">
                            <groupType>AndGroup</groupType>
                            <item xsi:type="FilterItemComparison">
                                <leftValuePath>Статус</leftValuePath>
                                <comparisonType>NotEqual</comparisonType>
                                <rightValue><value>Черновик</value></rightValue>
                            </item>
                            <item xsi:type="FilterItemComparison">
                                <leftValuePath>Регион</leftValuePath>
                                <comparisonType>NotInList</comparisonType>
                                <rightValue>
                                    <value>Москва</value>
                                    <value>Санкт-Петербург</value>
                                </rightValue>
                            </item>
                        </item>
                    </filter>
                </Settings>
                """.formatted(NAMESPACES);

        CanonicalConditionDto result = parser.parse(xml);

        assertEquals("AND", result.getLogic());
        assertEquals(2, result.getRules().size());
        assertEquals("NEQ", result.getRules().get(0).getOperator(), "NotEqual → NEQ");
        assertEquals("NOT_IN", result.getRules().get(1).getOperator(), "NotInList → NOT_IN");
        assertEquals(List.of("Москва", "Санкт-Петербург"), result.getRules().get(1).getValues());
    }

    @Test
    void flattensNestedGroupsIntoFlatRuleList() {
        String xml = """
                <Settings %s>
                    <filter>
                        <item xsi:type="FilterItemGroup">
                            <groupType>AndGroup</groupType>
                            <item xsi:type="FilterItemGroup">
                                <groupType>OrGroup</groupType>
                                <item xsi:type="FilterItemComparison">
                                    <leftValuePath>Поле1</leftValuePath>
                                    <comparisonType>Equal</comparisonType>
                                    <rightValue><value>A</value></rightValue>
                                </item>
                            </item>
                            <item xsi:type="FilterItemComparison">
                                <leftValuePath>Поле2</leftValuePath>
                                <comparisonType>Equal</comparisonType>
                                <rightValue><value>B</value></rightValue>
                            </item>
                        </item>
                    </filter>
                </Settings>
                """.formatted(NAMESPACES);

        CanonicalConditionDto result = parser.parse(xml);

        // Документированное ограничение модели: вложенные группы разворачиваются
        // в плоский список, логика берётся с верхнего уровня.
        assertEquals("AND", result.getLogic());
        assertEquals(2, result.getRules().size());
        assertEquals("Поле1", result.getRules().get(0).getField());
        assertEquals("Поле2", result.getRules().get(1).getField());
    }

    @Test
    void skipsUnknownTagsAndUnknownTypesInsideFilter() {
        String xml = """
                <Settings %s>
                    <filter>
                        <someUnknownTag>мусор</someUnknownTag>
                        <item xsi:type="FilterItemGroup">
                            <groupType>OrGroup</groupType>
                            <presentation>UI-шум</presentation>
                            <item xsi:type="FilterItemComparison">
                                <leftValuePath>Город</leftValuePath>
                                <comparisonType>Equal</comparisonType>
                                <rightValue><value>Екатеринбург</value></rightValue>
                            </item>
                            <item xsi:type="UnknownItemType">
                                <leftValuePath>Игнор</leftValuePath>
                                <comparisonType>Equal</comparisonType>
                            </item>
                        </item>
                    </filter>
                </Settings>
                """.formatted(NAMESPACES);

        CanonicalConditionDto result = parser.parse(xml);

        assertEquals("OR", result.getLogic());
        assertEquals(1, result.getRules().size(), "Неизвестные теги и типы не должны попадать в правила");
        assertEquals("Город", result.getRules().get(0).getField());
    }

    @Test
    void returnsEmptyConditionWhenFilterSectionMissing() {
        String xml = """
                <Settings %s>
                    <selection><item>только UI</item></selection>
                </Settings>
                """.formatted(NAMESPACES);

        CanonicalConditionDto result = parser.parse(xml);

        assertNull(result.getLogic(), "Без <filter> логика должна быть null");
        assertTrue(result.getRules().isEmpty(), "Без <filter> правила должны отсутствовать");
    }

    @Test
    void returnsEmptyConditionForEmptyFilterSection() {
        String xml = """
                <Settings %s>
                    <filter>
                    </filter>
                </Settings>
                """.formatted(NAMESPACES);

        CanonicalConditionDto result = parser.parse(xml);

        assertNull(result.getLogic(), "Пустой <filter></filter> не должен давать логику");
        assertTrue(result.getRules().isEmpty(), "Пустой <filter></filter> должен давать пустой список правил");
    }

    @Test
    void returnsEmptyConditionForBlankAndNullInput() {
        assertTrue(parser.parse(null).getRules().isEmpty());
        assertTrue(parser.parse("   \n\t ").getRules().isEmpty());
    }

    @Test
    void throwsIllegalArgumentExceptionForMalformedXml() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse("<Settings><filter>"));
        assertThrows(IllegalArgumentException.class, () -> parser.parse("not-xml-at-all"));
    }
}
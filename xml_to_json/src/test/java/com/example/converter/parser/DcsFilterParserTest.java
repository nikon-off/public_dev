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
 */
class DcsFilterParserTest {

    private final DcsFilterParser parser = new DcsFilterParser();

    /** Общие namespace-объявления из документации 1С DCS. */
    private static final String NAMESPACES =
            "xmlns=\"http://v8.1c.ru/8.1/data-composition-system/settings\" "
            + "xmlns:dcscor=\"http://v8.1c.ru/8.1/data-composition-system/core\" "
            + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
            + "xmlns:v8=\"http://v8.1c.ru/8.1/data/core\"";

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
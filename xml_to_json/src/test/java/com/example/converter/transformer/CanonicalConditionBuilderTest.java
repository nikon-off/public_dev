package com.example.converter.transformer;

import com.example.converter.dto.CanonicalConditionDto;
import com.example.converter.dto.RuleDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Юнит-тесты трансформатора {@link CanonicalConditionBuilder}: нормализация
 * полей (snake_case), операторов (канонический стандарт), значений
 * (UUID, булевы) и логической связки на основе результата парсера.
 */
class CanonicalConditionBuilderTest {

    private final CanonicalConditionBuilder builder = new CanonicalConditionBuilder();

    // ------------------------------------------------------------------
    // Нормализация полей (Field Mapping)
    // ------------------------------------------------------------------

    @Test
    void normalizesLatinCamelCaseFieldToSnakeCase() {
        CanonicalConditionDto result = builder.build(conditionOf(
                new RuleDto("cityFias", "EQ", List.of("A"))));

        assertEquals("city_fias", result.getRules().get(0).getField());
    }

    @Test
    void splitsAcronymRunBeforeFollowingWord() {
        CanonicalConditionDto result = builder.build(conditionOf(
                new RuleDto("FIASCode", "EQ", List.of("A"))));

        assertEquals("fias_code", result.getRules().get(0).getField());
    }

    @Test
    void normalizesCyrillicFieldToLowercaseSnakeCase() {
        CanonicalConditionDto result = builder.build(conditionOf(
                new RuleDto("ГородФИАС", "IN", List.of("A")),
                new RuleDto("Регион", "EQ", List.of("Крым"))));

        assertEquals("город_фиас", result.getRules().get(0).getField(),
                "Кириллица приводится к нижнему регистру с разделением CamelCase");
        assertEquals("регион", result.getRules().get(1).getField(),
                "Одиночное кириллическое слово приводится к нижнему регистру");
    }

    @Test
    void replacesWhitespaceWithUnderscoresInField() {
        CanonicalConditionDto result = builder.build(conditionOf(
                new RuleDto("Код города", "EQ", List.of("A"))));

        assertEquals("код_города", result.getRules().get(0).getField());
    }

    @Test
    void keepsAlreadyNormalizedSnakeCaseField() {
        CanonicalConditionDto result = builder.build(conditionOf(
                new RuleDto("city_fias", "EQ", List.of("A"))));

        assertEquals("city_fias", result.getRules().get(0).getField());
    }

    @Test
    void explicitFieldMappingWinsOverGenericNormalization() {
        CanonicalConditionBuilder mappedBuilder =
                new CanonicalConditionBuilder(Map.of("ГородФИАС", "city_fias"));

        CanonicalConditionDto result = mappedBuilder.build(conditionOf(
                new RuleDto("ГородФИАС", "EQ", List.of("A")),
                new RuleDto("Регион", "EQ", List.of("Крым"))));

        assertEquals("city_fias", result.getRules().get(0).getField(),
                "Явный маппинг 1С-имени имеет приоритет");
        assertEquals("регион", result.getRules().get(1).getField(),
                "Немаппируемые поля обрабатываются общей нормализацией");
    }

    // ------------------------------------------------------------------
    // Нормализация операторов (Operator Mapping)
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "оператор ''{0}'' → {1}")
    @CsvSource({
            "Equal,   EQ",
            "=,       EQ",
            "NotEqual, NEQ",
            "!=,      NEQ",
            "InList,  IN",
            "IN,      IN",
            "NotInList, NOT_IN",
            "NOT IN,  NOT_IN",
            "Greater, GT",
            ">,       GT",
            "Less,    LT",
            "<,       LT",
            "equal,   EQ"
    })
    void normalizesSupportedOperatorAliases(String rawOperator, String expected) {
        CanonicalConditionDto result = builder.build(conditionOf(
                new RuleDto("field", rawOperator, List.of("A"))));

        assertEquals(expected, result.getRules().get(0).getOperator());
    }

    @Test
    void skipsRuleWithUnknownOperator() {
        CanonicalConditionDto result = builder.build(conditionOf(
                new RuleDto("field", "Contains", List.of("A"))));

        assertTrue(result.getRules().isEmpty(),
                "Правило с неизвестным оператором пропускается с предупреждением");
    }

    @Test
    void skipsRuleWithBlankField() {
        CanonicalConditionDto result = builder.build(conditionOf(
                new RuleDto("   ", "EQ", List.of("A"))));

        assertTrue(result.getRules().isEmpty(), "Правило без поля бесполезно и пропускается");
    }

    // ------------------------------------------------------------------
    // Нормализация значений (Value Normalization)
    // ------------------------------------------------------------------

    @Test
    void normalizesUppercaseHyphenatedUuidToLowercase() {
        CanonicalConditionDto result = builder.build(conditionOf(
                new RuleDto("field", "EQ", List.of("AAAAAAAA-AAAA-AAAA-AAAA-AAAAAAAAAAAA"))));

        assertEquals(List.of("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                result.getRules().get(0).getValues());
    }

    @Test
    void insertsHyphensIntoUuidWithoutSeparators() {
        CanonicalConditionDto result = builder.build(conditionOf(
                new RuleDto("field", "EQ", List.of("11111111111111111111111111111111"))));

        assertEquals(List.of("11111111-1111-1111-1111-111111111111"),
                result.getRules().get(0).getValues());
    }

    @Test
    void lowercasesMixedCaseUuidWithoutSeparators() {
        CanonicalConditionDto result = builder.build(conditionOf(
                new RuleDto("field", "EQ", List.of("AAAA1111AAAA1111AAAA1111AAAA1111"))));

        assertEquals(List.of("aaaa1111-aaaa-1111-aaaa-1111aaaa1111"),
                result.getRules().get(0).getValues());
    }

    @Test
    void keepsAlreadyCanonicalUuidUnchanged() {
        CanonicalConditionDto result = builder.build(conditionOf(
                new RuleDto("field", "EQ", List.of("11111111-1111-1111-1111-111111111111"))));

        assertEquals(List.of("11111111-1111-1111-1111-111111111111"),
                result.getRules().get(0).getValues());
    }

    @Test
    void normalizesBooleanValuesToLowercaseStrings() {
        CanonicalConditionDto result = builder.build(conditionOf(
                new RuleDto("isActive", "EQ", List.of("TRUE")),
                new RuleDto("isDeleted", "NEQ", List.of("False"))));

        assertEquals(List.of("true"), result.getRules().get(0).getValues());
        assertEquals(List.of("false"), result.getRules().get(1).getValues());
    }

    @Test
    void normalizesEveryElementInsideInListValues() {
        CanonicalConditionDto result = builder.build(conditionOf(
                new RuleDto("region", "IN", List.of(" TRUE ", "false"))));

        assertEquals(List.of("true", "false"), result.getRules().get(0).getValues(),
                "Булевы значения внутри списка нормализуются, пробелы обрезаются");
    }

    @Test
    void keepsSingleValueAsOneElementListForIn() {
        CanonicalConditionDto result = builder.build(conditionOf(
                new RuleDto("region", "IN", List.of("Крым"))));

        assertEquals(List.of("Крым"), result.getRules().get(0).getValues(),
                "Для IN одиночное значение остаётся списком из одного элемента");
    }

    // ------------------------------------------------------------------
    // Логическая связка (логика верхнего уровня)
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "логика ''{0}'' → {1}")
    @CsvSource({
            "OrGroup, OR",
            "AND,     AND",
            "and,     AND",
            "xor,     XOR"
    })
    void normalizesLogicAliases(String rawLogic, String expected) {
        CanonicalConditionDto input = new CanonicalConditionDto(rawLogic,
                List.of(new RuleDto("field", "EQ", List.of("A"))));

        CanonicalConditionDto result = builder.build(input);

        assertEquals(expected, result.getLogic());
    }

    @Test
    void keepsNullLogicWhenInputHasNoLogic() {
        CanonicalConditionDto result = builder.build(new CanonicalConditionDto());

        assertNull(result.getLogic(), "Пустое условие без логики остаётся без логики");
    }

    // ------------------------------------------------------------------
    // Контракт build(...)
    // ------------------------------------------------------------------

    @Test
    void returnsEmptyDtoForNullInput() {
        CanonicalConditionDto result = builder.build(null);

        assertNull(result.getLogic());
        assertTrue(result.getRules().isEmpty());
    }

    @Test
    void doesNotMutateInputDto() {
        List<RuleDto> rules = List.of(
                new RuleDto("cityFias", "InList", List.of("TRUE", "11111111111111111111111111111111")));
        CanonicalConditionDto input = new CanonicalConditionDto("OrGroup", rules);

        builder.build(input);

        assertEquals("cityFias", input.getRules().get(0).getField());
        assertEquals("InList", input.getRules().get(0).getOperator());
        assertEquals(List.of("TRUE", "11111111111111111111111111111111"),
                input.getRules().get(0).getValues());
    }

    @Test
    void preservesRuleOrderAndTopLevelLogic() {
        CanonicalConditionDto input = new CanonicalConditionDto("AND", List.of(
                new RuleDto("Поле1", "Equal", List.of("A")),
                new RuleDto("Поле2", "Greater", List.of("B"))));

        CanonicalConditionDto result = builder.build(input);

        assertEquals("AND", result.getLogic(), "Верхнеуровневая логика сохраняется");
        assertEquals(2, result.getRules().size(), "Плоская структура правил сохраняется");
        assertEquals("поле1", result.getRules().get(0).getField());
        assertEquals("поле2", result.getRules().get(1).getField());
        assertEquals("GT", result.getRules().get(1).getOperator());
    }

    /** Вспомогательный метод: условие с заданной логикой OR и списком правил. */
    private static CanonicalConditionDto conditionOf(RuleDto... rules) {
        return new CanonicalConditionDto("OR", List.of(rules));
    }
}
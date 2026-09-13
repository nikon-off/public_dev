package com.example.converter.transformer;

import com.example.converter.dto.CanonicalConditionDto;
import com.example.converter.dto.RuleDto;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Трансформатор данных: нормализует результат работы парсера
 * ({@link CanonicalConditionDto}) в строгий канонический формат для хранения
 * в БД и дальнейших проверок.
 *
 * <h2>Контракт {@link #build(CanonicalConditionDto)}</h2>
 * <ul>
 *   <li>{@code null} на входе → пустой {@link CanonicalConditionDto}
 *       (отсутствие условия ≠ ошибка контракта, как и в парсере).</li>
 *   <li>Входной DTO не мутируется: всегда возвращается новая копия.</li>
 *   <li>Плоская структура сохраняется: вложенные группы уже развёрнуты
 *       парсером, трансформатор лишь нормализует верхнеуровневую логику.</li>
 * </ul>
 *
 * <h2>Нормализация полей</h2>
 * <ol>
 *   <li>Явный маппинг 1С-имён (инжектируемый {@code Map<String, String>})
 *       имеет приоритет — точка расширения для будущей таблицы соответствий.</li>
 *   <li>Общая нормализация: {@code camelCase}/{@code PascalCase} → snake_case
 *       (включая кириллицу и аббревиатуры: {@code FIASCode} → {@code fias_code},
 *       {@code ГородФИАС} → {@code город_фиас}); пробелы и разделители → {@code _};
 *       всё приводится к нижнему регистру.</li>
 * </ol>
 *
 * <h2>Нормализация операторов</h2>
 * Канонический стандарт: {@code EQ}, {@code NEQ}, {@code IN}, {@code NOT_IN},
 * {@code GT}, {@code LT}. Поддерживаемые алиасы: {@code Equal}/{@code =},
 * {@code NotEqual}/{@code !=}, {@code InList}, {@code NotInList}/{@code NOT IN},
 * {@code Greater}/{@code >}, {@code Less}/{@code <}.
 * <p><b>Стратегия ошибок:</b> правило с неизвестным оператором пропускается
 * с предупреждением в лог — строгий канонический формат не допускает
 * «полунормализованных» правил.</p>
 *
 * <h2>Нормализация значений</h2>
 * <ul>
 *   <li>UUID: гибридный вид приводится к стандартному строковому представлению
 *       (дефисы, нижний регистр); 32-значная hex-строка без дефисов
 *       интерпретируется как UUID и форматируется по шаблону 8-4-4-4-12.</li>
 *   <li>Булевы: {@code true}/{@code false} (без учёта регистра и пробелов)
 *       → строки {@code "true"}/{@code "false"}.</li>
 *   <li>Значения всегда представлены {@link List}{@code <String>}: для унарных
 *   операторов — список из одного элемента, для {@code IN}/{@code NOT_IN} —
 *   все элементы списка.</li>
 * </ul>
 *
 * <h2>Логическая связка</h2>
 * {@code OrGroup}/{@code or} → {@code OR}, {@code AndGroup}/{@code and} → {@code AND}.
 * Неизвестные значения сохраняются с приведением к верхнему регистру
 * (без потери значения); {@code null} остаётся {@code null}.
 */
public class CanonicalConditionBuilder {

    private static final Logger LOG = Logger.getLogger(CanonicalConditionBuilder.class.getName());

    /** Канонические операторы. */
    public static final String OP_EQ = "EQ";
    public static final String OP_NEQ = "NEQ";
    public static final String OP_IN = "IN";
    public static final String OP_NOT_IN = "NOT_IN";
    public static final String OP_GT = "GT";
    public static final String OP_LT = "LT";

    private static final String LOGIC_OR = "OR";
    private static final String LOGIC_AND = "AND";

    /** UUID с дефисами (без учёта регистра). */
    private static final String UUID_HYPHENATED_PATTERN =
            "(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    /** 32 hex-символа без дефисов — UUID без разделителей. */
    private static final String UUID_HEX32_PATTERN = "(?i)[0-9a-f]{32}";

    /**
     * Алиасы операторов (после {@code trim} + верхний регистр) → канонический
     * оператор. Включает как имена из XML 1С, так и символьные формы.
     */
    private static final Map<String, String> OPERATOR_ALIASES = Map.ofEntries(
            Map.entry("EQUAL", OP_EQ), Map.entry("=", OP_EQ), Map.entry("EQ", OP_EQ),
            Map.entry("NOTEQUAL", OP_NEQ), Map.entry("!=", OP_NEQ), Map.entry("NEQ", OP_NEQ),
            Map.entry("INLIST", OP_IN), Map.entry("IN", OP_IN),
            Map.entry("NOTINLIST", OP_NOT_IN), Map.entry("NOT IN", OP_NOT_IN),
            Map.entry("NOT_IN", OP_NOT_IN),
            Map.entry("GREATER", OP_GT), Map.entry(">", OP_GT), Map.entry("GT", OP_GT),
            Map.entry("LESS", OP_LT), Map.entry("<", OP_LT), Map.entry("LT", OP_LT)
    );

    /** Алиасы логических связок → каноническая логика верхнего уровня. */
    private static final Map<String, String> LOGIC_ALIASES = Map.of(
            "OR", LOGIC_OR, "ORGROUP", LOGIC_OR,
            "AND", LOGIC_AND, "ANDGROUP", LOGIC_AND
    );

    /** Явный маппинг 1С-имён полей → канонические имена (расширяемая точка). */
    private final Map<String, String> fieldMapping;

    /** Конструктор без маппинга: только общая нормализация. */
    public CanonicalConditionBuilder() {
        this(Map.of());
    }

    /**
     * Конструктор с инжекцией явного маппинга полей.
     *
     * @param fieldMapping соответствия «сырое имя 1С» → «каноническое имя»;
     *                     {@code null} трактуется как пустой маппинг
     */
    public CanonicalConditionBuilder(Map<String, String> fieldMapping) {
        this.fieldMapping = fieldMapping != null ? Map.copyOf(fieldMapping) : Map.of();
    }

    /**
     * Нормализует результат парсера в канонический формат.
     *
     * @param input результат работы парсера ({@link CanonicalConditionDto})
     * @return новый нормализованный DTO; пустой DTO, если {@code input == null}
     */
    public CanonicalConditionDto build(CanonicalConditionDto input) {
        if (input == null) {
            LOG.warning("build(null): входное условие отсутствует, возвращается пустой DTO");
            return new CanonicalConditionDto();
        }
        String logic = normalizeLogic(input.getLogic());
        List<RuleDto> rules = new ArrayList<>();
        for (RuleDto rule : input.getRules()) {
            RuleDto normalized = normalizeRule(rule);
            if (normalized != null) {
                rules.add(normalized);
            }
        }
        return new CanonicalConditionDto(logic, rules);
    }

    // ------------------------------------------------------------------
    // Нормализация одиночного правила
    // ------------------------------------------------------------------

    /** Возвращает нормализованное правило или {@code null}, если правило бесполезно. */
    private RuleDto normalizeRule(RuleDto rule) {
        if (rule == null) {
            LOG.warning("Список правил содержит null-элемент — пропущен");
            return null;
        }
        String field = normalizeField(rule.getField());
        if (field == null) {
            LOG.warning("Правило с пустым полем пропущено (raw field: '" + rule.getField() + "')");
            return null;
        }
        String operator = normalizeOperator(rule.getOperator());
        if (operator == null) {
            LOG.warning("Неизвестный оператор '" + rule.getOperator()
                    + "' для поля '" + rule.getField() + "' — правило пропущено");
            return null;
        }
        return new RuleDto(field, operator, normalizeValues(rule.getValues()));
    }

    // ------------------------------------------------------------------
    // Нормализация полей (Field Mapping)
    // ------------------------------------------------------------------

    /**
     * Явный маппинг (при наличии) → общая snake_case-нормализация.
     *
     * @return нормализованное имя поля или {@code null} для пустого имени
     */
    private String normalizeField(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String mapped = fieldMapping.get(trimmed);
        if (mapped != null) {
            return mapped;
        }
        return toSnakeCase(trimmed);
    }

    /**
     * Приводит имя к snake_case: {@code camelCase}/{@code PascalCase} →
     * {@code snake_case} (работает и для кириллицы), пробелы и разделители
     * {@code -}, {@code .} → {@code _}, всё к нижнему регистру.
     */
    private static String toSnakeCase(String input) {
        StringBuilder sb = new StringBuilder(input.length() + 8);
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (Character.isWhitespace(c) || c == '-' || c == '.') {
                appendUnderscore(sb);
                continue;
            }
            if (Character.isUpperCase(c)) {
                char prev = i > 0 ? input.charAt(i - 1) : '\0';
                char next = i + 1 < input.length() ? input.charAt(i + 1) : '\0';
                // граница перед заглавной после строчной буквы/цифры: "cityFias"
                boolean afterLowerOrDigit = i > 0
                        && (Character.isLowerCase(prev) || Character.isDigit(prev));
                // граница внутри аббревиатуры перед последней заглавной: "FIASCode"
                boolean beforeLowerCase = i > 0 && Character.isUpperCase(prev)
                        && next != '\0' && Character.isLowerCase(next);
                if (afterLowerOrDigit || beforeLowerCase) {
                    appendUnderscore(sb);
                }
            }
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }

    /** Добавляет подчёркивание, схлопывая дубли и лидирующие разделители. */
    private static void appendUnderscore(StringBuilder sb) {
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '_') {
            sb.append('_');
        }
    }

    // ------------------------------------------------------------------
    // Нормализация операторов (Operator Mapping)
    // ------------------------------------------------------------------

    /** Возвращает канонический оператор или {@code null} для неизвестного. */
    private String normalizeOperator(String raw) {
        if (raw == null) {
            return null;
        }
        String key = raw.trim().toUpperCase(Locale.ROOT);
        return OPERATOR_ALIASES.get(key);
    }

    // ------------------------------------------------------------------
    // Нормализация значений (Value Normalization)
    // ------------------------------------------------------------------

    /** Нормализует каждый элемент списка значений; пустые элементы отбрасываются. */
    private List<String> normalizeValues(List<String> rawValues) {
        if (rawValues == null || rawValues.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> result = new ArrayList<>(rawValues.size());
        for (String raw : rawValues) {
            String normalized = normalizeValue(raw);
            if (normalized != null) {
                result.add(normalized);
            }
        }
        return result;
    }

    /** Булево → каноническая строка; UUID → стандартное представление; иначе trim. */
    private String normalizeValue(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if ("true".equalsIgnoreCase(trimmed)) {
            return "true";
        }
        if ("false".equalsIgnoreCase(trimmed)) {
            return "false";
        }
        if (trimmed.matches(UUID_HYPHENATED_PATTERN)) {
            return trimmed.toLowerCase(Locale.ROOT);
        }
        if (trimmed.matches(UUID_HEX32_PATTERN)) {
            return insertUuidHyphens(trimmed.toLowerCase(Locale.ROOT));
        }
        return trimmed;
    }

    /** Форматирует 32 hex-символа по шаблону UUID 8-4-4-4-12. */
    private static String insertUuidHyphens(String hex) {
        return hex.substring(0, 8) + '-' + hex.substring(8, 12) + '-'
                + hex.substring(12, 16) + '-' + hex.substring(16, 20) + '-'
                + hex.substring(20);
    }

    // ------------------------------------------------------------------
    // Нормализация логической связки
    // ------------------------------------------------------------------

    /**
     * Известные связки → канонические {@code OR}/{@code AND}; неизвестные
     * сохраняются с приведением к верхнему регистру; {@code null} → {@code null}.
     */
    private String normalizeLogic(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String key = trimmed.toUpperCase(Locale.ROOT);
        return LOGIC_ALIASES.getOrDefault(key, key);
    }
}
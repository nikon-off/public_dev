package com.example.converter.parser;

import com.example.converter.dto.CanonicalConditionDto;
import com.example.converter.dto.RuleDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Парсер XML-фильтров 1С:Предприятие (формат Data Composition System / DCS)
 * в каноническую модель условия {@link CanonicalConditionDto}.
 *
 * <p>Из настроек {@code <Settings>} извлекается только секция {@code <filter>};
 * UI-шум ({@code <selection>}, {@code <title>} и пр.) игнорируется.</p>
 *
 * <p>Реализация построена на {@link XmlMapper#readTree(String)} (Jackson XML):
 * XML читается в {@link JsonNode}-дерево, которое затем обходится программно.
 * Это надёжнее маппинга на POJO, т.к. элементы фильтра полиморфны
 * (динамический атрибут {@code xsi:type}: {@code FilterItemGroup} /
 * {@code FilterItemComparison}).</p>
 *
 * <h2>Поведение и ограничения</h2>
 * <ul>
 *   <li>{@code null}/{@code blank} вход или отсутствие секции {@code <filter>}
 *       → возвращается пустой {@link CanonicalConditionDto} (логика отсутствия
 *       условия ≠ ошибка контракта).</li>
 *   <li>Невалидный XML (не well-formed) → {@link IllegalArgumentException},
 *       т.к. это ошибка входных данных, а не «условие отсутствует».</li>
 *   <li>Неизвестные теги внутри {@code <filter>} и элементы с неизвестным
 *       {@code xsi:type} молча пропускаются (устойчивость к эволюции формата).</li>
 *   <li>Правило без {@code leftValuePath} или без {@code comparisonType}
 *       считается бесполезным и пропускается.</li>
 *   <li>Вложенные группы ({@code FilterItemGroup} внутри {@code FilterItemGroup})
 *       рекурсивно разворачиваются в плоский список {@code rules}; логика
 *       берётся с верхнего уровня группы. Ограничение текущей модели:
 *       {@link CanonicalConditionDto} не поддерживает вложенность связок
 *       (YAGNI — расширяется при появлении реальной потребности).</li>
 * </ul>
 */
public class DcsFilterParser {

    /** Префикс, которым Jackson XML помечает XML-атрибуты в JsonNode-дереве. */
    private static final String ATTR_PREFIX = "@";

    private static final String TYPE_ATTR_LOCAL_NAME = "type";

    /**
     * Нормализация операторов сравнения 1С → канонические операторы.
     * Неизвестные операторы сохраняются в исходном виде (без потери информации).
     */
    private static final Map<String, String> OPERATOR_NORMALIZATION = Map.of(
            "InList", "IN",
            "Equal", "EQ",
            "NotEqual", "NEQ",
            "NotInList", "NOT_IN"
    );

    /**
     * Нормализация типа группы 1С → каноническая логическая связка.
     * Неизвестные типы сохраняются в исходном виде.
     */
    private static final Map<String, String> GROUP_LOGIC_NORMALIZATION = Map.of(
            "OrGroup", "OR",
            "AndGroup", "AND"
    );

    private final XmlMapper xmlMapper;

    /** Конструктор с собственным {@link XmlMapper} (рекомендуется). */
    public DcsFilterParser() {
        this(new XmlMapper());
    }

    /**
     * Конструктор с внедрением готового {@link XmlMapper}
     * (удобно для тестов и общих настроек).
     *
     * @param xmlMapper настроенный экземпляр Jackson XML mapper
     */
    public DcsFilterParser(XmlMapper xmlMapper) {
        this.xmlMapper = xmlMapper;
    }

    /**
     * Разбирает XML-строку настроек 1С и возвращает каноническое условие.
     *
     * @param xmlContent сырой XML настроек DCS (поле {@code xmlFilter})
     * @return каноническое условие; пустой объект, если секции {@code <filter>} нет
     * @throws IllegalArgumentException если входной XML не является well-formed
     */
    public CanonicalConditionDto parse(String xmlContent) {
        if (xmlContent == null || xmlContent.isBlank()) {
            return new CanonicalConditionDto();
        }
        JsonNode root = readRoot(xmlContent);
        JsonNode filter = findFirst(root, "filter");
        if (filter == null) {
            return new CanonicalConditionDto();
        }
        List<RuleDto> rules = new ArrayList<>();
        String logic = parseGroup(filter, rules);
        return new CanonicalConditionDto(logic, rules);
    }

    /**
     * Рекурсивно обрабатывает группу: находит дочерние {@code <item>} и
     * диспетчеризует их по атрибуту {@code xsi:type}.
     *
     * @param groupNode узел группы (или секции {@code <filter>})
     * @param rules     накопитель правил (плоский список)
     * @return нормализованная логика группы (может быть {@code null})
     */
    private String parseGroup(JsonNode groupNode, List<RuleDto> rules) {
        String logic = null;
        for (JsonNode item : childrenNamed(groupNode, "item")) {
            String type = fieldLocal(item, TYPE_ATTR_LOCAL_NAME);
            if (type == null) {
                continue; // item без xsi:type — пропускаем (устойчивость)
            }
            switch (type) {
                case "FilterItemGroup" -> {
                    String groupLogic = normalizeGroupLogic(textOfChild(item, "groupType"));
                    if (logic == null) {
                        logic = groupLogic;
                    }
                    // Вложенная группа рекурсивно разворачивается в правила родителя
                    // (см. ограничение в javadoc класса).
                    parseGroup(item, rules);
                }
                case "FilterItemComparison" -> {
                    RuleDto rule = parseComparison(item);
                    if (rule != null) {
                        rules.add(rule);
                    }
                }
                default -> {
                    // Неизвестный тип элемента — молча пропускаем (устойчивость).
                }
            }
        }
        return logic;
    }

    /**
     * Преобразует элемент {@code FilterItemComparison} в {@link RuleDto}.
     *
     * @param item узел сравнения
     * @return правило или {@code null}, если поле или оператор отсутствуют
     */
    private RuleDto parseComparison(JsonNode item) {
        String field = textOfChild(item, "leftValuePath");
        String rawOperator = textOfChild(item, "comparisonType");
        if (isBlank(field) || isBlank(rawOperator)) {
            return null;
        }
        String operator = OPERATOR_NORMALIZATION.getOrDefault(rawOperator, rawOperator);
        return new RuleDto(field, operator, collectValues(item));
    }

    /**
     * Собирает тексты всех узлов {@code <value>} внутри {@code <rightValue>}.
     *
     * @param item узел сравнения
     * @return список значений (никогда {@code null})
     */
    private List<String> collectValues(JsonNode item) {
        List<String> values = new ArrayList<>();
        JsonNode rightValue = directChild(item, "rightValue");
        if (rightValue != null) {
            collectValueNodes(rightValue, values);
        }
        return values;
    }

    /** Рекурсивный обход в поисках узлов {@code <value>} на любой глубине. */
    private void collectValueNodes(JsonNode node, List<String> values) {
        if (node.isArray()) {
            for (JsonNode element : node) {
                collectValueNodes(element, values);
            }
            return;
        }
        if (!node.isContainerNode()) {
            return;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String name = entry.getKey();
            if (name.startsWith(ATTR_PREFIX)) {
                continue; // атрибуты (xsi:type и пр.) не содержат значений
            }
            JsonNode child = entry.getValue();
            if ("value".equals(localPart(name))) {
                if (child.isArray()) {
                    for (JsonNode element : child) {
                        addValueText(element, values);
                    }
                } else {
                    addValueText(child, values);
                }
            } else {
                collectValueNodes(child, values);
            }
        }
    }

    /** Извлекает текст из узла {@code value} (с учётом атрибутов у самого узла). */
    private void addValueText(JsonNode valueNode, List<String> values) {
        String text = nodeText(valueNode);
        if (!isBlank(text)) {
            values.add(text.trim());
        }
    }

    // ------------------------------------------------------------------
    // Вспомогательные операции над JsonNode-деревом
    // ------------------------------------------------------------------

    /**
     * Ищет первый элемент с заданным локальным именем (без учёта namespace
     * префикса) обходом в глубину, начиная от {@code node}.
     */
    private JsonNode findFirst(JsonNode node, String localName) {
        if (node == null) {
            return null;
        }
        if (node.isArray()) {
            for (JsonNode element : node) {
                JsonNode found = findFirst(element, localName);
                if (found != null) {
                    return found;
                }
            }
            return null;
        }
        if (!node.isContainerNode()) {
            return null;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String name = entry.getKey();
            if (name.startsWith(ATTR_PREFIX)) {
                continue;
            }
            JsonNode child = entry.getValue();
            if (localName.equals(localPart(name))) {
                return child;
            }
            JsonNode found = findFirst(child, localName);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /**
     * Возвращает всех прямых дочерних элементов с заданным локальным именем.
     * Учитывает, что Jackson XML сводит повторяющиеся элементы в {@code ArrayNode}.
     */
    private List<JsonNode> childrenNamed(JsonNode parent, String localName) {
        List<JsonNode> result = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> fields = parent.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String name = entry.getKey();
            if (name.startsWith(ATTR_PREFIX) || !localName.equals(localPart(name))) {
                continue;
            }
            JsonNode value = entry.getValue();
            if (value.isArray()) {
                value.forEach(result::add);
            } else {
                result.add(value);
            }
        }
        return result;
    }

    /** Возвращает первый прямой дочерний элемент с заданным локальным именем. */
    private JsonNode directChild(JsonNode parent, String localName) {
        List<JsonNode> found = childrenNamed(parent, localName);
        return found.isEmpty() ? null : found.get(0);
    }

    /** Текст прямого дочернего элемента или {@code null}. */
    private String textOfChild(JsonNode parent, String localName) {
        JsonNode child = directChild(parent, localName);
        return child == null ? null : nodeText(child);
    }

    /**
     * Значение поля по локальному имени (без учёта namespace префикса).
     *
     * <p>Специально для {@code xsi:type}: Jackson XML (2.x) с включённой по
     * умолчанию фичей {@code AUTO_DETECT_XSI_TYPE} представляет атрибут как
     * обычное поле {@code "xsi:type"}, а с выключенной — как атрибут
     * {@code "@xsi:type"}. Поиск по локальному имени {@code type} покрывает
     * оба представления.</p>
     */
    private String fieldLocal(JsonNode node, String localName) {
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            if (localName.equals(localPart(entry.getKey()))) {
                JsonNode value = entry.getValue();
                if (value.isTextual()) {
                    return value.asText();
                }
            }
        }
        return null;
    }

    /**
     * Текст узла: для {@code TextNode} — сам текст; для объекта со смешанным
     * содержимым (атрибуты + текст) — текст из поля {@code ""}.
     */
    private String nodeText(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isContainerNode()) {
            JsonNode textField = node.get("");
            if (textField != null && textField.isTextual()) {
                return textField.asText();
            }
        }
        return null;
    }

    /** Локальная часть имени (отбрасывает namespace префикс до {@code ':'}). */
    private static String localPart(String name) {
        int colon = name.indexOf(':');
        return colon >= 0 ? name.substring(colon + 1) : name;
    }

    private static String normalizeGroupLogic(String raw) {
        if (isBlank(raw)) {
            return null;
        }
        return GROUP_LOGIC_NORMALIZATION.getOrDefault(raw, raw);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** Читает XML-дерево; невалидный XML превращает в {@link IllegalArgumentException}. */
    private JsonNode readRoot(String xmlContent) {
        try {
            return xmlMapper.readTree(xmlContent);
        } catch (IOException e) {
            throw new IllegalArgumentException("Не удалось распарсить XML-фильтр 1С: " + e.getMessage(), e);
        }
    }
}
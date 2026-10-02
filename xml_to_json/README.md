
# XML to JSON Converter

Конвертер XML-фильтров 1С (формат Data Composition System / DCS) в канонический JSON для хранения и дальнейшей обработки.

## Описание

Утилита предназначена для преобразования настроек фильтров из конфигураций 1С:Предприятие в структурированный JSON-формат. Основная задача — парсинг сырых XML-строк, содержащих условия отбора данных (DCS), и их нормализация в единый канонический формат.

### Ключевые возможности

*   **Парсинг XML DCS**: Извлечение условий фильтрации из XML-настроек 1С
*   **Нормализация операторов**: Приведение различных форматов операторов сравнения к каноническому виду (`EQ`, `NEQ`, `IN`, `NOT_IN`, `GT`, `LT`)
*   **Нормализация полей**: Преобразование имён полей в snake_case с поддержкой кириллицы и аббревиатур
*   **Обработка UUID**: Стандартизация представлений UUID (с дефисами, без дефисов)
*   **Поддержка логических связок**: Корректная обработка `AND`/`OR` группировок условий
*   **CLI интерфейс**: Простой запуск через командную строку

## Архитектура

Проект построен по принципу конвейера обработки данных:

```text
Входной JSON -> Парсер XML -> Трансформатор -> Выходной JSON
```

### Структура пакетов

```text
src/main/java/com/example/converter/
├── Main.java                          # Точка входа CLI
├── dto/                               # Модели данных
│   ├── InputContractDto.java          # Входной контракт
│   ├── GroupDto.java                  # Группа условий
│   ├── ConditionDto.java              # Условие с XML-фильтром
│   ├── CanonicalConditionDto.java     # Каноническая модель условия
│   ├── RuleDto.java                   # Правило (поле + оператор + значения)
│   └── output/                        # Выходные DTO
│       ├── OutputContractDto.java
│       ├── OutputGroupDto.java
│       └── OutputConditionDto.java
├── parser/                            # Парсер XML
│   └── DcsFilterParser.java           # Парсер DCS-фильтров 1С
└── transformer/                       # Трансформатор данных
    ├── CanonicalConditionBuilder.java # Нормализация условий
    └── TransformerConfig.java         # Конфигурация трансформатора
```

## Как работает

### 1. Входные данные

Входной файл — JSON-контракт со следующей структурой:

```json
{
  "contractId": "uuid",
  "contractName": "Название договора",
  "groups": [
    {
      "groupId": "uuid",
      "groupName": "Название группы",
      "conditions": [
        {
          "conditionId": "uuid",
          "conditionName": "Название условия",
          "xmlFilter": "<Settings xmlns=\"http://v8.1c.ru/...\">...</Settings>"
        }
      ]
    }
  ]
}
```

Поле `xmlFilter` содержит сырую XML-строку настроек фильтра 1С формата DCS.

### 2. Парсинг XML (`DcsFilterParser`)

Парсер использует Jackson XML для чтения XML в дерево `JsonNode` и программно обходит его:

*   Извлекает секцию `<filter>` из настроек
*   Распознаёт элементы по атрибуту `xsi:type`:
    *   `FilterItemGroup` — группа условий (логическая связка)
    *   `FilterItemComparison` — одиночное условие сравнения
*   Рекурсивно разворачивает вложенные группы в плоский список правил
*   Поддерживает реальный формат DCS:
    *   Поле: тег `<left xsi:type="dcscor:Field">`
    *   Оператор: тег `<comparisonType>`
    *   Значения: повторяющиеся теги `<right>`

**Особенности:**
*   Пустой или отсутствующий `xmlFilter` возвращает пустую каноническую модель (не ошибка)
*   Невалидный XML вызывает `IllegalArgumentException`
*   Неизвестные типы элементов молча пропускаются (устойчивость к эволюции формата)

### 3. Трансформация (`CanonicalConditionBuilder`)

Трансформатор нормализует результат парсера:

#### Нормализация полей
*   **Строгий режим** (`preserveOriginalFieldNames=true`): имена передаются без изменений (сохраняется регистр и точки)
*   **Обычный режим** (по умолчанию):
    1.  Явный маппинг 1С-имён (через `Map<String, String>`)
    2.  Общая нормализация: camelCase/PascalCase переходит в snake_case
        *   Примеры: `FIASCode` становится `fias_code`, `ГородФИАС` становится `город_фиас`

#### Нормализация операторов

| Исходный | Канонический |
| :--- | :--- |
| `Equal`, `=`, `EQ` | `EQ` |
| `NotEqual`, `!=`, `NEQ` | `NEQ` |
| `InList`, `IN` | `IN` |
| `NotInList`, `NOT IN`, `NOT_IN` | `NOT_IN` |
| `Greater`, `>`, `GT` | `GT` |
| `Less`, `<`, `LT` | `LT` |

Неизвестные операторы пропускаются с предупреждением в лог.

#### Нормализация значений
*   **UUID**: гибридные формы приводятся к стандартному представлению (дефисы, нижний регистр)
*   **Булевы**: `true`/`false` (без учёта регистра) преобразуются в строки `"true"`/`"false"`
*   Все значения представлены как `List<String>`

#### Логические связки
*   `OrGroup`, `or` преобразуется в `OR`
*   `AndGroup`, `and` преобразуется в `AND`
*   Несколько сравнений без группы получают `AND` (дефолт DCS)

### 4. Выходные данные

Выходной файл имеет ту же структуру, что и входной, но вместо `xmlFilter` каждое условие содержит типизированный объект `canonical`:

```json
{
  "contractId": "uuid",
  "contractName": "Название договора",
  "groups": [
    {
      "groupId": "uuid",
      "groupName": "Название группы",
      "conditions": [
        {
          "conditionId": "uuid",
          "conditionName": "Название условия",
          "canonical": {
            "logic": "OR",
            "rules": [
              {
                "field": "город_фиас",
                "operator": "IN",
                "values": ["uuid1", "uuid2", "..."]
              }
            ]
          }
        }
      ]
    }
  ]
}
```

## Установка и сборка

### Требования

*   Java 21 или выше
*   Maven 3.6+

### Сборка проекта

```bash
mvn clean package
```

Результат сборки: `target/xml-to-json-converter-1.0.0-SNAPSHOT-jar-with-dependencies.jar`

## Использование

### Запуск через JAR

```bash
java -jar target/xml-to-json-converter-1.0.0-SNAPSHOT-jar-with-dependencies.jar <input-file.json>
```

Пример:
```bash
java -jar target/xml-to-json-converter-1.0.0-SNAPSHOT-jar-with-dependencies.jar test_to_json_condition.json
```

Выходной файл создаётся рядом с входным с суффиксом `_processed.json`.

### Запуск из IDE

Запустите класс `com.example.converter.Main` с аргументом — путём к входному JSON-файлу.

## Тестирование

### Запуск тестов

```bash
mvn test
```

Проект содержит интеграционный тест `XmlToJsonPipelineIntegrationTest`, который:
*   Загружает реальный контракт из файла `test_to_json_condition.json`
*   Прогоняет его через весь конвейер
*   Проверяет структуру выходного JSON и корректность парсинга конкретных условий

### Примеры тестовых данных

В проекте присутствуют файлы:
*   `test_to_json_condition.docx` — исходный документ с настройками 1С
*   `test_to_json_condition.json` — извлечённый JSON-контракт
*   `test_to_json_condition_processed.json` — результат обработки
*   `cli_output.json` — пример вывода CLI

## Конфигурация

### Настройка трансформатора

Класс `TransformerConfig` позволяет настроить поведение нормализации:

```java
// Строгий режим: имена полей сохраняются без изменений
TransformerConfig config = new TransformerConfig(true);
CanonicalConditionBuilder builder = new CanonicalConditionBuilder(config);

// С явным маппингом полей
Map<String, String> fieldMapping = Map.of(
    "ОбъектСтрахования.СтранаРегистрации", "object_country",
    "Контрагент.ЮрФизЛицо", "counterparty_type"
);
TransformerConfig config = new TransformerConfig(false, fieldMapping);
CanonicalConditionBuilder builder = new CanonicalConditionBuilder(config);
```

## Примеры использования

### Пример 1: Фильтр с OR-группой

Входной XML:
```xml
<filter>
  <item xsi:type="FilterItemGroup">
    <groupType>OrGroup</groupType>
    <item xsi:type="FilterItemComparison">
      <left xsi:type="dcscor:Field">ГородФИАС</left>
      <comparisonType>InList</comparisonType>
      <right xsi:type="v8:UUID">b7b1ef3e-bd1c-4080-80ea-f0580b40281b</right>
      <right xsi:type="v8:UUID">afb681bd-4264-40ce-a676-cab8b33c7ca0</right>
    </item>
    <item xsi:type="FilterItemComparison">
      <left xsi:type="dcscor:Field">РайонФИАС</left>
      <comparisonType>InList</comparisonType>
      <right xsi:type="v8:UUID">b1f8603c-22c1-4c0d-9297-4d5cbef9b0ee</right>
    </item>
  </item>
</filter>
```

Выходной JSON:
```json
{
  "logic": "OR",
  "rules": [
    {
      "field": "город_фиас",
      "operator": "IN",
      "values": ["b7b1ef3e-bd1c-4080-80ea-f0580b40281b", "afb681bd-4264-40ce-a676-cab8b33c7ca0"]
    },
    {
      "field": "район_фиас",
      "operator": "IN",
      "values": ["b1f8603c-22c1-4c0d-9297-4d5cbef9b0ee"]
    }
  ]
}
```

### Пример 2: Прямые сравнения без группы

Входной XML:
```xml
<filter>
  <item xsi:type="FilterItemComparison">
    <left xsi:type="dcscor:Field">перестрахование_рса</left>
    <comparisonType>Equal</comparisonType>
    <right xsi:type="xs:boolean">false</right>
  </item>
  <item xsi:type="FilterItemComparison">
    <left xsi:type="dcscor:Field">страховой_продукт</left>
    <comparisonType>Equal</comparisonType>
    <right xsi:type="d4p1:CatalogRef._СтраховыеПродукты">342ec861-3f65-11e6-9e61-7824af33beda</right>
  </item>
</filter>
```

Выходной JSON:
```json
{
  "logic": "AND",
  "rules": [
    {
      "field": "перестрахование_рса",
      "operator": "EQ",
      "values": ["false"]
    },
    {
      "field": "страховой_продукт",
      "operator": "EQ",
      "values": ["342ec861-3f65-11e6-9e61-7824af33beda"]
    }
  ]
}
```

## Где можно использовать

### 1. Интеграция с базами данных
Хранение нормализованных условий в PostgreSQL (JSONB) для быстрого поиска и фильтрации.

### 2. Matching Engine
Использование канонических условий для сопоставления страховых продуктов с параметрами клиентов:
*   Проверка соответствия региона регистрации
*   Фильтрация по типу контрагента (ФЛ/ЮЛ)
*   Отбор по категориям договоров

### 3. Миграция данных 1С
Конвертация старых настроек фильтров при переходе на новые конфигурации или системы.

### 4. Аналитика и отчётность
Агрегация и анализ условий страхования по различным критериям.

### 5. API для внешних систем
Предоставление структурированных условий через REST API вместо сырых XML.

## Особенности реализации

### Устойчивость к изменениям формата
*   Неизвестные XML-элементы и типы молча пропускаются
*   Поддержка обоих форматов представления значений:
    *   Реальный DCS: `<left>`/`<comparisonType>`/`<right>`
    *   Синтетический: `<leftValuePath>`/`<rightValue>/<value>`

### Обработка ошибок
*   Отсутствие `xmlFilter` возвращает пустую каноническую модель (штатная ситуация)
*   Невалидный XML вызывает `IllegalArgumentException` с описанием ошибки
*   Неизвестный оператор приводит к пропуску правила с предупреждением в лог
*   Пустое поле приводит к пропуску правила

### Производительность
*   Переиспользование `ObjectMapper` и `XmlMapper` (потокобезопасны после конфигурации)
*   Минимум аллокаций при обработке больших контрактов
*   Плоская структура правил (без глубокой вложенности)

## Лицензия

MIT License

## Авторы

Kolya (nikon-off)

## История изменений

*   **Oct 2, 2026**: Рефакторинг кода
*   **Sep 26, 2026**: Исправление парсера
*   **Sep 21, 2026**: Доработка `CanonicalConditionBuilder`
*   **Sep 14, 2026**: Тестирование и сборка
*   **Sep 13, 2026**: Инициализация проекта
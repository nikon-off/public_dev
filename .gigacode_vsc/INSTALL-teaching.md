# Установка Teaching Agent для GigaCode

## Быстрый старт

### 1. Файлы уже в проекте

Все файлы созданы в `.gigacode_vsc/`:

```
.gigacode_vsc/
├── agent/
│   └── teaching.md              # Агент
├── command/
│   ├── explain.md               # Команда: объяснить
│   └── lesson.md                # Команда: провести урок
├── skills/
│   └── teaching/
│       ├── SKILL.md             # Основной навык
│       ├── teaching-patterns.md # Паттерны объяснений
│       └── anti-patterns.md     # Антипаттерны
└── gigacode.json                # Обновлён (добавлен skills.paths)
```

### 2. Конфигурация

В `.gigacode_vsc/gigacode.json` уже добавлено:

```json
{
  "skills": {
    "paths": [".gigacode_vsc/skills"]
  }
}
```

### 3. Использование

| Команда | Описание |
|---|---|
| `/explain <код или файл>` | Объяснить код, функцию или концепцию |
| `/lesson <тема>` | Провести структурированный урок |
| `@teaching <вопрос>` | Прямой вызов агента |

## Ручная установка (если файлы скопированы отдельно)

### Шаг 1: Разместить файлы

Скопируйте файлы в структуру выше. Убедитесь что пути совпадают точно.

### Шаг 2: Добавить skills.paths в gigacode.json

```json
{
  "skills": {
    "paths": [".gigacode_vsc/skills"]
  }
}
```

Добавьте этот объект в существующий `gigacode.json`, сохранив все текущие поля.

### Шаг 3: Перезапустить GigaCode

Перезапустите IDE/GigaCode для применения конфигурации.

## Структура навыков

| Файл | Назначение |
|---|---|
| `skills/teaching/SKILL.md` | Основной навык — процесс обучения, Socratic method, red flags |
| `skills/teaching/teaching-patterns.md` | Коллекция паттернов объяснений (аналогии, step-by-step, before/after) |
| `skills/teaching/anti-patterns.md` | Антипаттерны — что избегать (lecture mode, answer dumping) |
| `agent/teaching.md` | Агент — системный промт с инструкциями |
| `command/explain.md` | Команда `/explain` |
| `command/lesson.md` | Команда `/lesson` |

## Зависимости

- Навык `teaching` ссылается на `superpowers:test-driven-development` и `superpowers:brainstorming` для методологической основы
- Агент требует загруженного навыка `superpowers:teaching`

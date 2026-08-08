# Speech-to-Text с диаризацией спикеров (WhisperX)

Этот проект представляет собой инструмент для транскрипции аудиофайлов с диаризацией спикеров (разделением по говорящим).

## Запуск через .bat/.cmd файлы (Windows)

Для удобства запуска без ввода команд вручную созданы файлы:

### Вариант 1: Прямой запуск (.bat файл)
Дважды кликните на `start_whisper.bat` — откроется окно командной строки, автоматически активируется окружение Conda и запустится транскрибация.

### Вариант 2: Через cmd.exe с параметрами (/K)
Дважды кликните на `Запустить_Whisper.cmd` — этот файл открывает командную строку Windows (`cmd.exe`) с параметром `/K`, который оставляет окно открытым после выполнения скрипта. Внутри автоматически:
1. Активируется Conda окружение `whisper_env`
2. Запускается `main.py`
3. Окно остаётся открытым для просмотра результатов

**Важно:** В файле `start_whisper.bat` указан путь к Anaconda пользователя `Kolya`. Если ваш пользователь имеет другое имя, отредактируйте файл и измените строку:
```bat
set "CONDA_PATH=C:\Users\Kolya\anaconda3"
```
на правильный путь к вашей установке Anaconda.

### Как создать настоящий .exe файл (опционально)

Если вы хотите получить один исполняемый `.exe` файл вместо `.bat`:

1. Установите PyInstaller:
   ```bash
   conda activate whisper_env
   pip install pyinstaller
   ```

2. Создайте exe-файл:
   ```bash
   pyinstaller --onefile --name WhisperLauncher main.py
   ```

3. Готовый файл появится в папке `dist/WhisperLauncher.exe`

**Примечание:** При использовании `.exe` файла, созданного через PyInstaller, окружение Conda должно быть активировано заранее, либо нужно модифицировать скрипт для самостоятельной активации. Для простоты рекомендуется использовать предоставленные `.bat` и `.cmd` файлы.

---

## Быстрый старт

1. Убедитесь, что установлен Python 3.8+
2. Создайте и активируйте Conda окружение (рекомендуется):
   ```bash
   conda create -n whisper_env python=3.10
   conda activate whisper_env
   ```
3. Установите зависимости:
   ```bash
   pip install whisperx torch python-dotenv
   ```
4. Создайте файл `.env` с вашим токеном Hugging Face: `HF_TOKEN=ваш_токен`
5. Запустите CLI: `python main.py`

**Примечание:** Если вы столкнулись с ошибкой `ModuleNotFoundError: No module named 'dotenv'`, убедитесь, что пакет `python-dotenv` установлен в вашем окружении:
```bash
pip install python-dotenv
```

## Использование

### Режим по умолчанию (открывает окно выбора файла)

```bash
python main.py
```

Откроется окно выбора файла, где вы можете выбрать аудиофайл. После выбора скрипт выполнит полный цикл транскрипции и сохранит результат.

### Режим командной строки

```bash
python main.py --file audio.mp3
```

Или коротко:

```bash
python main.py -f audio.mp3
```

### С указанием выходного файла

```bash
python main.py -f meeting.wav -o transcript.txt
```

### С выбором модели и языка

```bash
python main.py -f audio.mp3 -m large-v2 -l ru
```

### Без использования GPU

```bash
python main.py -f audio.mp3 -d cpu
```

### Показать справку

```bash
python main.py --help
```

## Параметры командной строки

| Параметр | Короткая форма | Описание | По умолчанию |
|----------|----------------|----------|--------------|
| `--file` | `-f` | Путь к аудиофайлу | Открывает окно выбора |
| `--output` | `-o` | Путь для сохранения результата | Автоматически (рядом с аудио) |
| `--model` | `-m` | Модель Whisper (tiny, base, small, medium, large-v2, large-v3) | `large-v2` |
| `--language` | `-l` | Язык аудио (ru, en, и т.д.) | `ru` |
| `--device` | `-d` | Устройство для вычислений (cuda, cpu, mps) | `cuda` (если доступно) |
| `--batch_size` | `-b` | Размер пакета для транскрипции | `16` |
| `--min_speakers` | — | Минимальное количество спикеров | `1` |
| `--max_speakers` | — | Максимальное количество спикеров | `10` |
| `--help` | `-h` | Показать справку и выйти | — |

## Примеры использования

### Распознать русское аудио на CPU:
```bash
python main.py -f meeting.wav -l ru --device cpu
```

### Использовать модель medium с английским языком:
```bash
python main.py -f interview.mp3 -m medium -l en
```

### Указать точное количество спикеров:
```bash
python main.py -f discussion.wav --min_speakers 2 --max_speakers 2
```

### Сохранить результат в конкретный файл:
```bash
python main.py -f audio.mp3 -o results/transcript.txt
```

## Требования

- Python 3.8+
- Conda (рекомендуется для управления окружением)
- whisperx
- torch
- python-dotenv
- tkinter (для GUI выбора файла)
- FFmpeg (должен быть установлен в системе)

**Установка зависимостей:**
```bash
conda create -n whisper_env python=3.10
conda activate whisper_env
pip install whisperx torch python-dotenv
```

## Настройка

Откройте `config/settings.py` для изменения параметров по умолчанию:
- `WHISPER_MODEL`: Модель Whisper (по умолчанию: large-v2)
- `DEFAULT_LANGUAGE`: Код языка (по умолчанию: ru)
- `BATCH_SIZE`: Размер пакета для транскрипции (по умолчанию: 16)
- `FFMPEG_PATH`: Путь к исполняемому файлу FFmpeg (Windows)
- `FFPROBE_PATH`: Путь к исполняемому файлу FFprobe (Windows)

## Переменные окружения

- `HF_TOKEN`: Токен Hugging Face для диаризации спикеров (обязателен для диаризации)

Создайте файл `.env` в директории проекта:
```
HF_TOKEN=ваш_токен_huggingface
```

## Примечание о FFmpeg

**Windows:** Убедитесь, что FFmpeg установлен в `C:\ffmpeg\bin\` или обновите пути в `config/settings.py`.

**Linux/Mac:** Установите через менеджер пакетов (apt, brew и т.д.) и обновите пути соответствующим образом.

Пример установки:
```bash
# Ubuntu/Debian
sudo apt update && sudo apt install ffmpeg

# macOS
brew install ffmpeg
```

## Структура проекта

```
SpechToText/
├── main.py              # Точка входа CLI (запускайте это для работы)
├── run_whisper.py       # Оригинальный скрипт (сохранён для справки)
├── README.md            # Этот файл
├── config/              # Модуль конфигурации
│   ├── __init__.py      # Экспорт конфигурации
│   └── settings.py      # Настройки приложения и переменные окружения
├── core/                # Основная бизнес-логика
│   ├── __init__.py      # Экспорт ядра
│   └── transcriber.py   # Функции транскрипции и диаризации
└── utils/               # Вспомогательные функции
    ├── __init__.py      # Экспорт утилит
    ├── file_utils.py    # Операции с файлами и GUI диалоги
    └── formatter.py     # Функции форматирования текста
```

## Описание модулей

### config/
Содержит конфигурацию и настройки приложения.
- `settings.py`: Класс Settings со всеми настраиваемыми параметрами, определением устройства и управлением переменными окружения.

### core/
Содержит основную бизнес-логику для обработки речи.
- `transcriber.py`: Функции для транскрипции аудио, выравнивания, диаризации и назначения спикеров. Основная функция `transcribe_and_diarize()` управляет всем пайплайном.

### utils/
Содержит вспомогательные функции.
- `file_utils.py`: Диалог выбора файла (GUI) и генерация пути вывода.
- `formatter.py`: Форматирование транскрипта для группировки реплик по спикерам.

### main.py
Точка входа интерфейса командной строки. Предоставляет парсинг аргументов и организует рабочий процесс с использованием модулей из `config/`, `core/` и `utils/`.

### run_whisper.py
Оригинальный монолитный скрипт (сохранён для справки). Вся функциональность перенесена в модульную структуру выше.

## Как это работает

1. **Выбор файла**: Пользователь выбирает аудиофайл через проводник (или указывает в командной строке)
2. **Транскрипция**: WhisperX преобразует речь в текст с таймкодами
3. **Выравнивание**: Выравнивание текста с аудио для точных временных меток
4. **Диаризация**: Pyannote определяет сегменты с разными спикерами
5. **Сопоставление**: Сегменты транскрипции сопоставляются со спикерами
6. **Форматирование**: Результат группируется по спикерам для удобного чтения
7. **Сохранение**: Итоговый текст сохраняется в `.txt` файл рядом с аудиофайлом

## Важная информация о диаризации спикеров

Для использования диаризации спикеров необходимо:

1. Зарегистрироваться на [Hugging Face](https://huggingface.co/)
2. Принять лицензионные соглашения для моделей:
   - [pyannote/speaker-diarization-3.1](https://huggingface.co/pyannote/speaker-diarization-3.1)
   - [pyannote/segmentation-3.0](https://huggingface.co/pyannote/segmentation-3.0)
3. Создать токен доступа с правами `read`
4. Указать токен в файле `.env` или в настройках

Без токена диаризация не будет работать!

## Устранение неполадок (Troubleshooting)

### Ошибка: `ModuleNotFoundError: No module named 'dotenv'`

Эта ошибка возникает, если пакет `python-dotenv` не установлен в вашем Conda окружении.

**Решение:**
```bash
conda activate whisper_env
pip install python-dotenv
```

Или установите все зависимости сразу:
```bash
conda activate whisper_env
pip install whisperx torch python-dotenv
```

### Предупреждение о системной установке MSMPI (Windows)

**Сообщение:**
```
You seem to have a system wide installation of MSMPI.
Due to the way DLL loading works on windows, system wide installation
will probably overshadow the conda installation.
```

**Причина:** На вашей системе установлена глобальная версия Microsoft MPI (MSMPI), которая конфликтует с версией в Conda окружении.

**Варианты решения:**

**Вариант 1: Удалить системную установку MSMPI (рекомендуется)**
1. Откройте «Панель управления» → «Программы и компоненты»
2. Найдите «Microsoft MPI» или «MS-MPI» в списке программ
3. Удалите его
4. Удалите файлы `C:\Windows\System32\msmpi*.dll` (если существуют)

**⚠️ Внимание:** Это может нарушить работу другого ПО, использующего системную установку MSMPI.

**Вариант 2: Переустановить пакеты в Conda окружении**
```bash
conda activate whisper_env
pip uninstall mpi4py msmpi
pip install mpi4py --force-reinstall
```

**Вариант 3: Игнорировать предупреждение**
Если транскрипция работает корректно, можно игнорировать это предупреждение. Оно не влияет на базовую функциональность WhisperX.

### Ошибка: FFmpeg не найден

Убедитесь, что FFmpeg установлен по пути `C:\ffmpeg\bin\` или обновите пути в `config/settings.py`.

Проверить установку:
```bash
ffmpeg -version
```

Если FFmpeg не установлен, скачайте его с [официального сайта](https://ffmpeg.org/download.html) и распакуйте в `C:\ffmpeg\`.

### Ошибка: Torch/CUDA не работает

Проверьте наличие CUDA:
```python
import torch
print(torch.cuda.is_available())
```

Если возвращает `False`, но у вас есть NVIDIA GPU:
1. Убедитесь, что установлены драйверы NVIDIA
2. Переустановите torch с поддержкой CUDA:
   ```bash
   pip uninstall torch torchvision torchaudio
   pip install torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cu118
   ```

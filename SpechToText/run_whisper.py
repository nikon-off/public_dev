import whisperx
import torch
import gc
import os
import sys
import tkinter as tk
from tkinter import filedialog

# Установка путей к FFmpeg и FFprobe из C:\ffmpeg\bin
os.environ["FFMPEG_PATH"] = r"C:\ffmpeg\bin\ffmpeg.exe"
os.environ["FFPROBE_PATH"] = r"C:\ffmpeg\bin\ffprobe.exe"

# --- УНИВЕРСАЛЬНЫЙ ПОИСК МОДУЛЯ ДИАРИЗАЦИИ ---
def get_diarization_pipeline():
    """
    Пытается найти DiarizationPipeline в разных местах, 
    чтобы код работал на любой версии whisperx.
    """
    # Список возможных путей, где разработчики могут прятать этот класс
    possible_imports = [
        ('whisperx', 'DiarizationPipeline'),
        ('whisperx.diarization', 'DiarizationPipeline'),
        ('whisperx.diarize', 'DiarizationPipeline'),
        ('whisperx.inference', 'DiarizationPipeline'),
    ]
    
    for module_path, class_name in possible_imports:
        try:
            module = __import__(module_path, fromlist=[class_name])
            return getattr(module, class_name)
        except (ImportError, AttributeError):
            continue
            
    raise ImportError(
        "Не удалось найти DiarizationPipeline.\n"
        "Похоже, ваша версия whisperx имеет совершенно другую структуру.\n"
        "Попробуйте обновить библиотеку: pip install --upgrade whisperx"
    )

def select_file():
    """Открывает диалоговое окно для выбора аудиофайла."""
    root = tk.Tk()
    root.withdraw()
    root.attributes("-topmost", True)
    file_types = [
        ("Audio files", "*.mp3 *.wav *.flac *.m4a *.mp4 *.ogg"),
        ("All files", "*.*")
    ]
    file_path = filedialog.askopenfilename(title="Выберите аудиофайл для обработки", filetypes=file_types)
    root.destroy()
    return file_path

def transcribe_and_diarize(
    audio_file, 
    device="cuda", 
    batch_size=16, 
    hf_token=None, 
    compute_type="float16"
):
    """Выполняет транскрипцию и диаризацию аудиофайла."""
    print(f"--- Шаг 1: Распознавание текста (Whisper) ---")
    model = whisperx.load_model("large-v2", device, compute_type=compute_type)
    audio = whisperx.load_audio(audio_file)
    result = model.transcribe(audio, batch_size=batch_size, language="ru")
    
    # Очистка памяти
    del model
    gc.collect()
    torch.cuda.empty_cache()

    print(f"--- Шаг 2: Выравнивание текста ---")
    model_a, align_model = whisperx.load_align_model(language_code=result["language"], device=device)
    
    # Важно: В вашей версии удален параметр batch_size из функции align
    result = whisperx.align(result["segments"], model_a, align_model, audio, device)
    
    del model_a
    del align_model
    gc.collect()
    torch.cuda.empty_cache()

    if hf_token:
        print(f"--- Шаг 3: Определение спикеров (Diarization) ---")
        # Используем наш универсальный поиск класса
        DiarizationPipeline = get_diarization_pipeline()
        diarize_model = DiarizationPipeline(token=hf_token, device=device)
        diarize_segments = diarize_model(audio)
        
        print(f"--- Шаг 4: Объединение данных ---")
        result = whisperx.assign_word_speakers(diarize_segments, result)
    else:
        print("--- ВНИМАНИЕ: HF Token не предоставлен. Голоса не будут разделены! ---")
        for segment in result["segments"]:
            segment["speaker"] = "UNKNOWN"

    return result

def format_transcript(result):
    """Группирует последовательные реплики одного и того же спикера."""
    if not result or "segments" not in result or not result["segments"]:
        return "Текст не найден."

    formatted_lines = []
    current_speaker = None
    current_text_parts = []

    for segment in result["segments"]:
        speaker = segment.get("speaker", "Неизвестный")
        text = segment["text"].strip()
        
        if not text:
            continue

        if speaker == current_speaker:
            current_text_parts.append(text)
        else:
            if current_speaker is not None:
                line = f"{current_speaker}: {' '.join(current_text_parts)}"
                formatted_lines.append(line)
            
            current_speaker = speaker
            current_text_parts = [text]

    if current_speaker is not None and current_text_parts:
        line = f"{current_speaker}: {' '.join(current_text_parts)}"
        formatted_lines.append(line)

    return "\n".join(formatted_lines)

# --- НАСТРОЙКИ ---
# Вставьте ваш токен от Hugging Face здесь!
from dotenv import load_dotenv

load_dotenv()  # загружает .env из текущей директории
HF_TOKEN = os.environ.get("HF_TOKEN")

DEVICE = "cuda" if torch.cuda.is_available() else "cpu"
COMPUTE_TYPE = "float16" if DEVICE == "cuda" else "int8"

if __name__ == "__main__":
    # 1. Выбор файла через Проводник
    audio_path = select_file()

    if not audio_path:
        print("Выбор файла отменено пользователем.")
        sys.exit()

    print(f"Выбран файл: {audio_path}")

    try:
        # 2. Запуск процесса
        result = transcribe_and_diarize(
            audio_file=audio_path,
            device=DEVICE,
            hf_token=HF_TOKEN,
            compute_type=COMPUTE_TYPE
        )
        
        # 3. Форматирование текста
        transcript = format_transcript(result)
        
        # 4. Сохранение в ту же папку, где лежит аудио
        base_path = os.path.splitext(audio_path)[0]
        save_path = base_path + "_transcript.txt"

        with open(save_path, "w", encoding="utf-8") as f:
            f.write(transcript)
        
        print(f"\n[✅] ГОТОВО!")
        print(f"[📄] Результат сохранен в: {save_path}")
        print(f"\n--- ПРЕДПРОСМОТР (первые 500 симв.) ---")
        print(transcript[:500] + "...")

    except Exception as e:
        print(f"\n[❌] Произошла ошибка: {e}")
        import traceback
        traceback.print_exc()

#!/usr/bin/env python3
"""
Command-line interface for Speech-to-Text transcription.

This script provides a CLI entry point for transcribing audio files
with speaker diarization using WhisperX.

By default, opens a file dialog to select an audio file.
Use --no-gui flag to disable GUI and require --file argument.

Usage:
    python main.py                              # Open file dialog (default behavior)
    python main.py --file audio.mp3             # Transcribe specific file
    python main.py -f audio.wav -o transcript.txt
    python main.py --no-gui -f meeting.mp4 --model large-v2 --language ru
    python main.py -f audio.wav --no-diarize    # Только транскрипция (без диаризации)

Examples:
    $ python main.py
    (opens file selection dialog)

    $ python main.py -f audio.mp3
    (transcribes audio.mp3 and saves result)

    $ python main.py -f audio.wav -o my_transcript.txt
    (transcribes and saves to custom output file)
"""

import sys
import os
import argparse

# Add parent directory to path for imports
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from config import Settings
from core import transcribe_and_diarize
from core.memory import MemoryGovernor, configure_allocator
from utils import select_file, get_output_path, format_transcript
from utils.logger import get_logger, setup_logger


def parse_args():
    """Parse command line arguments."""
    parser = argparse.ArgumentParser(
        description="Speech-to-Text transcription with speaker diarization",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__
    )

    parser.add_argument(
        "--file", "-f",
        type=str,
        default=None,
        help="Path to the audio file to transcribe"
    )

    parser.add_argument(
        "--output", "-o",
        type=str,
        default=None,
        help="Output file path (default: <input_file>_transcript.txt)"
    )

    parser.add_argument(
        "--no-gui",
        action="store_true",
        help="Disable GUI file dialog, require --file argument"
    )

    parser.add_argument(
        "--model", "-m",
        type=str,
        default=Settings.WHISPER_MODEL,
        help=f"Whisper model to use (default: {Settings.WHISPER_MODEL})"
    )

    parser.add_argument(
        "--language", "-l",
        type=str,
        default=Settings.DEFAULT_LANGUAGE,
        help=f"Language code for transcription (default: {Settings.DEFAULT_LANGUAGE})"
    )

    parser.add_argument(
        "--batch-size", "-b",
        type=int,
        default=Settings.BATCH_SIZE,
        help=f"Batch size for transcription (default: {Settings.BATCH_SIZE})"
    )

    parser.add_argument(
        "--device", "-d",
        type=str,
        default=None,
        help="Device to use (cuda/cpu). Auto-detected if not specified."
    )

    parser.add_argument(
        "--compute-type", "-c",
        type=str,
        default=None,
        help="Compute type (float16/int8). Auto-detected if not specified."
    )

    parser.add_argument(
        "--max-ram",
        type=int,
        default=None,
        help=f"Жёсткий бюджет RAM процесса в МБ (default: {Settings.MAX_RAM_MB})"
    )

    parser.add_argument(
        "--chunk-length",
        type=int,
        default=Settings.CHUNK_LENGTH_SEC,
        help=f"Длина куска аудио в секундах (default: {Settings.CHUNK_LENGTH_SEC})"
    )

    parser.add_argument(
        "--no-chunk",
        action="store_true",
        help="Отключить чанкированный транскрипт (обрабатывать целиком)"
    )

    parser.add_argument(
        "--no-diarize",
        action="store_true",
        help="Отключить диаризацию (для дорожек с одним голосом) — экономит память"
    )

    return parser.parse_args()


def main():
    """Main entry point for the CLI."""
    args = parse_args()

    # Настройка логирования: консоль + файл logs/whisper_*.txt
    setup_logger(Settings.LOG_DIR)
    logger = get_logger()

    # Setup environment
    Settings.setup_environment()
    configure_allocator()

    # Управление памятью
    max_ram_mb = args.max_ram if args.max_ram is not None else Settings.MAX_RAM_MB
    governor = MemoryGovernor(
        max_ram_mb=max_ram_mb,
        max_vram_fraction=Settings.MAX_VRAM_FRACTION,
    )

    # Determine compute type
    compute_type = args.compute_type if args.compute_type else Settings.get_compute_type(args.device or "cuda")

    # Determine device (авто-fallback на CPU, если VRAM не хватает под модель)
    required_vram_gb = Settings.estimate_model_vram_gb(args.model, compute_type)
    device = governor.choose_device(args.device, required_vram_gb=required_vram_gb)
    logger.info(f"Устройство: {device}, модель: {args.model}, compute: {compute_type}, "
                f"RAM-бюджет: {max_ram_mb} МБ")
    logger.info(governor.report("старт"))

    # Get Hugging Face token
    hf_token = Settings.HF_TOKEN

    if not hf_token:
        logger.warning("ВНИМАНИЕ: Токен HF_TOKEN не найден! Диаризация будет пропущена.")
        logger.warning("Создайте файл .env рядом со скриптом и добавьте туда: HF_TOKEN=ваш_новый_токен")

    # Get input file - always use GUI if no file specified (unless --no-gui is explicitly set)
    audio_path = args.file

    if not audio_path and not args.no_gui:
        # Use GUI to select file
        audio_path = select_file()

    if not audio_path:
        if args.no_gui:
            logger.error("Ошибка: Необходимо указать путь к файлу с помощью --file или использовать GUI.")
        else:
            logger.error("Выбор файла отменено пользователем.")
        sys.exit(1)

    logger.info(f"Выбран файл: {audio_path}")

    try:
        # Run transcription and diarization
        result = transcribe_and_diarize(
            audio_file=audio_path,
            device=device,
            batch_size=args.batch_size,
            hf_token=hf_token,
            compute_type=compute_type,
            model_name=args.model,
            language=args.language,
            enable_diarization=(not args.no_diarize),
            enable_chunking=(not args.no_chunk),
            chunk_len_sec=args.chunk_length,
            overlap_sec=Settings.CHUNK_OVERLAP_SEC,
            governor=governor,
        )

        # Format transcript
        transcript = format_transcript(result)

        # Determine output path
        save_path = args.output if args.output else get_output_path(audio_path)

        # Save transcript
        with open(save_path, "w", encoding="utf-8") as f:
            f.write(transcript)

        logger.info(f"[✅] ГОТОВО!")
        logger.info(f"[📄] Результат сохранен в: {save_path}")
        logger.info(f"\n--- ПРЕДПРОСМОТР (первые 500 симв.) ---")
        logger.info(transcript[:500] + "...")

    except Exception as e:
        logger.exception(f"[❌] Произошла ошибка: {e}")
        sys.exit(1)


if __name__ == "__main__":
    main()

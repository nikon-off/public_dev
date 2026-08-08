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
from utils import select_file, get_output_path, format_transcript


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
    
    return parser.parse_args()


def main():
    """Main entry point for the CLI."""
    args = parse_args()
    
    # Setup environment
    Settings.setup_environment()
    
    # Determine device and compute type
    device = args.device if args.device else Settings.get_device()
    compute_type = args.compute_type if args.compute_type else Settings.get_compute_type(device)
    
    # Get Hugging Face token
    hf_token = Settings.HF_TOKEN
    
    if not hf_token:
        print("⚠️ ВНИМАНИЕ: Токен HF_TOKEN не найден! Диаризация будет пропущена.")
        print("Создайте файл .env рядом со скриптом и добавьте туда: HF_TOKEN=ваш_новый_токен")
    
    # Get input file - always use GUI if no file specified (unless --no-gui is explicitly set)
    audio_path = args.file
    
    if not audio_path and not args.no_gui:
        # Use GUI to select file
        audio_path = select_file()
    
    if not audio_path:
        if args.no_gui:
            print("Ошибка: Необходимо указать путь к файлу с помощью --file или использовать GUI.")
        else:
            print("Выбор файла отменено пользователем.")
        sys.exit(1)
    
    print(f"Выбран файл: {audio_path}")
    
    try:
        # Run transcription and diarization
        result = transcribe_and_diarize(
            audio_file=audio_path,
            device=device,
            batch_size=args.batch_size,
            hf_token=hf_token,
            compute_type=compute_type,
            model_name=args.model,
            language=args.language
        )
        
        # Format transcript
        transcript = format_transcript(result)
        
        # Determine output path
        save_path = args.output if args.output else get_output_path(audio_path)
        
        # Save transcript
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
        sys.exit(1)


if __name__ == "__main__":
    main()

# Speech-to-Text Project using WhisperX

This project provides tools for transcribing audio files with speaker diarization.

## Quick Start

1. Make sure you have Python 3.8+ installed
2. Install dependencies: `pip install whisperx torch python-dotenv`
3. Create a `.env` file with your Hugging Face token: `HF_TOKEN=your_token_here`
4. Run the CLI: `python main.py`

## Usage

### Default behavior (opens file dialog)
```bash
python main.py
```
This will open a file selection dialog where you can choose an audio file. After selection, the script will perform the full transcription cycle and save the result.

### Command line mode
```bash
python main.py --file audio.mp3
```

### With custom output file
```bash
python main.py -f meeting.wav -o transcript.txt
```

### Specify model and language
```bash
python main.py -f audio.mp3 -m large-v2 -l ru
```

### Without GPU
```bash
python main.py -f audio.mp3 -d cpu
```

### Show help
```bash
python main.py --help
```

## Requirements

- Python 3.8+
- whisperx
- torch
- python-dotenv
- tkinter (for GUI file picker)

## Configuration

Edit `config/settings.py` to change default values:
- `WHISPER_MODEL`: Whisper model to use (default: large-v2)
- `DEFAULT_LANGUAGE`: Language code (default: ru)
- `BATCH_SIZE`: Batch size for transcription (default: 16)
- `FFMPEG_PATH`: Path to FFmpeg executable (Windows)
- `FFPROBE_PATH`: Path to FFprobe executable (Windows)

## Environment Variables

- `HF_TOKEN`: Hugging Face token for speaker diarization (required for diarization)

Create a `.env` file in the project directory:
```
HF_TOKEN=your_huggingface_token_here
```

## Note on FFmpeg

On Windows, ensure FFmpeg is installed at `C:\ffmpeg\bin\` or update the paths in `config/settings.py`.
On Linux/Mac, install via package manager (apt, brew, etc.) and update paths accordingly.

## Project Structure

```
SpechToText/
├── main.py              # CLI entry point (run this to use the tool)
├── run_whisper.py       # Original script (kept for reference)
├── README.md            # This file
├── config/              # Configuration module
│   ├── __init__.py      # Config package exports
│   └── settings.py      # Application settings and environment setup
├── core/                # Core business logic
│   ├── __init__.py      # Core package exports
│   └── transcriber.py   # Transcription and diarization functions
└── utils/               # Utility functions
    ├── __init__.py      # Utils package exports
    ├── file_utils.py    # File operations and GUI dialogs
    └── formatter.py     # Text formatting functions
```

## Module Descriptions

### config/
Contains application configuration and settings.
- `settings.py`: Settings class with all configurable parameters, device detection, and environment variable management.

### core/
Contains the main business logic for speech processing.
- `transcriber.py`: Functions for audio transcription, alignment, diarization, and speaker assignment. The main function `transcribe_and_diarize()` orchestrates the entire pipeline.

### utils/
Contains utility/helper functions.
- `file_utils.py`: File selection dialog (GUI) and output path generation.
- `formatter.py`: Transcript formatting to group utterances by speaker.

### main.py
Command-line interface entry point. Provides argument parsing and orchestrates the workflow using modules from `config/`, `core/`, and `utils/`.

### run_whisper.py
Original monolithic script (preserved for reference). All functionality has been moved to the modular structure above.

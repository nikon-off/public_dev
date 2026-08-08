"""Utility functions for file operations and UI."""

import tkinter as tk
from tkinter import filedialog


def select_file(file_types=None) -> str:
    """
    Open a file dialog to select an audio file.
    
    Args:
        file_types: List of file type tuples for the dialog.
                   Defaults to common audio formats.
    
    Returns:
        Path to the selected file, or empty string if cancelled.
    """
    if file_types is None:
        file_types = [
            ("Audio files", "*.mp3 *.wav *.flac *.m4a *.mp4 *.ogg"),
            ("All files", "*.*")
        ]
    
    root = tk.Tk()
    root.withdraw()
    root.attributes("-topmost", True)
    
    file_path = filedialog.askopenfilename(
        title="Выберите аудиофайл для обработки",
        filetypes=file_types
    )
    
    root.destroy()
    return file_path


def get_output_path(audio_path: str, suffix: str = "_transcript.txt") -> str:
    """
    Generate output file path based on input audio path.
    
    Args:
        audio_path: Path to the input audio file.
        suffix: Suffix to append to the base filename.
    
    Returns:
        Path for the output file.
    """
    base_path = audio_path.rsplit('.', 1)[0]
    return base_path + suffix

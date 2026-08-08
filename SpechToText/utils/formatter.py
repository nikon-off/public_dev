"""Utility functions for text formatting."""

from typing import List, Dict, Any


def format_transcript(result: Dict[str, Any]) -> str:
    """
    Group consecutive utterances from the same speaker.
    
    Args:
        result: Dictionary containing transcription segments.
    
    Returns:
        Formatted transcript string with speaker labels.
    """
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

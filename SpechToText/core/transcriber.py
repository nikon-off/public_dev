"""Core transcription and diarization logic."""

import gc
from typing import Dict, Any, Optional

import torch
import whisperx


def get_diarization_pipeline():
    """
    Try to find DiarizationPipeline in different locations.
    
    This ensures compatibility across different versions of whisperx.
    
    Returns:
        DiarizationPipeline class.
        
    Raises:
        ImportError: If DiarizationPipeline cannot be found.
    """
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


def transcribe_audio(audio_file: str, model_name: str, device: str, 
                     batch_size: int, language: str, compute_type: str) -> Dict[str, Any]:
    """
    Transcribe audio using Whisper model.
    
    Args:
        audio_file: Path to the audio file.
        model_name: Name of the Whisper model to use.
        device: Device to run inference on ('cuda' or 'cpu').
        batch_size: Batch size for transcription.
        language: Language code for transcription.
        compute_type: Compute type for model inference.
    
    Returns:
        Dictionary containing transcription results.
    """
    print("--- Шаг 1: Распознавание текста (Whisper) ---")
    model = whisperx.load_model(model_name, device, compute_type=compute_type)
    audio = whisperx.load_audio(audio_file)
    result = model.transcribe(audio, batch_size=batch_size, language=language)
    
    # Cleanup memory
    del model
    gc.collect()
    if torch.cuda.is_available():
        torch.cuda.empty_cache()
    
    return result


def align_transcription(result: Dict[str, Any], audio: Any, device: str) -> Dict[str, Any]:
    """
    Align transcription segments with audio timestamps.
    
    Args:
        result: Transcription result dictionary.
        audio: Loaded audio data.
        device: Device to run alignment on.
    
    Returns:
        Dictionary with aligned segments.
    """
    print("--- Шаг 2: Выравнивание текста ---")
    model_a, align_model = whisperx.load_align_model(
        language_code=result["language"], 
        device=device
    )
    
    result = whisperx.align(
        result["segments"], 
        model_a, 
        align_model, 
        audio, 
        device
    )
    
    del model_a
    del align_model
    gc.collect()
    if torch.cuda.is_available():
        torch.cuda.empty_cache()
    
    return result


def perform_diarization(audio: Any, device: str, hf_token: Optional[str] = None) -> Any:
    """
    Perform speaker diarization on audio.
    
    Args:
        audio: Loaded audio data.
        device: Device to run diarization on.
        hf_token: Hugging Face token for diarization model.
    
    Returns:
        Diarization segments.
    """
    print("--- Шаг 3: Определение спикеров (Diarization) ---")
    DiarizationPipeline = get_diarization_pipeline()
    diarize_model = DiarizationPipeline(token=hf_token, device=device)
    diarize_segments = diarize_model(audio)
    
    return diarize_segments


def assign_speakers(diarize_segments: Any, result: Dict[str, Any]) -> Dict[str, Any]:
    """
    Assign speaker labels to transcription segments.
    
    Args:
        diarize_segments: Diarization segments.
        result: Transcription result dictionary.
    
    Returns:
        Result dictionary with speaker assignments.
    """
    print("--- Шаг 4: Объединение данных ---")
    result = whisperx.assign_word_speakers(diarize_segments, result)
    return result


def transcribe_and_diarize(
    audio_file: str,
    device: str = "cuda",
    batch_size: int = 16,
    hf_token: Optional[str] = None,
    compute_type: str = "float16",
    model_name: str = "large-v2",
    language: str = "ru"
) -> Dict[str, Any]:
    """
    Perform complete transcription and diarization pipeline.
    
    Args:
        audio_file: Path to the audio file.
        device: Device to run processing on.
        batch_size: Batch size for transcription.
        hf_token: Hugging Face token for diarization.
        compute_type: Compute type for model inference.
        model_name: Name of the Whisper model.
        language: Language code for transcription.
    
    Returns:
        Dictionary containing final transcription with speaker labels.
    """
    # Step 1: Transcription
    result = transcribe_audio(
        audio_file=audio_file,
        model_name=model_name,
        device=device,
        batch_size=batch_size,
        language=language,
        compute_type=compute_type
    )
    
    # Load audio for alignment and diarization
    audio = whisperx.load_audio(audio_file)
    
    # Step 2: Alignment
    result = align_transcription(result=result, audio=audio, device=device)
    
    # Step 3 & 4: Diarization and speaker assignment
    if hf_token:
        diarize_segments = perform_diarization(
            audio=audio,
            device=device,
            hf_token=hf_token
        )
        result = assign_speakers(diarize_segments, result)
    else:
        print("--- ВНИМАНИЕ: HF Token не предоставлен. Голоса не будут разделены! ---")
        for segment in result["segments"]:
            segment["speaker"] = "UNKNOWN"
    
    return result

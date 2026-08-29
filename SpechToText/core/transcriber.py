"""Core transcription and diarization logic."""

import gc
from typing import Any, Dict, List, Optional

import torch
import whisperx

from utils.logger import get_logger

SAMPLE_RATE = 16000  # фиксированная частота дискретизации WhisperX


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


def _split_audio(audio, chunk_len_sec, overlap_sec):
    """
    Разбить аудио на куски с перекрытием.

    Args:
        audio: numpy-массив аудио (16 кГц, моно).
        chunk_len_sec: длина куска в секундах.
        overlap_sec: перекрытие кусков в секундах.

    Returns:
        (chunks, starts) — список кусков и список их абсолютных стартов (сек).
    """
    n = len(audio)
    chunk_len = int(chunk_len_sec * SAMPLE_RATE)
    hop = int((chunk_len_sec - overlap_sec) * SAMPLE_RATE)
    if hop <= 0:
        hop = chunk_len

    chunks: List[Any] = []
    starts: List[float] = []
    start = 0
    while start < n:
        end = min(start + chunk_len, n)
        chunks.append(audio[start:end])
        starts.append(start / SAMPLE_RATE)
        if end >= n:
            break
        start += hop
    return chunks, starts


def _transcribe_chunked(model, audio, batch_size, language,
                        chunk_len_sec, overlap_sec, governor=None):
    """
    Транскрипция длинного аудио по кускам.

    Обрабатывает аудио частями, освобождая промежуточные тензоры, что
    ограничивает пик потребления памяти. Время сегментов сдвигается на
    абсолютный старт куска; сегменты из зоны перекрытия отбрасываются
    (кроме первого куска).

    Returns:
        Результат в формате {'language': ..., 'segments': [...]}.
    """
    logger = get_logger()
    chunks, starts = _split_audio(audio, chunk_len_sec, overlap_sec)
    logger.info(f"Транскрипция: {len(chunks)} куск(ов) по ~{chunk_len_sec}с")

    language_detected = language or "ru"
    all_segments: List[Dict[str, Any]] = []

    for idx, (chunk_audio, chunk_start) in enumerate(zip(chunks, starts)):
        logger.info(f"  кусок {idx + 1}/{len(chunks)} (начало {chunk_start:.1f}с)")
        chunk_result = model.transcribe(
            chunk_audio, batch_size=batch_size, language=language
        )
        language_detected = chunk_result.get("language") or language_detected

        for s in chunk_result.get("segments", []):
            segment = dict(s)
            segment["start"] = segment.get("start", 0.0) + chunk_start
            segment["end"] = segment.get("end", 0.0) + chunk_start
            # Дедупликация на границах: пропускаем сегменты из перекрытия.
            if idx > 0 and segment["start"] < chunk_start + overlap_sec:
                continue
            all_segments.append(segment)

        # Освобождаем результаты куска сразу после обработки.
        del chunk_result
        gc.collect()
        if governor is not None and torch.cuda.is_available():
            torch.cuda.empty_cache()

    return {"language": language_detected, "segments": all_segments}


def transcribe_audio(model_name, device, batch_size, language, compute_type,
                     audio=None, audio_file=None,
                     chunk_len_sec=90, overlap_sec=5,
                     enable_chunking=True, governor=None) -> Dict[str, Any]:
    """
    Transcribe audio using Whisper model (опционально — по кускам).

    Args:
        model_name: Name of the Whisper model to use.
        device: Device to run inference on ('cuda' or 'cpu').
        batch_size: Batch size for transcription.
        language: Language code for transcription.
        compute_type: Compute type for model inference.
        audio: Уже загруженное аудио (numpy-массив). Если не задано — грузится
               из audio_file.
        audio_file: Path to the audio file (используется, если audio не передан).
        chunk_len_sec: Длина куска для чанкинга (сек).
        overlap_sec: Перекрытие кусков (сек).
        enable_chunking: Включить ли чанкинг.
        governor: MemoryGovernor для контроля/очистки памяти.

    Returns:
        Dictionary containing transcription results.
    """
    logger = get_logger()
    logger.info("--- Шаг 1: Распознавание текста (Whisper) ---")

    model = whisperx.load_model(model_name, device, compute_type=compute_type)
    if audio is None:
        audio = whisperx.load_audio(audio_file)

    if enable_chunking and len(audio) > (chunk_len_sec + overlap_sec) * SAMPLE_RATE:
        result = _transcribe_chunked(
            model, audio, batch_size, language,
            chunk_len_sec, overlap_sec, governor=governor,
        )
    else:
        result = model.transcribe(audio, batch_size=batch_size, language=language)

    # Cleanup memory: модель и ссылка на аудио здесь больше не нужны.
    del model
    del audio
    gc.collect()
    if torch.cuda.is_available():
        torch.cuda.empty_cache()

    if governor is not None:
        logger.info(governor.report("после транскрипции"))
    return result


def align_transcription(result, audio, device, governor=None) -> Dict[str, Any]:
    """
    Align transcription segments with audio timestamps.

    Args:
        result: Transcription result dictionary.
        audio: Loaded audio data.
        device: Device to run alignment on.
        governor: MemoryGovernor для контроля/очистки памяти.

    Returns:
        Dictionary with aligned segments.
    """
    logger = get_logger()
    logger.info("--- Шаг 2: Выравнивание текста ---")
    model_a, align_model = whisperx.load_align_model(
        language_code=result["language"],
        device=device,
    )

    result = whisperx.align(
        result["segments"],
        model_a,
        align_model,
        audio,
        device,
    )

    del model_a
    del align_model
    gc.collect()
    if torch.cuda.is_available():
        torch.cuda.empty_cache()

    if governor is not None:
        logger.info(governor.report("после выравнивания"))
    return result


def perform_diarization(audio, device, hf_token=None) -> Any:
    """
    Perform speaker diarization on audio.

    Args:
        audio: Loaded audio data.
        device: Device to run diarization on.
        hf_token: Hugging Face token for diarization model.

    Returns:
        Diarization segments.
    """
    logger = get_logger()
    logger.info("--- Шаг 3: Определение спикеров (Diarization) ---")
    DiarizationPipeline = get_diarization_pipeline()
    diarize_model = DiarizationPipeline(token=hf_token, device=device)
    diarize_segments = diarize_model(audio)
    del diarize_model
    gc.collect()
    if torch.cuda.is_available():
        torch.cuda.empty_cache()
    return diarize_segments


def assign_speakers(diarize_segments, result) -> Dict[str, Any]:
    """
    Assign speaker labels to transcription segments.

    Args:
        diarize_segments: Diarization segments.
        result: Transcription result dictionary.

    Returns:
        Result dictionary with speaker assignments.
    """
    logger = get_logger()
    logger.info("--- Шаг 4: Объединение данных ---")
    result = whisperx.assign_word_speakers(diarize_segments, result)
    return result


def _mark_unknown_speakers(result: Dict[str, Any]) -> Dict[str, Any]:
    """Пометить все сегменты спикером UNKNOWN (если диаризация отключена)."""
    for segment in result["segments"]:
        segment["speaker"] = "UNKNOWN"
    return result


def transcribe_and_diarize(
    audio_file: str,
    device: str = "cuda",
    batch_size: int = 4,
    hf_token: Optional[str] = None,
    compute_type: str = "int8",
    model_name: str = "large-v2",
    language: str = "ru",
    enable_diarization: bool = True,
    enable_chunking: bool = True,
    chunk_len_sec: int = 90,
    overlap_sec: int = 5,
    governor=None,
) -> Dict[str, Any]:
    """
    Perform complete transcription and (опционально) diarization pipeline.

    Args:
        audio_file: Path to the audio file.
        device: Device to run processing on.
        batch_size: Batch size for transcription.
        hf_token: Hugging Face token for diarization.
        compute_type: Compute type for model inference.
        model_name: Name of the Whisper model.
        language: Language code for transcription.
        enable_diarization: Выполнять ли диаризацию (для дорожек с одним голосом).
        enable_chunking: Включить ли чанкированный транскрипт.
        chunk_len_sec: Длина куска (сек).
        overlap_sec: Перекрытие кусков (сек).
        governor: MemoryGovernor для контроля/очистки памяти.

    Returns:
        Dictionary containing final transcription with speaker labels.
    """
    logger = get_logger()

    # Единая загрузка аудио один раз — используется всеми фазами.
    audio = whisperx.load_audio(audio_file)

    # Step 1: Transcription (передаём уже загруженное аудио)
    result = transcribe_audio(
        model_name=model_name,
        device=device,
        batch_size=batch_size,
        language=language,
        compute_type=compute_type,
        audio=audio,
        chunk_len_sec=chunk_len_sec,
        overlap_sec=overlap_sec,
        enable_chunking=enable_chunking,
        governor=governor,
    )

    # Step 2: Alignment
    result = align_transcription(result=result, audio=audio, device=device,
                                 governor=governor)

    # Step 3 & 4: Diarization (опциональна)
    if enable_diarization and hf_token:
        diarize_segments = perform_diarization(audio=audio, device=device,
                                               hf_token=hf_token)
        result = assign_speakers(diarize_segments, result)
    else:
        if not enable_diarization:
            logger.info("Диаризация отключена — спикер помечается как UNKNOWN.")
        else:
            logger.info("HF Token не предоставлен. Голоса не будут разделены!")
        result = _mark_unknown_speakers(result)

    # Аудио больше не нужно.
    del audio
    gc.collect()
    if torch.cuda.is_available():
        torch.cuda.empty_cache()

    if governor is not None:
        logger.info(governor.report("итог"))

    return result

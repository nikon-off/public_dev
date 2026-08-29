"""Управление памятью: контроль VRAM/RAM, авто-выбор устройства, очистка."""

import gc
import os

import torch

try:
    import psutil
except ImportError:  # psutil опционален: без него отчёты по RAM недоступны
    psutil = None


def configure_allocator(max_split_size_mb=64):
    """
    Настроить CUDA-аллокатор PyTorch до загрузки моделей.

    Снижает фрагментацию и кэш-перерасход видеопамяти. Важно вызывать
    ДО импорта/инициализации torch (переменная окружения читается при старте).
    """
    conf = os.environ.get("PYTORCH_CUDA_ALLOC_CONF", "")
    if "max_split_size_mb" not in conf:
        parts = [p for p in conf.split(",") if p]
        parts.append(f"max_split_size_mb:{max_split_size_mb}")
        if not any("garbage_collection_threshold" in p for p in parts):
            parts.insert(0, "garbage_collection_threshold:0.8")
        os.environ["PYTORCH_CUDA_ALLOC_CONF"] = ",".join(parts)


class MemoryGovernor:
    """
    Единая точка управления памятью процесса.

    - жёсткий потолок VRAM через set_per_process_memory_fraction;
    - авто-выбор устройства (cuda/cpu) с учётом свободной VRAM;
    - отчёт по RSS и VRAM для контроля пиков;
    - принудительная очистка кэша между фазами обработки.
    """

    def __init__(self, max_ram_mb=None, max_vram_fraction=0.85):
        self.max_ram_mb = max_ram_mb
        self.max_vram_fraction = max_vram_fraction
        self.cuda_available = torch.cuda.is_available()

        if self.cuda_available and max_vram_fraction:
            try:
                torch.cuda.set_per_process_memory_fraction(max_vram_fraction)
            except Exception:
                pass

    # --- выбор устройства ---

    def choose_device(self, force=None, required_vram_gb=None):
        """
        Выбрать устройство для вычислений.

        Args:
            force: Явное устройство ('cuda'/'cpu'). Если задано — возвращается.
            required_vram_gb: Оценочный объём VRAM под модель (веса + активации).
                              Если свободной VRAM меньше — вернётся 'cpu'.

        Returns:
            'cuda' или 'cpu'.
        """
        if force:
            return force
        if not self.cuda_available:
            return "cpu"
        if required_vram_gb:
            try:
                free, _total = torch.cuda.mem_get_info()
                if (free / (1024 ** 3)) < required_vram_gb:
                    return "cpu"
            except Exception:
                pass
        return "cuda"

    # --- метрики ---

    def rss_mb(self):
        """Резидентная память процесса в МБ (или None)."""
        if psutil is None:
            return None
        try:
            return psutil.Process().memory_info().rss / (1024 * 1024)
        except Exception:
            return None

    def vram_used_mb(self):
        """Используемая видеопамять в МБ (или None)."""
        if not self.cuda_available:
            return None
        try:
            return torch.cuda.memory_allocated() / (1024 * 1024)
        except Exception:
            return None

    def vram_reserved_mb(self):
        """Зарезервированная видеопамять в МБ (или None)."""
        if not self.cuda_available:
            return None
        try:
            return torch.cuda.memory_reserved() / (1024 * 1024)
        except Exception:
            return None

    # --- отчёт и контроль ---

    def report(self, label=""):
        """
        Строка-отчёт по памяти: RAM + VRAM used/reserved.
        Используется для логирования и контроля пиков.
        """
        parts = [f"[MEM] {label}" if label else "[MEM]"]
        rss = self.rss_mb()
        if rss is not None:
            parts.append(f"RAM={rss:.0f}MB")
        if self.cuda_available:
            used = self.vram_used_mb()
            reserved = self.vram_reserved_mb()
            if used is not None:
                parts.append(f"VRAM_used={used:.0f}MB")
            if reserved is not None:
                parts.append(f"VRAM_res={reserved:.0f}MB")
        return " ".join(parts)

    def enforce_budget(self):
        """
        Проверить бюджет RAM. При превышении — выполнить очистку.

        Returns:
            True, если бюджет нарушен (даже после очистки); иначе False.
        """
        rss = self.rss_mb()
        if self.max_ram_mb and rss is not None and rss > self.max_ram_mb:
            self.cleanup()
            rss_after = self.rss_mb()
            return rss_after is not None and rss_after > self.max_ram_mb
        return False

    # --- очистка ---

    def cleanup(self):
        """Принудительная очистка: сборщик мусора + освобождение кэша CUDA."""
        gc.collect()
        if self.cuda_available:
            torch.cuda.empty_cache()
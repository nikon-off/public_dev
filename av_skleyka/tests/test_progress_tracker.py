"""Unit tests for progress_tracker module."""

import pytest
from unittest.mock import patch, MagicMock, call

from core.progress_tracker import ProgressTracker


class TestProgressTracker:
    """Tests for ProgressTracker class."""

    @patch('core.progress_tracker.tqdm')
    def test_init_creates_progress_bar(self, mock_tqdm):
        """Test that __init__ creates a tqdm progress bar."""
        total_seconds = 120.0

        tracker = ProgressTracker(total_seconds)

        mock_tqdm.assert_called_once()
        call_kwargs = mock_tqdm.call_args[1]
        assert call_kwargs['total'] == total_seconds
        assert call_kwargs['unit'] == 's'
        assert call_kwargs['desc'] == 'Синхронизация'
        assert call_kwargs['ncols'] == 100

    @patch('core.progress_tracker.tqdm')
    def test_update_with_valid_time_string(self, mock_tqdm):
        """Test that update correctly parses out_time_ms and updates progress."""
        # Setup mock progress bar
        mock_pbar = MagicMock()
        mock_pbar.n = 0  # Current progress is 0
        mock_tqdm.return_value = mock_pbar

        tracker = ProgressTracker(120.0)

        # Simulate ffmpeg output with time in milliseconds
        line = "out_time_ms=5000"
        tracker.update(line)

        # Verify pbar.update was called with correct delta (5.0 seconds)
        mock_pbar.update.assert_called_once_with(5.0)

    @patch('core.progress_tracker.tqdm')
    def test_update_with_complex_line(self, mock_tqdm):
        """Test that update works with complex ffmpeg output lines."""
        mock_pbar = MagicMock()
        mock_pbar.n = 0
        mock_tqdm.return_value = mock_pbar

        tracker = ProgressTracker(120.0)

        # Line with additional content
        line = "frame=  25 fps=0.0 q=0.0 size=       0kB time=00:00:05.00 bitrate=N/A speed=0.0x out_time_ms=5000"
        tracker.update(line)

        mock_pbar.update.assert_called_once_with(5.0)

    @patch('core.progress_tracker.tqdm')
    def test_update_with_invalid_string_does_not_crash(self, mock_tqdm):
        """Test that update handles invalid strings gracefully without crashing."""
        mock_pbar = MagicMock()
        mock_pbar.n = 0
        mock_tqdm.return_value = mock_pbar

        tracker = ProgressTracker(120.0)

        # Invalid line without out_time_ms
        invalid_lines = [
            "",
            "some random text",
            "frame=25",
            "out_time_ms=",
            "out_time_ms=abc",  # Non-numeric value
        ]

        for line in invalid_lines:
            # Should not raise any exception
            tracker.update(line)

        # Verify no unexpected calls were made for invalid lines
        # (update should silently ignore invalid lines)

    @patch('core.progress_tracker.tqdm')
    def test_update_incremental_progress(self, mock_tqdm):
        """Test that update correctly calculates delta for incremental progress."""
        mock_pbar = MagicMock()
        mock_pbar.n = 5.0  # Already at 5 seconds
        mock_tqdm.return_value = mock_pbar

        tracker = ProgressTracker(120.0)

        # New time is 10 seconds (10000 ms)
        line = "out_time_ms=10000"
        tracker.update(line)

        # Delta should be 10.0 - 5.0 = 5.0
        mock_pbar.update.assert_called_once_with(5.0)

    @patch('core.progress_tracker.tqdm')
    def test_update_negative_delta_ignored(self, mock_tqdm):
        """Test that negative delta (going backwards) is ignored."""
        mock_pbar = MagicMock()
        mock_pbar.n = 10.0  # Already at 10 seconds
        mock_tqdm.return_value = mock_pbar

        tracker = ProgressTracker(120.0)

        # Time goes backwards to 5 seconds (shouldn't happen but test handling)
        line = "out_time_ms=5000"
        tracker.update(line)

        # update should NOT be called since delta would be negative
        mock_pbar.update.assert_not_called()

    @patch('core.progress_tracker.tqdm')
    def test_close_calls_pbar_close_and_logs(self, mock_tqdm):
        """Test that close method properly closes progress bar and logs."""
        mock_pbar = MagicMock()
        mock_tqdm.return_value = mock_pbar

        tracker = ProgressTracker(120.0)

        with patch('core.progress_tracker.logger') as mock_logger:
            tracker.close()

            mock_pbar.close.assert_called_once()
            mock_logger.info.assert_called_once_with('Отслеживание прогресса завершено')

    @patch('core.progress_tracker.tqdm')
    def test_multiple_updates_accumulate(self, mock_tqdm):
        """Test that multiple updates accumulate correctly."""
        # Create a mock pbar with dynamic n attribute
        mock_pbar = MagicMock()
        mock_pbar.n = 0
        
        def update_side_effect(delta):
            mock_pbar.n += delta
        
        mock_pbar.update.side_effect = update_side_effect
        mock_tqdm.return_value = mock_pbar

        tracker = ProgressTracker(120.0)

        # First update: from 0 to 5 seconds (delta = 5)
        tracker.update("out_time_ms=5000")
        # Second update: from 5 to 10 seconds (delta = 5)
        tracker.update("out_time_ms=10000")
        # Third update: from 10 to 15 seconds (delta = 5)
        tracker.update("out_time_ms=15000")

        # Should have been called 3 times with deltas
        assert mock_pbar.update.call_count == 3
        calls = mock_pbar.update.call_args_list
        assert calls[0] == call(5.0)
        assert calls[1] == call(5.0)
        assert calls[2] == call(5.0)

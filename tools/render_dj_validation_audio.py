#!/usr/bin/env python3
"""Reconstruct illustrative PCM from Android validation fixtures and measured gain traces.

These WAVs are NOT captured Android output. This stage-one renderer includes player
volume only; it does not reproduce later per-song normalization or bass DSP. They illustrate the measured envelope
with generated test tones; no conclusion about device acoustic quality is implied.
Usage: python3 tools/render_dj_validation_audio.py <pulled-dj-validation-directory>
Requires numpy. Report and original traces remain the source of timing evidence.
"""
import argparse
import json
from pathlib import Path
import wave

import numpy as np


def read_pcm(path):
    with wave.open(str(path), 'rb') as source:
        if source.getsampwidth() != 2 or source.getnchannels() != 1:
            raise ValueError('Expected mono PCM16 validation fixtures')
        return source.getframerate(), np.frombuffer(source.readframes(source.getnframes()), dtype='<i2').astype(float) / 32768.0


def render(directory, name):
    trace = json.loads((directory / (name + '.json')).read_text())
    # Keep the last observation at each instant, then interpolate recorded controls.
    rows = {row['wallMs']: row for row in trace['samples']}
    rows = [rows[key] for key in sorted(rows)]
    fade_start = next(row['wallMs'] for row in rows if row['incomingVolume'] > .001)
    start = max(rows[0]['wallMs'], fade_start - 2000)
    end = rows[-1]['wallMs']
    sample_rate = 16000
    times = np.arange(int((end-start) * sample_rate / 1000)) * 1000 / sample_rate + start
    measured_times = np.array([r['wallMs'] for r in rows])
    mixed = np.zeros(len(times))
    for deck in ('outgoing', 'incoming'):
        rate, pcm = read_pcm(directory / trace[deck + 'Fixture'])
        positions = []
        for i, row in enumerate(rows):
            position = row[deck + 'PositionMs']
            # A cleared outgoing deck reports position zero after handoff. Its gain is
            # zero then, but retain its final trajectory across that observation boundary.
            if row[deck + 'ItemCount'] == 0 and i and positions[-1] > position:
                position = positions[-1] + row['wallMs'] - rows[i-1]['wallMs']
            positions.append(position)
        source_indices = np.interp(times, measured_times, positions) * rate / 1000
        values = np.interp(source_indices, np.arange(len(pcm)), pcm, left=0, right=0)
        gains = np.interp(times, measured_times, [r[deck+'Volume'] for r in rows])
        mixed += values * gains
    output = directory / (name + '-reconstruction.wav')
    with wave.open(str(output), 'wb') as destination:
        destination.setnchannels(1)
        destination.setsampwidth(2)
        destination.setframerate(sample_rate)
        destination.writeframes((np.clip(mixed, -1, 1) * 32767).astype('<i2').tobytes())
    return {'file': output.name, 'durationSeconds': len(mixed)/sample_rate,
            'peak': float(np.max(np.abs(mixed))), 'clippedSamples': int(np.sum(np.abs(mixed)>1))}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory', type=Path)
    args = parser.parse_args()
    results = [render(args.directory, name) for name in (
        'long_silent_tail_early_handoff', 'normal_blend_with_quiet_then_loud_intro')]
    report = {'kind': 'Illustrative reconstruction, not captured system audio', 'files': results}
    (args.directory/'listening-previews.json').write_text(json.dumps(report, indent=2)+'\n')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()

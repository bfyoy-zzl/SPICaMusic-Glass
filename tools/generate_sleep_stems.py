#!/usr/bin/env python3
"""生成助眠用的无缝循环噪声素材。

为什么不用普通音频文件直接循环：
  助眠音频通常只有一两分钟，整夜重复播放，接缝处任何一点咔哒声都会毁掉体验，
  而且房间越安静越明显。这里在**生成阶段**就把尾部与头部做交叉淡化，
  得到一个真正首尾相接的循环体，播放器即使有十几毫秒的接缝间隙也听不出来。

用法：
  python tools/generate_sleep_stems.py
输出：
  feature-sleep-data/src/main/assets/sleep/{white,pink,brown}.wav

换成自己的真实素材（雨声、篝火）时不需要这个脚本，
把文件丢进同一个目录并在 SleepCatalog 里加一条记录即可。
"""

from __future__ import annotations

import array
import math
import os
import random
import wave

SAMPLE_RATE = 44_100
LOOP_SECONDS = 12.0
CROSSFADE_SECONDS = 0.8
PEAK_TARGET = 0.72          # -2.9 dBFS，留够余量，多轨叠加时不至于一上来就削波
OUTPUT_DIR = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "feature-sleep-data", "src", "main", "assets", "sleep",
)


def make_white(count: int, rng: random.Random) -> list[float]:
    return [rng.uniform(-1.0, 1.0) for _ in range(count)]


def make_pink(count: int, rng: random.Random) -> list[float]:
    """Paul Kellet 的 7 极点近似，误差在 0.05 dB 以内，计算量小。"""
    b0 = b1 = b2 = b3 = b4 = b5 = b6 = 0.0
    out = [0.0] * count
    for i in range(count):
        w = rng.uniform(-1.0, 1.0)
        b0 = 0.99886 * b0 + w * 0.0555179
        b1 = 0.99332 * b1 + w * 0.0750759
        b2 = 0.96900 * b2 + w * 0.1538520
        b3 = 0.86650 * b3 + w * 0.3104856
        b4 = 0.55000 * b4 + w * 0.5329522
        b5 = -0.7616 * b5 - w * 0.0168980
        out[i] = b0 + b1 + b2 + b3 + b4 + b5 + b6 + w * 0.5362
        b6 = w * 0.115926
    return out


def make_brown(count: int, rng: random.Random) -> list[float]:
    """带泄漏的随机游走（积分白噪），泄漏系数决定最低频截止。"""
    out = [0.0] * count
    last = 0.0
    for i in range(count):
        last = last * 0.997 + rng.uniform(-1.0, 1.0) * 0.06
        out[i] = last
    mean = sum(out) / count
    return [v - mean for v in out]


def fold_to_loop(samples: list[float], loop_len: int, fade_len: int) -> list[float]:
    """把 [0, fade) 与 [loop_len, loop_len + fade) 交叉，得到首尾相接的循环体。

    播放到末尾后回到开头时，样本序列天然连续（末尾尾巴正是开头的前置延续），
    所以既没有咔哒声，也没有淡入淡出那种"每次循环都呼吸一下"的感觉。
    """
    out = samples[:loop_len]
    for i in range(fade_len):
        weight = i / fade_len
        out[i] = samples[i] * weight + samples[loop_len + i] * (1.0 - weight)
    return out


def normalize(samples: list[float], peak_target: float) -> list[float]:
    peak = max(abs(v) for v in samples) or 1.0
    scale = peak_target / peak
    return [v * scale for v in samples]


def write_wav(path: str, samples: list[float]) -> tuple[float, float]:
    pcm = array.array("h", (int(max(-1.0, min(1.0, v)) * 32767) for v in samples))
    with wave.open(path, "wb") as wav:
        wav.setnchannels(1)
        wav.setsampwidth(2)
        wav.setframerate(SAMPLE_RATE)
        wav.writeframes(pcm.tobytes())
    peak = max(abs(v) for v in samples)
    rms = math.sqrt(sum(v * v for v in samples) / len(samples))
    return peak, rms


def main() -> None:
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    loop_len = int(LOOP_SECONDS * SAMPLE_RATE)
    fade_len = int(CROSSFADE_SECONDS * SAMPLE_RATE)
    total = loop_len + fade_len

    generators = {
        "white": make_white,
        "pink": make_pink,
        "brown": make_brown,
    }

    for name, generator in generators.items():
        rng = random.Random(hash(name) & 0xFFFFFFFF)
        raw = generator(total, rng)
        loop = fold_to_loop(raw, loop_len, fade_len)
        loop = normalize(loop, PEAK_TARGET)
        path = os.path.join(OUTPUT_DIR, f"{name}.wav")
        peak, rms = write_wav(path, loop)
        size_mb = os.path.getsize(path) / (1024 * 1024)
        print(
            f"{name:6s} {LOOP_SECONDS:.0f}s {SAMPLE_RATE}Hz mono  "
            f"peak={20 * math.log10(peak):6.2f} dBFS  "
            f"rms={20 * math.log10(rms):6.2f} dBFS  "
            f"{size_mb:.2f} MB"
        )


if __name__ == "__main__":
    main()

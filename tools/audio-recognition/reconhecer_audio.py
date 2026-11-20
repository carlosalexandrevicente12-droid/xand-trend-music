#!/usr/bin/env python3
"""Identifica titulo, artista e genero de um arquivo de audio do XAND MUSIC.

Nao baixa videos do TikTok nem altera a fila Android.
A assinatura acustica e consultada por uma API externa nao oficial.
"""
from __future__ import annotations

import argparse
import asyncio
import json
import sys
from pathlib import Path
from typing import Any


def parse_result(data: dict[str, Any], file_name: str) -> dict[str, Any]:
    track = data.get("track")
    if not isinstance(track, dict) or not track:
        return {
            "status": "not_found",
            "source_audio": file_name,
            "title": None,
            "artist": None,
            "genre": None,
            "provider": "ShazamIO",
        }
    genres = track.get("genres")
    genre = genres.get("primary") if isinstance(genres, dict) else None
    return {
        "status": "matched",
        "source_audio": file_name,
        "title": track.get("title"),
        "artist": track.get("subtitle"),
        "genre": genre,
        "track_key": track.get("key"),
        "provider": "ShazamIO",
    }


async def recognize(path: Path, timeout_seconds: float) -> dict[str, Any]:
    from shazamio import Shazam

    async with Shazam() as client:
        data = await asyncio.wait_for(
            client.recognize(str(path)), timeout=timeout_seconds
        )
    if not isinstance(data, dict):
        raise ValueError("O servico nao retornou um objeto JSON.")
    return parse_result(data, path.name)


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Reconhece musica em audio local e imprime JSON para a fila XAND MUSIC."
    )
    parser.add_argument("audio", type=Path, help="Caminho de um arquivo MP3/WAV/OGG.")
    parser.add_argument("--timeout", type=float, default=45.0)
    args = parser.parse_args()

    path = args.audio.expanduser().resolve()
    if not path.is_file():
        print(json.dumps({
            "status": "error", "message": "Arquivo de audio nao encontrado."
        }, ensure_ascii=False))
        return 1
    if args.timeout <= 0:
        parser.error("--timeout deve ser maior que zero.")

    try:
        result = asyncio.run(recognize(path, args.timeout))
    except (Exception, KeyboardInterrupt) as exc:
        # Nunca inclui tokens, URLs do servico ou diretorios internos no JSON.
        print(json.dumps({
            "status": "error",
            "message": f"Falha no reconhecimento ({type(exc).__name__})."
        }, ensure_ascii=False))
        return 1

    print(json.dumps(result, ensure_ascii=False))
    return 0 if result["status"] == "matched" else 2


if __name__ == "__main__":
    sys.exit(main())

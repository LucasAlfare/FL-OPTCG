#!/usr/bin/env python3
"""Download OPTCG API booster-set card data, removing price fields only.

Examples:
  python download_optcg_sets.py --set OP-07
  python download_optcg_sets.py --set OP-07 --set OP-08
  python download_optcg_sets.py --all-sets
  python download_optcg_sets.py --all-sets --output ./card-data/raw/sets --force

The script preserves source values and card rows as returned by the API. It does
not normalize card names, nulls, types, traits, or card variants.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.parse import quote
from urllib.request import Request, urlopen

BASE_URL = "https://optcgapi.com/api"
USER_AGENT = "FL-OPTCG-Card-Data-Downloader/1.0 (personal project)"
RETRY_LIMIT = 4
TIMEOUT_SECONDS = 30
RECORD_KEYS = ("data", "cards", "sets", "results", "records")


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


def request_json(url: str, delay: float) -> Any:
    """GET one JSON endpoint, with polite pacing and bounded retries."""
    for attempt in range(RETRY_LIMIT + 1):
        if delay > 0:
            time.sleep(delay)

        request = Request(
            url,
            headers={
                "Accept": "application/json",
                "User-Agent": USER_AGENT,
            },
            method="GET",
        )

        try:
            with urlopen(request, timeout=TIMEOUT_SECONDS) as response:
                body = response.read()
            try:
                return json.loads(body.decode("utf-8"))
            except (UnicodeDecodeError, json.JSONDecodeError) as exc:
                raise RuntimeError(f"Resposta não é JSON válido: {url}") from exc

        except HTTPError as exc:
            retryable = exc.code == 429 or 500 <= exc.code <= 599
            if not retryable or attempt >= RETRY_LIMIT:
                raise RuntimeError(f"HTTP {exc.code} ao consultar {url}") from exc

            retry_after = exc.headers.get("Retry-After") if exc.headers else None
            try:
                wait_seconds = float(retry_after) if retry_after else 2 ** attempt
            except ValueError:
                wait_seconds = 2 ** attempt
            wait_seconds = max(wait_seconds, delay)
            print(f"HTTP {exc.code}; aguardando {wait_seconds:.1f}s para tentar novamente...", file=sys.stderr)
            time.sleep(wait_seconds)

        except URLError as exc:
            if attempt >= RETRY_LIMIT:
                raise RuntimeError(f"Falha de conexão ao consultar {url}: {exc}") from exc
            wait_seconds = max(2 ** attempt, delay)
            print(f"Falha de conexão; aguardando {wait_seconds:.1f}s para tentar novamente...", file=sys.stderr)
            time.sleep(wait_seconds)

    raise RuntimeError(f"Não foi possível consultar {url}")


def extract_records(payload: Any, description: str) -> list[dict[str, Any]]:
    """Accept a JSON array or a common object wrapper containing an array."""
    if isinstance(payload, list):
        records = payload
    elif isinstance(payload, dict):
        records = None
        for key in RECORD_KEYS:
            candidate = payload.get(key)
            if isinstance(candidate, list):
                records = candidate
                break
        if records is None:
            raise RuntimeError(
                f"Formato inesperado em {description}: esperava uma lista JSON "
                f"ou um objeto contendo uma destas listas: {', '.join(RECORD_KEYS)}"
            )
    else:
        raise RuntimeError(f"Formato inesperado em {description}: resposta não é lista nem objeto")

    if not all(isinstance(record, dict) for record in records):
        raise RuntimeError(f"Formato inesperado em {description}: há registros que não são objetos JSON")
    return records


def remove_price_fields(value: Any) -> Any:
    """Recursively remove fields whose names contain 'price', case-insensitively."""
    if isinstance(value, dict):
        return {
            key: remove_price_fields(item)
            for key, item in value.items()
            if "price" not in key.lower()
        }
    if isinstance(value, list):
        return [remove_price_fields(item) for item in value]
    return value


def safe_filename(value: str) -> str:
    cleaned = re.sub(r"[^A-Za-z0-9._-]+", "_", value).strip("._")
    if not cleaned:
        raise RuntimeError(f"Identificador de coleção inválido: {value!r}")
    return cleaned


def extract_set_ids(payload: Any) -> list[str]:
    sets = extract_records(payload, "/api/allSets/")
    set_ids: list[str] = []
    for entry in sets:
        set_id = entry.get("set_id")
        if not isinstance(set_id, str) or not set_id.strip():
            raise RuntimeError(
                "A resposta de /api/allSets/ contém um registro sem 'set_id'. "
                "Confira o formato atual da API antes de continuar."
            )
        set_ids.append(set_id.strip())
    return sorted(set(set_ids))


def save_json(path: Path, payload: Any) -> bytes:
    encoded = (json.dumps(payload, ensure_ascii=False, indent=2) + "\n").encode("utf-8")
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_bytes(encoded)
    temporary.replace(path)
    return encoded


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Baixa cartas das coleções OPTCG e remove somente campos relacionados a preços."
    )
    parser.add_argument(
        "--set",
        dest="set_ids",
        action="append",
        help="ID de coleção, por exemplo OP-07. Pode ser repetido.",
    )
    parser.add_argument(
        "--all-sets",
        action="store_true",
        help="Consulta /api/allSets/ e baixa cada coleção encontrada.",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("card-data/raw/sets"),
        help="Diretório de saída (padrão: card-data/raw/sets).",
    )
    parser.add_argument(
        "--delay",
        type=float,
        default=1.0,
        help="Pausa mínima em segundos antes de cada requisição (padrão: 1.0).",
    )
    parser.add_argument(
        "--force",
        action="store_true",
        help="Baixa novamente e substitui arquivos JSON que já existem.",
    )
    args = parser.parse_args()

    if not args.all_sets and not args.set_ids:
        parser.error("Informe --set OP-07 ou --all-sets.")
    if args.delay < 0:
        parser.error("--delay não pode ser negativo.")

    args.output.mkdir(parents=True, exist_ok=True)

    set_ids: list[str] = list(args.set_ids or [])
    if args.all_sets:
        sets_url = f"{BASE_URL}/allSets/"
        print(f"Consultando lista de coleções: {sets_url}")
        set_ids.extend(extract_set_ids(request_json(sets_url, args.delay)))

    # Keep order while removing duplicate IDs supplied by the user and API.
    set_ids = list(dict.fromkeys(set_ids))
    if not set_ids:
        print("A API não retornou coleções.")
        return 0

    manifest_path = args.output / "download-manifest.json"
    manifest: dict[str, Any] = {
        "provider": "OPTCG API",
        "base_url": BASE_URL,
        "updated_at_utc": utc_now(),
        "price_fields_removed": "All object keys containing 'price' (case-insensitive)",
        "sets": {},
    }
    if manifest_path.exists():
        try:
            previous = json.loads(manifest_path.read_text(encoding="utf-8"))
            if isinstance(previous, dict) and isinstance(previous.get("sets"), dict):
                manifest["sets"].update(previous["sets"])
        except json.JSONDecodeError:
            print("Aviso: manifesto anterior inválido; será recriado.", file=sys.stderr)

    downloaded = 0
    skipped = 0

    for index, set_id in enumerate(set_ids, start=1):
        filename = safe_filename(set_id) + ".json"
        output_path = args.output / filename
        endpoint = f"{BASE_URL}/sets/{quote(set_id, safe='')}/"

        if output_path.exists() and not args.force:
            print(f"[{index}/{len(set_ids)}] Ignorando {set_id}: {filename} já existe (use --force para substituir).")
            skipped += 1
            continue

        print(f"[{index}/{len(set_ids)}] Baixando {set_id}...")
        payload = request_json(endpoint, args.delay)
        records = extract_records(payload, endpoint)
        cleaned = remove_price_fields(records)
        encoded = save_json(output_path, cleaned)

        manifest["sets"][set_id] = {
            "file": filename,
            "endpoint": endpoint,
            "retrieved_at_utc": utc_now(),
            "record_count": len(cleaned),
            "sha256": hashlib.sha256(encoded).hexdigest(),
        }
        manifest["updated_at_utc"] = utc_now()
        save_json(manifest_path, manifest)

        print(f"  Salvo: {output_path} ({len(cleaned)} registros; preços removidos)")
        downloaded += 1

    print()
    print(f"Concluído. Baixados: {downloaded}; ignorados: {skipped}; coleções solicitadas: {len(set_ids)}.")
    print(f"Diretório: {args.output.resolve()}")
    print(f"Manifesto: {manifest_path.resolve()}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, RuntimeError, ValueError) as exc:
        print(f"ERRO: {exc}", file=sys.stderr)
        raise SystemExit(1)

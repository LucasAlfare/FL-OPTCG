#!/usr/bin/env python3
"""Download OPTCG set data and create compact, deduplicated LLM input."""
from __future__ import annotations
import argparse, hashlib, json, re, sys, time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.parse import quote
from urllib.request import Request, urlopen

BASE_URL = "https://optcgapi.com/api"
USER_AGENT = "FL-OPTCG-Card-Data-Downloader/2.0"
RETRIES, TIMEOUT = 4, 30
WRAPPERS = ("data", "cards", "sets", "results", "records")

def now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds")

def get_json(url: str, delay: float) -> Any:
    for attempt in range(RETRIES + 1):
        if delay:
            time.sleep(delay)
        req = Request(url, headers={"Accept": "application/json", "User-Agent": USER_AGENT})
        try:
            with urlopen(req, timeout=TIMEOUT) as response:
                return json.loads(response.read().decode("utf-8"))
        except HTTPError as exc:
            if exc.code not in (429, 500, 502, 503, 504) or attempt == RETRIES:
                raise RuntimeError(f"HTTP {exc.code}: {url}") from exc
            try:
                wait = max(float(exc.headers.get("Retry-After", 2 ** attempt)), delay)
            except (TypeError, ValueError):
                wait = max(2 ** attempt, delay)
            print(f"HTTP {exc.code}; nova tentativa em {wait:g}s", file=sys.stderr)
            time.sleep(wait)
        except (URLError, TimeoutError, UnicodeDecodeError, json.JSONDecodeError) as exc:
            if attempt == RETRIES:
                raise RuntimeError(f"Falha ao consultar {url}: {exc}") from exc
            time.sleep(max(2 ** attempt, delay))
    raise RuntimeError(f"Falha ao consultar {url}")

def records_from(payload: Any, source: str) -> list[dict[str, Any]]:
    if isinstance(payload, dict):
        payload = next((payload[k] for k in WRAPPERS if isinstance(payload.get(k), list)), None)
    if not isinstance(payload, list) or not all(isinstance(x, dict) for x in payload):
        raise RuntimeError(f"Resposta inesperada da API em {source}")
    return payload

def without_prices(value: Any) -> Any:
    if isinstance(value, dict):
        return {k: without_prices(v) for k, v in value.items() if "price" not in k.lower()}
    if isinstance(value, list):
        return [without_prices(v) for v in value]
    return value

def save_json(path: Path, value: Any, compact_cards: bool = False) -> str:
    path.parent.mkdir(parents=True, exist_ok=True)
    if compact_cards and isinstance(value, list):
        content = "[\n" + ",\n".join(
            "  " + json.dumps(item, ensure_ascii=False, separators=(",", ":")) for item in value
        ) + "\n]\n"
    else:
        content = json.dumps(value, ensure_ascii=False, indent=2) + "\n"
    encoded = content.encode("utf-8")
    temp = path.with_suffix(path.suffix + ".tmp")
    temp.write_bytes(encoded)
    temp.replace(path)
    return hashlib.sha256(encoded).hexdigest()

def compact_cards(raw_cards: list[dict[str, Any]]) -> tuple[list[dict[str, Any]], int]:
    """One object per card_set_id. Equal fields occur once; varying fields only in printings."""
    groups: dict[str, list[dict[str, Any]]] = {}
    missing_id: list[tuple[int, dict[str, Any]]] = []
    for index, card in enumerate(raw_cards):
        card_id = card.get("card_set_id")
        if card_id is None or str(card_id).strip() == "":
            missing_id.append((index, card))
        else:
            groups.setdefault(str(card_id), []).append(card)

    result: list[dict[str, Any]] = []
    conflict_count = 0
    gameplay_keys = {
        "card_name", "card_text", "card_color", "card_type", "life", "card_cost",
        "card_power", "sub_types", "counter_amount", "attribute",
    }
    for card_id, variants in groups.items():
        keys = list(dict.fromkeys(k for variant in variants for k in variant))
        shared: dict[str, Any] = {"card_set_id": card_id}
        varying: list[str] = []
        for key in keys:
            values = [variant.get(key) for variant in variants]
            if all(value == values[0] for value in values[1:]):
                if key != "card_set_id":
                    shared[key] = values[0]
            else:
                varying.append(key)

        # Only differing properties appear in each printing. If only image differs,
        # every printing object contains just card_image_id/card_image.
        shared["printings"] = [{key: variant.get(key) for key in varying} for variant in variants]
        conflicts = [key for key in varying if key in gameplay_keys]
        if conflicts:
            conflict_count += 1
            shared["translation_status"] = "CONFLICT"
            shared["gameplay_conflicts"] = {
                key: list(dict.fromkeys(json.dumps(v.get(key), ensure_ascii=False, sort_keys=True) for v in variants))
                for key in conflicts
            }
        result.append(shared)

    for index, original in missing_id:
        card = dict(original)
        card["_source_record"] = index
        card["_grouping_warning"] = "MISSING_CARD_SET_ID"
        card["printings"] = [{k: card.pop(k) for k in ("rarity", "card_image_id", "card_image") if k in card}]
        result.append(card)

    result.sort(key=lambda item: str(item.get("card_set_id", "")))
    return result, conflict_count

def safe_name(value: str) -> str:
    name = re.sub(r"[^A-Za-z0-9._-]+", "_", value).strip("._")
    if not name:
        raise RuntimeError(f"ID de coleção inválido: {value!r}")
    return name

def main() -> int:
    parser = argparse.ArgumentParser(description="Baixa cartas OPTCG e gera input compacto agrupado para LLM.")
    parser.add_argument("--set", dest="set_ids", action="append", help="ID da coleção, por exemplo OP-01; pode repetir.")
    parser.add_argument("--all-sets", action="store_true", help="Descobre e baixa todas as coleções disponíveis.")
    parser.add_argument("--output", type=Path, default=Path("card-data"), help="Raiz dos dados (padrão: card-data).")
    parser.add_argument("--delay", type=float, default=1.0, help="Pausa entre requisições em segundos (padrão: 1).")
    parser.add_argument("--refresh", action="store_true", help="Baixa novamente mesmo quando o RAW já existe.")
    args = parser.parse_args()
    if not args.all_sets and not args.set_ids:
        parser.error("Informe --set OP-01 ou --all-sets.")
    if args.delay < 0:
        parser.error("--delay não pode ser negativo.")

    raw_dir = args.output / "raw" / "sets"
    llm_dir = args.output / "llm-input" / "sets"
    set_ids = list(args.set_ids or [])
    if args.all_sets:
        all_sets = records_from(get_json(f"{BASE_URL}/allSets/", args.delay), "/allSets/")
        for item in all_sets:
            sid = item.get("set_id")
            if not isinstance(sid, str) or not sid.strip():
                raise RuntimeError("Registro sem set_id em /allSets/")
            set_ids.append(sid.strip())
    set_ids = list(dict.fromkeys(set_ids))

    manifest_path = raw_dir / "download-manifest.json"
    manifest: dict[str, Any] = {"provider": "OPTCG API", "sets": {}}
    if manifest_path.exists():
        try:
            previous = json.loads(manifest_path.read_text(encoding="utf-8"))
            if isinstance(previous, dict) and isinstance(previous.get("sets"), dict):
                manifest["sets"].update(previous["sets"])
        except json.JSONDecodeError:
            print("Aviso: manifesto anterior inválido; será recriado.", file=sys.stderr)

    for position, set_id in enumerate(set_ids, 1):
        filename = safe_name(set_id) + ".json"
        raw_path, llm_path = raw_dir / filename, llm_dir / filename
        endpoint = f"{BASE_URL}/sets/{quote(set_id, safe='')}/"
        if raw_path.exists() and not args.refresh:
            print(f"[{position}/{len(set_ids)}] RAW existente: {raw_path}")
            raw_records = records_from(json.loads(raw_path.read_text(encoding="utf-8")), str(raw_path))
        else:
            print(f"[{position}/{len(set_ids)}] Baixando {set_id}...")
            api_records = records_from(get_json(endpoint, args.delay), endpoint)
            raw_records = without_prices(api_records)
            raw_hash = save_json(raw_path, raw_records)
            manifest["sets"][set_id] = {
                "endpoint": endpoint, "retrieved_at_utc": now(), "raw_file": filename,
                "raw_record_count": len(raw_records), "raw_sha256": raw_hash,
            }
            manifest["updated_at_utc"] = now()
            save_json(manifest_path, manifest)

        grouped, conflicts = compact_cards(raw_records)
        llm_hash = save_json(llm_path, grouped, compact_cards=True)
        print(f"  RAW: {len(raw_records)} registros | LLM: {len(grouped)} cartas | conflitos de gameplay: {conflicts}")
        print(f"  Input LLM: {llm_path} ({llm_path.stat().st_size:,} bytes; SHA-256 {llm_hash[:12]})")
    print("\nConcluído.")
    print(f"RAW: {raw_dir}\nLLM INPUT: {llm_dir}")
    return 0

if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, RuntimeError, ValueError, json.JSONDecodeError) as exc:
        print(f"ERRO: {exc}", file=sys.stderr)
        raise SystemExit(1)

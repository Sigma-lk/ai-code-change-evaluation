# -*- coding: utf-8 -*-
"""
根据 HTTP ``status_code`` 与响应体 ``body`` 解析为与 Java DTO JSON 字段名一致的结构。

- 2xx：``RiskPropagationResponse`` — ``repoId``, ``effectiveDepth``, ``results``；``truncation`` 仅在 body 含该对象时输出（与 ``@JsonInclude(NON_NULL)`` 下无该字段时一致）。
- 非 2xx：与 2xx 相同的 ``out`` 结构，各字段为 ``""`` / ``0`` / ``[]`` / ``truncation`` 为 ``{}``。

Dify：入参绑定 HTTP 节点的 ``status_code``、``body``，整段粘贴后调用 ``main(status_code, body)``。
"""

from __future__ import annotations

import json
import math
from typing import Any, Dict, List, Union

RawInput = Union[str, bytes, bytearray, dict, None]


def _parse_json_root(value: RawInput) -> Dict[str, Any]:
    if value is None:
        return {}
    if isinstance(value, dict):
        return value
    if isinstance(value, (bytes, bytearray)):
        try:
            text = value.decode("utf-8")
        except UnicodeDecodeError:
            return {}
    else:
        text = str(value)
    if not text.strip():
        return {}
    try:
        root = json.loads(text)
    except (json.JSONDecodeError, ValueError, TypeError):
        return {}
    return root if isinstance(root, dict) else {}


def _node_ref(item: Any) -> Dict[str, Any]:
    if not isinstance(item, dict):
        return {"kind": "", "qualifiedName": "", "filePath": ""}
    kind = item.get("kind")
    qn = item.get("qualifiedName")
    fp = item.get("filePath")
    return {
        "kind": kind if isinstance(kind, str) else "",
        "qualifiedName": qn if isinstance(qn, str) else "",
        "filePath": fp if isinstance(fp, str) else "",
    }


def _impact_chain(item: Any) -> Dict[str, Any]:
    if not isinstance(item, dict):
        return {
            "chainKind": "",
            "hopCount": 0,
            "nodes": [],
            "edgeTypes": [],
        }
    nodes_raw = item.get("nodes")
    nodes: List[Dict[str, Any]] = []
    if isinstance(nodes_raw, list):
        nodes = [_node_ref(x) for x in nodes_raw]
    edges_raw = item.get("edgeTypes")
    edge_types: List[str] = []
    if isinstance(edges_raw, list):
        edge_types = [str(x) for x in edges_raw if isinstance(x, str)]
    ck = item.get("chainKind")
    hc = item.get("hopCount")
    hop = int(hc) if isinstance(hc, int) and not isinstance(hc, bool) else 0
    return {
        "chainKind": ck if isinstance(ck, str) else "",
        "hopCount": hop,
        "nodes": nodes,
        "edgeTypes": edge_types,
    }


def _seed_result(item: Any) -> Dict[str, Any]:
    if not isinstance(item, dict):
        return {
            "seed": {"kind": "", "qualifiedName": "", "filePath": ""},
            "matchedInGraph": False,
            "impactChains": [],
        }
    seed = _node_ref(item.get("seed"))
    mig = item.get("matchedInGraph")
    matched = bool(mig) if isinstance(mig, bool) else False
    chains_raw = item.get("impactChains")
    chains: List[Dict[str, Any]] = []
    if isinstance(chains_raw, list):
        chains = [_impact_chain(x) for x in chains_raw]
    return {
        "seed": seed,
        "matchedInGraph": matched,
        "impactChains": chains,
    }


def _risk_propagation_response(body_str: str) -> Dict[str, Any]:
    """与 ``RiskPropagationResponse`` + 嵌套 DTO 的 JSON 命名对齐。"""
    root = _parse_json_root(body_str)
    rid = root.get("repoId")
    ed = root.get("effectiveDepth")
    depth = int(ed) if isinstance(ed, int) and not isinstance(ed, bool) else 0

    results_raw = root.get("results")
    results: List[Dict[str, Any]] = []
    if isinstance(results_raw, list):
        results = [_seed_result(x) for x in results_raw]

    out: Dict[str, Any] = {
        "repoId": rid if isinstance(rid, str) else "",
        "effectiveDepth": depth,
        "results": results,
    }
    trunc_raw = root.get("truncation")
    if isinstance(trunc_raw, dict):
        w = trunc_raw.get("warnings")
        warnings = [str(x) for x in w] if isinstance(w, list) else []
        out["truncation"] = {"warnings": warnings}
    return out


def _empty_risk_propagation_response() -> Dict[str, Any]:
    """非 2xx 时与 ``RiskPropagationResponse`` 同形，字段尽数为「空」。"""
    return {
        "repoId": "",
        "effectiveDepth": 0,
        "results": [],
        "truncation": {},
    }


def _coerce_status_code(status_code: Any) -> int:
    if status_code is None:
        return 0
    if isinstance(status_code, bool):
        return 0
    if isinstance(status_code, int):
        return status_code
    if isinstance(status_code, float):
        if math.isnan(status_code) or math.isinf(status_code):
            return 0
        return int(status_code)
    if isinstance(status_code, str) and status_code.strip():
        try:
            return int(float(status_code.strip()))
        except ValueError:
            return 0
    return 0


def _body_to_str(body: Any) -> str:
    if body is None:
        return ""
    if isinstance(body, str):
        return body
    if isinstance(body, (bytes, bytearray)):
        try:
            return body.decode("utf-8")
        except UnicodeDecodeError:
            return ""
    return str(body)


def main(status_code, body):
    code = _coerce_status_code(status_code)
    body_str = _body_to_str(body)
    if 200 <= code <= 299:
        return _risk_propagation_response(body_str)
    return _empty_risk_propagation_response()

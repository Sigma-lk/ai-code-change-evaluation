# -*- coding: utf-8 -*-
"""
对「风险评估」LLM 输出做节点过滤与图传播深度计算，仅产出 ``nodes`` 与 ``propagationMaxDepth``（与
``RiskPropagationRequest`` 中对应字段同名），供 Dify 下游与其它节点拼完整请求体。
``repoId``、``maxNodes``、``maxEdges`` 不在此脚本处理，由上游或 HTTP 节点自行合并。

``llm_risks_json`` 入参可为 **JSON 字符串** 或 **已解析的 dict**（Dify 上游直接传 object 时走 dict 分支，**不再** ``json.loads``）。

处理步骤：
1. 丢弃 ``confidence`` 严格小于 ``0.5`` 的风险条目（缺失或非数字视为不达标并丢弃）。
2. 从保留条目中收集全部 ``nodes``，按 ``(kind, qualifiedName, filePath)`` 去重（strip；``kind`` 大小写不敏感）。
3. 根据去重后的节点数 ``n`` 计算 ``propagationMaxDepth``：
   - ``n < 20`` → ``30``
   - ``20 <= n <= 50`` → 从 ``30`` 线性降到 ``20``
   - ``n > 50`` → ``10``

出参（camelCase，仅两键）：
- ``nodes``：**JSON 字符串**，内容为 ``List[{kind, qualifiedName, filePath}]`` 的 ``json.dumps`` 结果（``ensure_ascii=False``），便于 Dify 下游与其它字段拼接后再整体 ``json.loads``。
- ``propagationMaxDepth``：``integer``。

用法（Dify「代码」节点）：
- ``main(llm_risks_json)``：参数为 **str 或 dict**（根对象含 ``risks``）。
- 粘贴进 Dify 时无需 ``if __name__ == "__main__"``；本地自测见 ``scripts/test_dify_risk_nodes_filter_and_depth.py``。
"""

from __future__ import annotations

import json
import math
from typing import Any, Dict, List, Optional, Tuple, Union

CONFIDENCE_THRESHOLD = 0.5
DEPTH_SMALL_MAX = 30
DEPTH_LARGE = 10
_DEPTH_AT_20 = 30
_DEPTH_AT_50 = 20


def _parse_llm_risks_root(value: Union[str, bytes, dict, None]) -> Dict[str, Any]:
    """
    归一为含 ``risks`` 列表的 dict。
    已为 ``dict`` 时直接使用（不序列化/反序列化）；字符串时才 ``json.loads``。
    """
    if value is None:
        return {"risks": []}
    if isinstance(value, dict):
        root = value
    else:
        if isinstance(value, (bytes, bytearray)):
            try:
                text = value.decode("utf-8")
            except (UnicodeDecodeError, ValueError):
                return {"risks": []}
        else:
            text = str(value)
        if not text.strip():
            return {"risks": []}
        try:
            root = json.loads(text)
        except (json.JSONDecodeError, ValueError, TypeError):
            return {"risks": []}
        if not isinstance(root, dict):
            return {"risks": []}
    risks = root.get("risks")
    if not isinstance(risks, list):
        return {"risks": []}
    return {"risks": risks}


def _confidence_ok(risk: Dict[str, Any]) -> bool:
    c = risk.get("confidence")
    if isinstance(c, bool):
        return False
    if isinstance(c, (int, float)):
        if isinstance(c, float) and (math.isnan(c) or math.isinf(c)):
            return False
        return float(c) >= CONFIDENCE_THRESHOLD
    if isinstance(c, str) and c.strip():
        try:
            return float(c) >= CONFIDENCE_THRESHOLD
        except ValueError:
            return False
    return False


def _normalize_node(node: Any) -> Optional[Dict[str, str]]:
    if not isinstance(node, dict):
        return None
    kind = node.get("kind")
    qn = node.get("qualifiedName")
    fp = node.get("filePath")
    if not isinstance(kind, str) or not kind.strip():
        return None
    if not isinstance(qn, str) or not qn.strip():
        return None
    path = fp if isinstance(fp, str) else ""
    return {
        "kind": kind.strip(),
        "qualifiedName": qn.strip(),
        "filePath": path.strip(),
    }


def _node_dedupe_key(n: Dict[str, str]) -> Tuple[str, str, str]:
    return (n["kind"].upper(), n["qualifiedName"], n["filePath"])


def _dedupe_nodes_preserve_order(nodes: List[Dict[str, str]]) -> List[Dict[str, str]]:
    seen: set[Tuple[str, str, str]] = set()
    out: List[Dict[str, str]] = []
    for n in nodes:
        k = _node_dedupe_key(n)
        if k in seen:
            continue
        seen.add(k)
        out.append(
            {
                "kind": n["kind"],
                "qualifiedName": n["qualifiedName"],
                "filePath": n["filePath"],
            }
        )
    return out


def propagation_max_depth_from_node_count(n: int) -> int:
    """
    根据去重后的节点数量返回图搜索允许的最大深度（整数）。

    - n < 20 → 30
    - 20 <= n <= 50 → 从 30 线性减至 20
    - n > 50 → 10
    """
    if n < 20:
        return DEPTH_SMALL_MAX
    if n <= 50:
        depth = _DEPTH_AT_20 + (_DEPTH_AT_50 - _DEPTH_AT_20) * (n - 20) / (50 - 20)
        return int(round(depth))
    return DEPTH_LARGE


def filter_risks_and_build_propagation_input(
    llm_risks_input: Union[str, bytes, dict, None],
) -> Dict[str, Any]:
    """
    执行过滤、节点去重与深度计算。

    ``llm_risks_input``：``dict``（根对象含 ``risks``）或 JSON 字符串 / bytes。

    返回 ``nodes``（JSON 字符串）、``propagationMaxDepth``，以及便于调试的 ``filteredRisks``、``stats``。
    """
    root = _parse_llm_risks_root(llm_risks_input)
    raw_risks = root.get("risks", [])
    if not isinstance(raw_risks, list):
        raw_risks = []

    filtered: List[Dict[str, Any]] = []
    raw_node_count = 0
    for item in raw_risks:
        if not isinstance(item, dict):
            continue
        if not _confidence_ok(item):
            continue
        filtered.append(dict(item))
        ns = item.get("nodes")
        if isinstance(ns, list):
            raw_node_count += sum(1 for x in ns if isinstance(x, dict))

    flat: List[Dict[str, str]] = []
    for risk in filtered:
        ns = risk.get("nodes")
        if not isinstance(ns, list):
            continue
        for node in ns:
            norm = _normalize_node(node)
            if norm is not None:
                flat.append(norm)

    nodes = _dedupe_nodes_preserve_order(flat)
    n = len(nodes)
    depth = propagation_max_depth_from_node_count(n)

    stats = {
        "originalRiskCount": len(raw_risks),
        "filteredRiskCount": len(filtered),
        "rawNodeOccurrences": raw_node_count,
        "dedupedNodeCount": n,
        "confidenceThreshold": CONFIDENCE_THRESHOLD,
        "depthRule": "n<20->30; 20<=n<=50 linear 30->20; n>50->10",
    }

    return {
        "nodes": json.dumps(nodes, ensure_ascii=False),
        "propagationMaxDepth": depth,
        "filteredRisks": filtered,
        "stats": stats,
    }


def build_risk_propagation_request_body(
    llm_risks_input: Union[str, bytes, dict, None],
) -> Dict[str, Any]:
    """
    仅组装 ``nodes``（JSON 字符串）与 ``propagationMaxDepth``（camelCase），与完整 ``RiskPropagationRequest`` 的子集一致。
    """
    core = filter_risks_and_build_propagation_input(llm_risks_input)
    return {
        "nodes": core["nodes"],
        "propagationMaxDepth": core["propagationMaxDepth"],
    }


def main(llm_risks_json: Union[str, dict, None]) -> dict:
    """
    Dify 代码节点入口。

    :param llm_risks_json: LLM 输出；**dict**（已解析 object）或 **JSON 字符串**（根对象含 ``risks``）。为 dict 时不做 ``json.loads``。
    :return: 仅含 ``nodes``（JSON 字符串）、``propagationMaxDepth``；``repoId`` / ``maxNodes`` / ``maxEdges`` 由其它步骤拼接。
    """
    return build_risk_propagation_request_body(llm_risks_json)

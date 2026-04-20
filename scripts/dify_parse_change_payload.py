# -*- coding: utf-8 -*-
"""
解析 Dify 工作流入参 change_payload（JSON 字符串或已解析的 dict），
安全拆出 meta / lineStats / truncation / changedFiles / nodes。

用法（Dify「代码」节点）：
1. 将本文件主体复制到节点中（或仅复制 parse_change_payload + main）。
2. 入参变量名与 Dify 中「开始」或上一步输出一致，例如 change_payload。
3. 返回值 dict 的键可作为下游节点变量（视 Dify 版本对返回类型的支持而定）。

安全策略：
- 非法 JSON、根非 object、字段类型不符 → 使用空 dict / 空 list；
- changedFiles / nodes 若为 JSON 字符串（历史双序列化）→ 尝试再解析一层为 list。
"""

from __future__ import annotations

import json
from typing import Any, Dict, List, Union

PayloadInput = Union[str, bytes, bytearray, dict, None]


def _ensure_dict(value: Any) -> Dict[str, Any]:
    return value if isinstance(value, dict) else {}


def _ensure_list(value: Any) -> List[Any]:
    if isinstance(value, list):
        return value
    if isinstance(value, str) and value.strip():
        try:
            inner = json.loads(value)
        except (json.JSONDecodeError, ValueError, TypeError):
            return []
        return inner if isinstance(inner, list) else []
    return []


def _normalize_truncation(value: Any) -> Dict[str, Any]:
    """truncation 缺省为 {}；若为 dict 则浅拷贝，且仅在存在 warnings 键时保证其为 list。"""
    if value is None:
        return {}
    if not isinstance(value, dict):
        return {}
    out = dict(value)
    if "warnings" in out and not isinstance(out.get("warnings"), list):
        out["warnings"] = []
    return out


def parse_change_payload(change_payload: PayloadInput) -> Dict[str, Any]:
    """
    将 change_payload 解析为固定结构；任一步失败则对应块为「空」占位。

    返回键：meta, lineStats, truncation, changedFiles, nodes（camelCase，与 Java 侧一致）。
    """
    empty: Dict[str, Any] = {
        "meta": {},
        "lineStats": {},
        "truncation": {},
        "changedFiles": [],
        "nodes": [],
        "meta": ""
    }

    if change_payload is None:
        return empty

    if isinstance(change_payload, dict):
        root = change_payload
    else:
        if isinstance(change_payload, (bytes, bytearray)):
            try:
                text = change_payload.decode("utf-8")
            except (UnicodeDecodeError, ValueError):
                return empty
        else:
            text = str(change_payload)
        if not text.strip():
            return empty
        try:
            root = json.loads(text)
        except (json.JSONDecodeError, ValueError, TypeError):
            return empty
        if not isinstance(root, dict):
            return empty

    meta = _ensure_dict(root.get("meta"))
    line_stats = _ensure_dict(root.get("lineStats"))
    truncation = _normalize_truncation(root.get("truncation"))

    changed_files = _ensure_list(root.get("changedFiles"))
    nodes = _ensure_list(root.get("nodes"))

    return {
        "meta": meta,
        "lineStats": line_stats,
        "truncation": truncation,
        "changedFiles": changed_files,
        "nodes": nodes,
        "repoId": meta.get("repoId", "")
    }


def main(change_payload: str) -> dict:
    """
    Dify 代码节点默认入口（将 change_payload 绑定为函数参数名）。

    若你的 Dify 版本使用固定签名 ``main(arg1: str)``，把参数名改为与 UI 一致，
    并在函数体内调用 ``return parse_change_payload(arg1)`` 即可。
    """
    return parse_change_payload(change_payload)

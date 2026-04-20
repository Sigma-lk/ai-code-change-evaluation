#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
向评估服务 POST /api/v1/risk/propagation。

服务端契约中 ``nodes`` 字段为 **JSON 数组的字符串**（与 ``RiskPropagationRequest`` 一致），
本脚本在组装请求体时会自动 ``json.dumps``，避免 Dify 直接传数组导致的反序列化错误。

由 Dify 代码节点直接调用 ``main(...)``，不设命令行入口。

返回值 ``{"status_code": int, "body": str}`` 可与 ``parse_risk_propagation_response.main(status_code, body)`` 直接对接。
"""

from __future__ import annotations

import json
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any, List, Union

DEFAULT_BASE_URL = "http://x538ff69.natappfree.cc"
RISK_PROPAGATION_PATH = "/api/v1/risk/propagation"
DEFAULT_MAX_NODES = 500
DEFAULT_MAX_EDGES = 2000


def _nodes_to_json_string(nodes: Union[str, Path, List[dict[str, Any]]]) -> str:
    """
    将 ``nodes`` 转为接口所需的 JSON 数组文本（单层 JSON，作为外层 JSON 的 string 值）。

    - ``str``：视为已是 JSON 数组文本，原样 strip 后使用（调用方需保证合法）。
    - ``Path``：读取文件内容，strip 后使用。
    - ``list``：序列化为紧凑 JSON 文本。
    """
    if isinstance(nodes, Path):
        raw = nodes.read_text(encoding="utf-8").strip()
        json.loads(raw)  # 尽早发现非法 JSON
        return raw
    if isinstance(nodes, list):
        return json.dumps(nodes, ensure_ascii=False, separators=(",", ":"))
    if isinstance(nodes, str):
        s = nodes.strip()
        json.loads(s)
        return s
    raise TypeError(f"nodes 类型不支持: {type(nodes)}")


def main(
    repo_id: str,
    nodes: Union[str, Path, List[dict[str, Any]]],
    propagation_max_depth: int,
    base_url: str = DEFAULT_BASE_URL,
) -> dict[str, Any]:
    """
    发送风险传播 POST 请求，返回 HTTP 状态码与响应体文本，供 ``parse_risk_propagation_response`` 解析。

    :param repo_id: 仓库 ID
    :param nodes: 节点列表（list）、JSON 数组文本（str）或节点 JSON 文件路径（Path）
    :param propagation_max_depth: 最大传播深度
    :param base_url: 服务根地址，不含末尾斜杠
    :return: ``{"status_code": int, "body": str}``；网络层失败时 ``status_code`` 为 ``0``
    """
    base = base_url.rstrip("/")
    url = f"{base}{RISK_PROPAGATION_PATH}"
    nodes_str = _nodes_to_json_string(nodes)
    body_obj = {
        "repoId": repo_id,
        "nodes": nodes_str,
        "propagationMaxDepth": propagation_max_depth,
        "maxNodes": DEFAULT_MAX_NODES,
        "maxEdges": DEFAULT_MAX_EDGES,
    }
    data = json.dumps(body_obj, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(
        url,
        data=data,
        method="POST",
        headers={"Content-Type": "application/json; charset=utf-8"},
    )
    try:
        with urllib.request.urlopen(req, timeout=120) as resp:
            charset = resp.headers.get_content_charset() or "utf-8"
            text = resp.read().decode(charset)
            return {"status_code": int(resp.getcode()), "body": text}
    except urllib.error.HTTPError as e:
        err_body = e.read().decode(e.headers.get_content_charset() or "utf-8", errors="replace")
        return {"status_code": int(e.code), "body": err_body}
    except urllib.error.URLError as e:
        reason = e.reason if isinstance(e.reason, str) else str(e.reason)
        return {"status_code": 0, "body": reason or str(e)}

# -*- coding: utf-8 -*-
"""
将「变更标题 + 变更摘要」LLM 所需说明与五段结构化输入拼成一条 prompt 字符串；约定模型最终输出 JSON 含 ``title`` 与 ``summary`` 两个字符串字段。

用法（Dify「代码」节点）：
1. 上游可为「解析 change_payload」节点的输出：meta、lineStats、truncation、changedFiles、nodes。
2. 将本文件复制到代码节点；入参与 main 参数名一致。
3. 返回 dict，键 ``prompt`` 为最终提示词，供下游 LLM 节点引用。

入参约定：
- ``meta`` / ``lineStats`` / ``truncation``：**dict**（非法或缺失时当作 ``{}``）。
- ``changedFiles`` / ``nodes``：**array[dict]**（非法或缺失时当作 ``[]``；非 dict 元素丢弃）。

说明：占位符使用 ``<<<KEY>>>``，避免与 JSON 中的花括号冲突。
"""

from __future__ import annotations

import json
from typing import Any, Dict, List

# ---------------------------------------------------------------------------
# 与解析脚本一致的类型归一（便于单文件粘贴进 Dify）
# ---------------------------------------------------------------------------


def _ensure_dict(value: Any) -> Dict[str, Any]:
    if isinstance(value, dict):
        return value
    if isinstance(value, str) and value.strip():
        try:
            inner = json.loads(value)
        except (json.JSONDecodeError, ValueError, TypeError):
            return {}
        return inner if isinstance(inner, dict) else {}
    return {}


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


def _ensure_list_of_dicts(value: Any) -> List[Dict[str, Any]]:
    """仅保留 dict 元素，得到 array[dict]。"""
    return [x for x in _ensure_list(value) if isinstance(x, dict)]


def _normalize_truncation(value: Any) -> Dict[str, Any]:
    if value is None:
        return {}
    if not isinstance(value, dict):
        return {}
    out = dict(value)
    if "warnings" in out and not isinstance(out.get("warnings"), list):
        out["warnings"] = []
    return out


def _json_compact(obj: Any) -> str:
    """紧凑 JSON 文本，便于嵌入 prompt。"""
    return json.dumps(obj, ensure_ascii=False, separators=(",", ":"))


# ---------------------------------------------------------------------------
# 摘要 Prompt 模版（占位符勿改）
# ---------------------------------------------------------------------------

SUMMARY_PROMPT_TEMPLATE = """你是一名资深研发负责人，需要根据一次代码推送的结构化数据，生成给人看的「短标题」与「变更摘要」。

## 写作要求（内容）
1. **必须明确给出以下三项（缺一不可，建议放在摘要开头最醒目处，可用一两句总起 + 文件列表）**：
   - **修改文件数量**：给出具体整数；以输入中 ``changedFiles`` 数组的有效元素个数为准（与 ``lineStats.javaFilesTouched`` 通常应一致；若你认为不一致，须在摘要中说明依据的是哪一项及原因）。
   - **代码数量（增删行）**：根据 ``lineStats`` 写出 ``totalInsertions``（新增行数）与 ``totalDeletions``（删除行数）；若 ``lineStats`` 为空或缺少上述字段，须明确写「输入中无增删行统计」或等价如实表述，不得编造数字。
   - **修改了哪些文件**：**完整列出** ``changedFiles`` 中每个元素的 ``path``（仓库相对路径）；可用分号、换行或简短列表形式，但必须与输入一一对应、路径字面一致；若 ``changedFiles`` 为空数组，须明确写「本次无变更 Java 文件」或等价表述。
2. 在以上三项之后，可补充宏观信息：大致影响范围（从已列出的 ``path`` 归纳包/目录层级上的「模块」）、提交意图；不要罗列每个 Java 类名或 FQN，但**文件路径清单按上条必须完整列出**。
3. 必须基于下列输入中的数字与文本推断，不要编造仓库里不存在的路径或统计。
4. 摘要正文使用中文；除第 1 条硬性三项外，建议再覆盖：
   - 数据与上下文补充：分支、短提交哈希、作者等 ``meta`` 中已有信息可择要一句带过。
   - 变更意图：约 2～5 句，综合 ``meta.commitMessages`` 概括「这次主要在做什么」，合并为连贯叙述，不要逐条翻译每条 message。
5. 若 ``truncation.warnings`` 非空，用一句话如实说明存在哪些数据限制（例如文件列表是否可能被截断），不要展开技术细节。
6. ``nodes`` 仅用于感知 AST 节点规模（可按 ``kind`` 宏观统计），禁止逐条抄写 ``qualifiedName`` 或 ``diffSnippet``；不要输出大段代码或 diff。
7. **标题（对应输出 JSON 的 ``title`` 字段）**：必须**主要结合** ``meta.commitMessages`` 提炼一条简短标题，概括本次推送在提交说明中体现的核心意图；**trim 后长度不得超过 15 个字符**（按 Unicode 码位计数，中文、英文、数字、空格、标点均各算 1）；不要外加引号、书名号或 Markdown；若 ``commitMessages`` 为空、缺失或全为无信息占位，则根据你在 ``summary`` 中将写的变更意图拟写**中性、如实**的短标题，仍不得超过 15 个字符，且不得捏造具体业务事件或数据。

## 输出格式（必须严格遵守）
1. 最终回复**只能是合法 JSON**，根对象**恰好含两个键** ``title`` 与 ``summary``（均为小写），值均为字符串；建议键顺序为 ``title`` 在前、``summary`` 在后；整体形状与 ``{"title":"","summary":""}`` 相同。
2. ``title`` 填入第 7 条要求的标题；``summary`` 填入完整摘要正文（须满足上文「写作要求」全部条款）。
3. ``title`` 与 ``summary`` 的值均须符合 JSON 字符串转义规则（换行、双引号、反斜杠等按 RFC 8259 转义），保证可被标准 JSON 解析器一次解析成功。
4. **不要**在 JSON 前后输出说明文字、Markdown 标题或代码围栏；除上述 JSON 文本外不得有任何额外字符。

## 输入数据

### meta（JSON object，可能为空对象）
<<<META_JSON>>>

### lineStats（JSON object，可能为空对象）
<<<LINE_STATS_JSON>>>

### truncation（JSON object，可能为空对象；warnings 应为数组）
<<<TRUNCATION_JSON>>>

### changedFiles（array[dict]，每行一个文件对象；可能为空数组）
<<<CHANGED_FILES_JSON>>>

### nodes（array[dict]；可能为空数组；请宏观使用，勿逐条抄写）
<<<NODES_JSON>>>
"""


def build_summary_prompt(
    meta: dict,
    line_stats: dict,
    truncation: dict,
    changed_files: list,
    nodes: list,
) -> dict:
    """
    将五段输入归一后填入模版，返回 ``{"prompt": "..."}``。
    前三个参数归一为 dict，后两个归一为 array[dict]。
    模版要求下游 LLM 输出 ``{"title":"","summary":""}``（``title`` 不超过 15 字符）。
    """
    m = _ensure_dict(meta)
    ls = _ensure_dict(line_stats)
    tr = _normalize_truncation(truncation)
    cf = _ensure_list_of_dicts(changed_files)
    nd = _ensure_list_of_dicts(nodes)
    prompt = (
        SUMMARY_PROMPT_TEMPLATE.replace("<<<META_JSON>>>", _json_compact(m))
        .replace("<<<LINE_STATS_JSON>>>", _json_compact(ls))
        .replace("<<<TRUNCATION_JSON>>>", _json_compact(tr))
        .replace("<<<CHANGED_FILES_JSON>>>", _json_compact(cf))
        .replace("<<<NODES_JSON>>>", _json_compact(nd))
    )
    return {"prompt": prompt}


def main(
    meta: dict,
    lineStats: dict,
    truncation: dict,
    changedFiles: list,
    nodes: list,
) -> dict:
    """
    Dify 代码节点入口；参数名与上游输出（camelCase）对齐。
    ``meta`` / ``lineStats`` / ``truncation`` 为 dict；``changedFiles`` / ``nodes`` 为 list。
    若上游类型异常，仍由 ``_ensure_*`` 在 ``build_summary_prompt`` 内归一。

    若环境仅支持 ``line_stats`` 等蛇形命名，可改为：
    ``return build_summary_prompt(meta, line_stats, truncation, changed_files, nodes)``
    """
    return build_summary_prompt(meta, lineStats, truncation, changedFiles, nodes)

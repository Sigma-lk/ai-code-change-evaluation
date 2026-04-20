# -*- coding: utf-8 -*-
"""
将「影响面与风险初步评估」LLM 所需说明与五段结构化输入拼成一条 prompt 字符串。

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
# 风险初步评估 Prompt 模版（占位符勿改）
# ---------------------------------------------------------------------------

RISK_PROMPT_TEMPLATE = """你是一名资深架构师，兼做安全与稳定性方面的评审。你将基于一次 Java 代码推送的结构化变更证据，完成「影响面与风险」的初步评估。

## 一、什么是「风险」
「风险」指：若本次变更按当前信息被合入主干或发布，可能在生产或关键路径上造成的负面后果，包括但不限于：
- 功能正确性：逻辑错误、边界条件、并发与一致性。
- 安全：鉴权/授权绕过、敏感数据暴露、注入、不安全反序列化、密钥或凭证硬编码等。
- 稳定性与性能：死锁、资源泄漏、热点路径变慢、无限重试或风暴等。
- 兼容性与契约：对外 API、序列化格式、配置、数据库 schema 等破坏性变更。
- 运维与可观测性：关键日志缺失、告警失效、部署或回滚困难等。

## 二、风险类型清单与优先级（P0～P3）
每条输出中的 ``risk_type`` 必须从下列枚举中选一项（``risk_name`` 用简短中文概括同一含义）：
1. ``correctness_bug`` — 正确性缺陷风险。
2. ``security_vulnerability`` — 安全漏洞或攻击面扩大。
3. ``concurrency_consistency`` — 并发与数据一致性。
4. ``performance_regression`` — 性能退化。
5. ``breaking_change`` — 破坏性变更或契约破坏。
6. ``reliability_ops`` — 稳定性与运维风险。
7. ``compliance_privacy`` — 合规与隐私相关。

``risk_priority`` 只能取 ``P0``、``P1``、``P2``、``P3``，含义如下：
- **P0**：极可能导致严重线上事故、高危安全问题或大范围不可用，且与本次改动强相关。
- **P1**：高影响，上线前必须有明确缓解措施或验证结论。
- **P2**：中等影响，需要针对性测试或评审。
- **P3**：低影响，或证据较弱时的保守归类。

## 三、输入数据（必须严格以此为事实来源）
以下为 JSON 片段（可能为空对象或空数组）。不得编造输入中不存在的路径、节点名或统计数字。

### meta（JSON object，可能为空对象）
<<<META_JSON>>>

### lineStats（JSON object，可能为空对象）
<<<LINE_STATS_JSON>>>

### truncation（JSON object，可能为空对象；warnings 应为数组）
<<<TRUNCATION_JSON>>>

### changedFiles（array[dict]；可能为空数组）
<<<CHANGED_FILES_JSON>>>

### nodes（array[dict]；重点：将风险关联到具体节点；diffSnippet 可能为空，不得据此捏造细节）
<<<NODES_JSON>>>

## 四、分析要求
1. 先阅读 ``truncation.warnings``：若提示大量节点无 ``diffSnippet``，必须降低细节推断的把握，并优先依据 ``kind``、``qualifiedName``、``filePath``、``changedFiles``、``meta.commitMessages`` 做粗粒度判断。
2. 对 ``risks`` 中每一条：尽量列出支撑该判断的 ``nodes``（元素为对象，字段与输入节点一致：``kind``、``qualifiedName``、``filePath``）。若无法关联到任何节点，``nodes`` 可为空数组，此时应通过较保守的 ``confidence`` 与较笼统且仍属实的 ``risk_name`` 反映依据主要来自 meta、changedFiles、lineStats、commitMessages 等，不得编造节点。
3. 同一条风险可对应多个节点；同一节点可出现在多条风险中。
4. 不要逐字抄写大段 ``diffSnippet``；必要时只概括与 snippet 一致的现象级描述。
5. 证据不足时，宁可少报风险、降低 ``risk_priority``、降低 ``confidence``，不要臆测具体漏洞类型或不存在的行为。

## 五、输出格式（必须严格遵守）
1. 最终回复**只能是合法 JSON**，根对象**仅含一个键**：``risks``（数组）。**不要**输出未列字段;
2. ``risks`` 中每个元素为对象，字段如下：
   - ``risk_name``：字符串，中文风险名称，简短。
   - ``risk_priority``：``P0``|``P1``|``P2``|``P3``。
   - ``nodes``：数组；每项为 ``{"kind":"","qualifiedName":"","filePath":""}``，值须与输入中已有节点一致（勿改写字段名）。
   - ``confidence``：0～1 的 JSON number，表示**该条风险成立**的主观把握（非优先级）。标度锚点：``1.0`` 仅当输入中有**直接、无歧义**的证据（如 snippet/commitMessages 明确对应）时使用，本阶段**极少**用到；``0.8`` 十足把握；``0.5`` 有一定把握；``0.2`` 可能存在、证据偏弱；``0`` 表示**该条风险肯定不成立**——若如此**不要**把它放进 ``risks``（宁可删条也不要输出 confidence 为 0 的条目）。证据越少，数值应整体下移。
3. 所有字符串须符合 RFC 8259 转义规则；``confidence`` 为 JSON number，不要用引号包裹。
4. **不要**在 JSON 前后输出说明文字、Markdown 标题或代码围栏；除上述 JSON 文本外不得有任何额外字符。

## 六、输出形状示例（字段名与类型须一致；内容为示意）
{"risks":[{"risk_name":"鉴权相关方法变更","risk_priority":"P1","nodes":[{"kind":"METHOD","qualifiedName":"com.example.AuthService#login","filePath":"/tmp/example/AuthService.java"}],"confidence":0.55}]}
"""


def build_risk_prompt(
    meta: dict,
    line_stats: dict,
    truncation: dict,
    changed_files: list,
    nodes: list,
) -> dict:
    """
    将五段输入归一后填入模版，返回 ``{"prompt": "..."}``。
    前三个参数归一为 dict，后两个归一为 array[dict]。
    """
    m = _ensure_dict(meta)
    ls = _ensure_dict(line_stats)
    tr = _normalize_truncation(truncation)
    cf = _ensure_list_of_dicts(changed_files)
    nd = _ensure_list_of_dicts(nodes)
    prompt = (
        RISK_PROMPT_TEMPLATE.replace("<<<META_JSON>>>", _json_compact(m))
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
    若上游类型异常，仍由 ``_ensure_*`` 在 ``build_risk_prompt`` 内归一。

    若环境仅支持 ``line_stats`` 等蛇形命名，可改为：
    ``return build_risk_prompt(meta, line_stats, truncation, changed_files, nodes)``
    """
    return build_risk_prompt(meta, lineStats, truncation, changedFiles, nodes)

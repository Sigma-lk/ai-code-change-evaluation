# -*- coding: utf-8 -*-
"""
将「风险识别与判断（含传播链证据与排查建议）」LLM 所需说明与两段结构化输入拼成一条 prompt 字符串。

用法（Dify「代码」节点）：
1. 上游 A：影响面初步判断 LLM 的 ``risks`` 数组，或与 ``dify_risk_nodes_filter_and_depth`` 的 ``filteredRisks`` 等价列表（顺序须与调用传播 API 时种子顺序一致）。
2. 上游 B：``RiskPropagationApi`` 响应 JSON，或经 ``parse_risk_propagation_response.main`` 解析后的 **dict**（字段名与 Java DTO 一致）。
3. 返回 ``{"prompt": "..."}``，供下游 LLM 节点引用。

入参约定：
- ``preliminaryRisks``：**array[dict]**，或 **dict**（根对象含 ``risks`` 数组）；非法时当作 ``[]``。
- ``propagation``：**dict**（非法时当作 ``{}``）。

说明：占位符使用 ``<<<KEY>>>``，避免与 JSON 中的花括号冲突。
"""

from __future__ import annotations

import json
from typing import Any, Dict, List

# ---------------------------------------------------------------------------
# 类型归一（便于单文件粘贴进 Dify）
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
    return [x for x in _ensure_list(value) if isinstance(x, dict)]


def _ensure_preliminary_risks_list(value: Any) -> List[Dict[str, Any]]:
    """支持根对象 ``{"risks":[...]}`` 或直接数组。"""
    if isinstance(value, dict):
        r = value.get("risks")
        if isinstance(r, list):
            return [x for x in r if isinstance(x, dict)]
        return []
    return _ensure_list_of_dicts(value)


def _json_compact(obj: Any) -> str:
    """紧凑 JSON 文本，便于嵌入 prompt。"""
    return json.dumps(obj, ensure_ascii=False, separators=(",", ":"))


# ---------------------------------------------------------------------------
# 风险识别与判断 Prompt 模版（占位符勿改）
# ---------------------------------------------------------------------------

RISK_IDENTIFICATION_PROMPT_TEMPLATE = """你是一名资深架构师，负责在「影响面初步判断」之后做**风险识别与二次判断**，并基于知识图谱上的**风险传播链**给出**可执行的排查建议**。你必须严格以输入 JSON 为事实来源，不得编造不存在的节点、路径、hop 数或 API 行为。

## 一、输入说明（JSON 片段）

### preliminaryRisks（array）
与上游 ``risks`` **数组顺序一致**（下标 0 起）。每条至少包含：
- ``risk_name``（string）
- ``risk_priority``：``P0``|``P1``|``P2``|``P3``
- ``confidence``：0～1 的 number
- ``nodes``（array）：元素含 ``kind``、``qualifiedName``、``filePath``

**顺序约定**：本数组应与调用 ``/api/v1/risk/propagation`` 时请求体中 ``nodes`` 种子顺序**协调一致**（通常先经置信度过滤再查图）；``propagation.results`` 与种子**顺序对齐**。

<<<PRELIMINARY_RISKS_JSON>>>

### propagation（object；与 RiskPropagation API 响应字段名一致）
- ``repoId``、``effectiveDepth``
- ``results``：与请求中种子顺序对齐；每项含 ``seed``（``kind``/``qualifiedName``/``filePath``）、``matchedInGraph``（boolean）、``impactChains``（array）
- ``impactChains`` 每项含：``chainKind``（如 ``TYPE_MEMBERS``、``FIELD_ACCESS`` 等）、``hopCount``（integer）、``nodes``（**有序**数组，与图上一致）、``edgeTypes``（与相邻节点间关系对应，可为空数组）
- 若存在 ``truncation.warnings``（string 数组），表示结果可能被截断或存在数据质量提示

<<<PROPAGATION_JSON>>>

## 二、什么是本阶段的「风险识别」与「判断」
- **识别（identification）**：在纳入传播链后，用一两句中文说明该条风险在代码结构上的**具体所指**（例如从某节点经何种关系可达哪些语义相关的类/方法），避免空泛复述 ``risk_name``。
- **判断（judgment）**：综合初步条目与链上证据，给出简短论证（可分段，总篇幅克制）：是否支持/削弱/无法判断该风险假设；不确定性须写明（无链、未匹配图、截断等）。

## 三、任务要求（对 preliminaryRisks 每一条，按下标逐一完成）

1. **对齐传播结果**  
   用 ``(kind, qualifiedName, filePath)`` 字符串与 ``propagation.results[].seed`` 精确匹配，将本条风险的 ``nodes`` 映射到对应 ``seed`` 及 ``impactChains``。若某节点未出现在任一 ``seed`` 中，须在 judgment 中说明「无对应传播结果」。

2. **利用证据链**  
   - ``matchedInGraph`` 为 true：选取最能支撑或反驳该风险假设的 **1～3** 条 ``impactChains``（优先 hop 较短、节点语义与风险相关的链），说明链上关键节点与 ``edgeTypes``/``chainKind`` 如何支持结论。
   - 为 false 或 ``impactChains`` 为空：明确图中未命中或无可达链，**不得虚构**传播路径；结论须保守。
   - ``truncation.warnings`` 非空或链很长：说明受深度/预算/截断限制，必要时下调 ``confidence_after``。

3. **结论标签 verdict**（英文 snake_case，便于下游解析）  
   从以下**择一**：``confirmed`` | ``amplified`` | ``narrowed`` | ``inconclusive`` | ``likely_false_positive``  
   - ``confirmed``：链与风险假设高度一致，影响面可被链支撑。  
   - ``amplified``：链显示影响面**大于**初步描述。  
   - ``narrowed``：链存在但实际影响面**小于**初步假设或热点不在风险点。  
   - ``inconclusive``：证据不足、图未覆盖关键路径或截断严重。  
   - ``likely_false_positive``：在现有图证据下假设**明显缺乏**结构支撑（须简述理由）。

4. **优先级二次确认**  
   ``priority_review``：``unchanged`` | ``escalate`` | ``downgrade``。  
   ``priority_after``：``P0``～``P3``；无充分理由时与输入 ``risk_priority`` 相同且 ``priority_review`` 为 ``unchanged``。

5. **把握度**  
   ``confidence_after``：0～1，表示纳入传播链后的把握；须与 verdict 一致，不得高于证据能支撑的程度。

6. **排查建议（investigation）**  
   - 从 ``impactChains[].nodes``（有序）中挑出**最值得人工优先排查**的若干方法或类型/类节点；若链缺失，则主要基于本条 ``preliminaryRisks[risk_index].nodes`` 给出**最小**建议集，并在 ``focus`` 中体现依据弱。  
   - 每条建议须标注 **focus**：``upstream_entry``（偏入口/调用上游侧，按链序与 ``edgeTypes`` 语义推断；若无法区分方向，须在 ``rationale`` 中写明「按当前链序推断，建议在仓库中用 IDE/调用层级复核」）、``downstream_effect``（偏下游/连带影响）、``local_change_hotspot``（变更相关热点）、``graph_unavailable_fallback``（图未命中时的保守建议）。  
   - 每条建议须有 ``evidence_ref``：有链时用 ``{"kind":"chain","seed_index":<int>,"chain_index":<int>}``；无链或仅用初步节点时用 ``{"kind":"preliminary_node_only","risk_index":<int>}``。下标须存在于输入中。

7. **动作级建议 action_suggestions**  
   输出 2～5 条中文**动作**建议（如：检索某方法调用方、核对某类鉴权注解、补某场景集成测试），须与 ``investigation_targets`` 与 verdict 一致；``inconclusive`` 时避免过度断言的具体漏洞类型。

## 四、硬性约束
- 不得输出输入中不存在的 ``qualifiedName``、``filePath``、hop 数字或 ``results`` 下标。
- ``investigation_targets[].node`` 必须来自：① 某条 ``impactChains[].nodes``；或 ② 该条 ``preliminaryRisks[risk_index].nodes``（在图不可用或 inconclusive 时允许且应标注 ``graph_unavailable_fallback`` 等合适 ``focus``）。
- ``risk_name`` 输出须与同下标输入条目的 ``risk_name`` **完全一致**。
- 最终回复**只能是合法 JSON**；根对象**仅含一个键**：``risk_judgments``（数组）。  
- ``risk_judgments.length`` **必须等于** ``preliminaryRisks`` 数组长度，且 ``risk_index`` 与输入下标一一对应（0 起）。  
- **不要**在 JSON 前后输出说明文字、Markdown 标题或代码围栏；除 JSON 外不得有任何额外字符。

## 五、输出格式（risk_judgments 每个元素字段，仅列这些键）

- ``risk_index``：integer，0 起。  
- ``risk_name``：string，与输入同下标完全一致。  
- ``identification``：string，中文。  
- ``judgment``：string，中文。  
- ``verdict``：string，枚举之一。  
- ``priority_review``：string。  
- ``priority_after``：string，``P0``|``P1``|``P2``|``P3``。  
- ``confidence_after``：number，0～1。  
- ``evidence_refs``：array of object；元素可为 ``{"kind":"seed_index","value":<int>}`` 或 ``{"kind":"chain","seed_index":<int>,"chain_index":<int>}``；无可靠链时可为 ``[]``。  
- ``investigation_targets``：array of object，每项含：  
  - ``node``：``{"kind":"","qualifiedName":"","filePath":""}``（与输入字符串一致）  
  - ``focus``：``upstream_entry``|``downstream_effect``|``local_change_hotspot``|``graph_unavailable_fallback``  
  - ``rationale``：string，一句中文  
  - ``evidence_ref``：object（规则见上）  
- ``action_suggestions``：array of string，2～5 条。

## 六、输出形状示例（字段名与类型须一致；内容为示意）

{"risk_judgments":[{"risk_index":0,"risk_name":"鉴权相关方法变更","identification":"…","judgment":"…","verdict":"inconclusive","priority_review":"unchanged","priority_after":"P1","confidence_after":0.45,"evidence_refs":[{"kind":"seed_index","value":0}],"investigation_targets":[{"node":{"kind":"METHOD","qualifiedName":"com.example.X#handle","filePath":"src/main/java/com/example/X.java"},"focus":"upstream_entry","rationale":"链上 FIELD_ACCESS 指向该入口，建议先核对调用侧鉴权。","evidence_ref":{"kind":"chain","seed_index":0,"chain_index":0}}],"action_suggestions":["静态检索 com.example.X#handle 的调用方是否均经过统一鉴权","补充鉴权失败场景的集成测试"]}]}
"""


def build_risk_identification_prompt(
    preliminary_risks: Any,
    propagation: Any,
) -> dict:
    """
    将两段输入归一后填入模版，返回 ``{"prompt": "..."}``。

    :param preliminary_risks: 风险数组或 ``{"risks":[...]}``。
    :param propagation: 与 API 响应同形的 dict。
    """
    risks_list = _ensure_preliminary_risks_list(preliminary_risks)
    prop = _ensure_dict(propagation)
    prompt = (
        RISK_IDENTIFICATION_PROMPT_TEMPLATE.replace(
            "<<<PRELIMINARY_RISKS_JSON>>>", _json_compact(risks_list)
        ).replace("<<<PROPAGATION_JSON>>>", _json_compact(prop))
    )
    return {"prompt": prompt}


def main(preliminaryRisks: Any, propagation: Any) -> dict:
    """
    Dify 代码节点入口；参数名与常见上游 camelCase 对齐。

    :param preliminaryRisks: 数组或根对象含 ``risks``；字符串 JSON 时先 ``json.loads``（由 ``_ensure_preliminary_risks_list`` 间接处理：dict 分支外若传 str 需在 Dify 侧先转 object，或扩展本函数）。
    :param propagation: 传播响应 object；字符串时同理建议上游先解析为 object。
    """
    pr = preliminaryRisks
    if isinstance(pr, str) and pr.strip():
        try:
            pr = json.loads(pr)
        except (json.JSONDecodeError, ValueError, TypeError):
            pr = []
    pg = propagation
    if isinstance(pg, str) and pg.strip():
        try:
            pg = json.loads(pg)
        except (json.JSONDecodeError, ValueError, TypeError):
            pg = {}
    return build_risk_identification_prompt(pr, pg)

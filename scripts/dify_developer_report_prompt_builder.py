# -*- coding: utf-8 -*-
"""
将「推送给开发者的整体变更报告」LLM 所需说明与两段结构化输入拼成一条 prompt 字符串。

用法（Dify「代码」节点）：
1. 上游 A：摘要节点 LLM 输出，根对象仅含 ``summary``（string），或直接传入摘要字符串。
2. 上游 B：风险识别与判断节点 LLM 输出，根对象含 ``risk_judgments``（array），或直接传入该数组。
3. 返回 ``{"prompt": "..."}``，供下游 LLM 节点生成 JSON：含 ``title``（报告总标题字符串）与 ``report``（正文；每条风险以 ``##`` 二级标题与正文区分）。

入参约定：
- ``summary``：**str**（摘要正文）或 **dict**（须含 ``summary`` 键）；非法时当作空字符串。
- ``riskJudgments``：**list[dict]** 或 **dict**（根对象含 ``risk_judgments``）；非法时当作 ``[]``。

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


def _json_compact(obj: Any) -> str:
    """紧凑 JSON 文本，便于嵌入 prompt。"""
    return json.dumps(obj, ensure_ascii=False, separators=(",", ":"))


def _extract_summary_text(value: Any) -> str:
    """从字符串或 ``{"summary":"..."}`` 中取出摘要正文。"""
    if isinstance(value, str):
        return value.strip()
    d = _ensure_dict(value)
    s = d.get("summary")
    if isinstance(s, str):
        return s.strip()
    return ""


def _extract_risk_judgments(value: Any) -> List[Dict[str, Any]]:
    """支持根对象 ``{"risk_judgments":[...]}`` 或直接数组。"""
    if isinstance(value, dict):
        rj = value.get("risk_judgments")
        if isinstance(rj, list):
            return [x for x in rj if isinstance(x, dict)]
        return []
    return _ensure_list_of_dicts(value)


# ---------------------------------------------------------------------------
# 开发者报告 Prompt 模版（占位符勿改）
# ---------------------------------------------------------------------------

DEVELOPER_REPORT_PROMPT_TEMPLATE = """你是一名资深研发负责人，要把一次代码变更的「摘要」与「风险识别结论」整理成**推送给开发者**的整体说明。

## 〇、事实性与禁止编造（优先级最高，违反即视为不合格）

1. **唯一事实源**：你只能复述、归纳、改写 ``summaryText`` 与 ``riskJudgments`` 中**已出现**的信息；不得把常识、行业通识或模型「推测」当作本次变更中已发生的事实写进报告。
2. **禁止捏造**：不得虚构或补充输入中**不存在**的仓库路径、类名、方法名、``qualifiedName``、``filePath``、提交哈希、作者、行数统计、依赖版本、CVE 编号、线上事故、监控结论、测试结果或任何具体数字与专有名词；**不得**编造传播链 hop、图数据库命中情况或 API 行为。
3. **引用边界**：凡涉及代码位置、节点、建议动作，须能在输入字段中找到依据（如 ``investigation_targets``、``action_suggestions``、``identification``、``judgment`` 等）；若输入未给出具体路径或动作，只写方向性表述并明确写「输入未提供具体位置/步骤」，**不得**自行补全为看似真实的细节。
4. **不确定表述**：证据不足、字段缺失或与摘要明显无法对齐时，使用保守措辞（如「依据当前输入无法判断」「结论以识别节点输出为准」），**禁止**用肯定语气掩盖信息缺口。

## 一、输入说明（JSON 片段）

### summaryText（string）
来自摘要节点；可能为空。若为空，报告中「摘要」部分用一两句如实说明「摘要缺失」，并仅基于风险条目组织后文。

<<<SUMMARY_TEXT_JSON>>>

### riskJudgments（array）
来自 ``dify_risk_identification_prompt_builder`` 所约定 LLM 输出的 ``risk_judgments``；元素字段可能含：
``risk_index``、``risk_name``、``identification``、``judgment``、``verdict``、``priority_after``、``confidence_after``、``investigation_targets``、``action_suggestions`` 等。请按 ``risk_index`` 升序（若缺失则按数组顺序）覆盖**全部**条目，不得遗漏。

<<<RISK_JUDGMENTS_JSON>>>

## 二、正文结构（逻辑顺序；允许适度换行）

按以下顺序组织输出；**总标题**只写入 JSON 的 ``title`` 字段，**不要**写入 ``report`` 字符串。

### ``title``（与 ``report`` 分开）

用一句简短话概括本次通知性质（例如「本次变更评审与风险提示」类表述）；**不要**使用井号、星号、尖括号等标记；长度建议不超过 40 字。

### ``report`` 正文顺序

在**不破坏可读性**前提下**允许**适度换行（摘要一段后可换行；**不要**连续多个空行）。

1. **摘要**：用若干句复述 ``summaryText`` 的要点；若摘要为空则如实说明。**不要**在 ``report`` 开头重复 ``title`` 或另写一行总标题。
2. **风险循环**：对 ``riskJudgments`` **每一条**依次输出两段结构：
   - **二级标题（单独一行）**：必须以 Markdown 二级标题形式书写，即行首为 ``## ``（两个井号加一个半角空格），后接该条 ``risk_name``（与输入**完全一致**，不得改写）。
   - **标题下一行起为正文**：写清四件事（句内用分号或句号串联即可；**勿**用 ``1.``、``-``、``*`` 等列表排版）：
     - **分析**：综合 ``identification`` 与 ``judgment``，说明在代码结构或影响面上「具体指什么、证据支持何种结论」；
     - **原因**：把 ``verdict``、``priority_after``、``confidence_after`` 的含义用自然语言交代清楚（``verdict`` 等枚举词可保留英文，但须用中文解释语义）；
     - **排查建议**：把 ``investigation_targets`` 中的关注点与 ``action_suggestions`` 中的动作建议，合并为可执行的短句，避免堆砌字段名。

若某条缺少部分字段，用保守措辞说明「输入未提供」，不得臆测或补全虚构细节。

## 三、版式与编码硬性约束

1. ``report`` 字符串：除汉字、数字、常用中文标点及**适度换行符**外，**仅允许**在每条风险小节使用**一行**二级标题，格式为单独一行且行首为 ``## `` + 该条 ``risk_name``；除此之外**禁止**其他 Markdown（如一级标题 ``#``、三级及以下标题、``*``、``-`` 列表、反引号、代码围栏）、**禁止** HTML 标签与实体、**禁止** URL 自动链接语法。
2. 变量名、方法名、类名、``qualifiedName``、``filePath``、枚举值等**本身为英文**的可原样保留，周围用中文说明即可。
3. ``title`` 与 ``report`` 的字符总长度（按 Unicode 码点计，含标点、空格与换行符）**不得超过 2000**；若素材过多，**优先压缩**各风险正文中的重复表述与次要细节，但**不得删除**任一 ``risk_name`` 对应的小节（须保留其 ``##`` 标题行），且须保留每条至少一条可执行建议。
4. 语气专业、克制、面向开发者；避免耸人听闻或与输入证据不符的断言。

## 四、输出格式（必须严格遵守）

1. 最终回复**只能是合法 JSON**，根对象**须且仅须**含两个键（均为小写键名、值为字符串）：``title``、``report``。
2. ``title``：总标题一句，**不得**写入 ``report``。
3. ``report``：仅含摘要与各风险小节；换行须按 JSON 字符串规则转义为 ``\\n``；每条风险前须有单独一行的 ``## `` + ``risk_name``。
4. 两个字符串的值均须符合 JSON 转义规则，保证可被标准 JSON 解析器一次解析成功。
5. **不要**在 JSON 前后输出说明文字、Markdown 标题或代码围栏；除上述 JSON 文本外不得有任何额外字符。

## 五、输出形状示例（字段名与类型须一致；内容为示意）

{"title":"本次变更评审与风险提示","report":"摘要：……\\n## 鉴权相关方法变更\\n……原因……建议……\\n## 另一风险名称\\n……"}
"""


def build_developer_report_prompt(
    summary: Any,
    risk_judgments: Any,
) -> dict:
    """
    将摘要正文与 ``risk_judgments`` 嵌入模版，返回 ``{"prompt": "..."}``。

    :param summary: 摘要字符串或 ``{"summary":"..."}``。
    :param risk_judgments: 判断数组或 ``{"risk_judgments":[...]}``。
    """
    summary_text = _extract_summary_text(summary)
    rj = _extract_risk_judgments(risk_judgments)
    # summaryText 在模版中以 JSON 字符串形式嵌入，避免未转义引号破坏模版
    summary_payload = summary_text
    prompt = (
        DEVELOPER_REPORT_PROMPT_TEMPLATE.replace(
            "<<<SUMMARY_TEXT_JSON>>>", _json_compact(summary_payload)
        ).replace("<<<RISK_JUDGMENTS_JSON>>>", _json_compact(rj))
    )
    return {"prompt": prompt}


def main(summary: Any, riskJudgments: Any) -> dict:
    """
    Dify 代码节点入口；参数名与常见上游 camelCase 对齐。

    :param summary: 摘要字符串或含 ``summary`` 的对象；字符串若为整段 JSON 则先解析。
    :param riskJudgments: ``risk_judgments`` 数组或含该键的根对象；字符串 JSON 时先 ``json.loads``。
    """
    sm = summary
    if isinstance(sm, str) and sm.strip().startswith("{"):
        try:
            sm = json.loads(sm)
        except (json.JSONDecodeError, ValueError, TypeError):
            pass
    rj = riskJudgments
    if isinstance(rj, str) and rj.strip():
        try:
            rj = json.loads(rj)
        except (json.JSONDecodeError, ValueError, TypeError):
            rj = []
    return build_developer_report_prompt(sm, rj)

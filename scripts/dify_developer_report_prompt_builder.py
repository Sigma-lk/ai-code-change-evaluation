# -*- coding: utf-8 -*-
"""
将「推送给开发者的整体变更报告」LLM 所需说明与两段结构化输入拼成一条 prompt 字符串。

用法（Dify「代码」节点）：
1. 上游 A：摘要节点 LLM 输出，根对象含 ``summary``（string）等，或直接传入摘要字符串；摘要**仅作风险叙述的事实对齐参考**，不要求写入最终报告。
2. 上游 B：风险识别与判断节点 LLM 输出，根对象含 ``risk_judgments``（array），或直接传入该数组。
3. 返回 ``{"prompt": "..."}``，供下游 LLM 节点生成 JSON：根对象**仅**含 ``report``（纯文本风格正文，无 Markdown/HTML）。

入参约定：
- ``summary``：**str**（摘要正文）或 **dict**（须含 ``summary`` 键；可另含 ``title``，本脚本只读取 ``summary``）；非法时当作空字符串。
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
    """从字符串或 ``{"summary":"..."}`` / ``{"title":"...","summary":"..."}`` 中取出摘要正文。"""
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

DEVELOPER_REPORT_PROMPT_TEMPLATE = """你是一名资深研发负责人，依据「风险识别结论」撰写**推送给开发者**的风险说明；``summaryText`` 仅供你在脑中核对变更背景与措辞一致性，**不得**写入最终输出。

〇、事实性与禁止编造（优先级最高，违反即视为不合格）

1. **唯一事实源**：风险正文只能复述、归纳、改写 ``riskJudgments`` 中**已出现**的信息，并可借助 ``summaryText`` 判断是否与变更语境矛盾，但**禁止**把摘要段落复述进 ``report``。不得把常识、行业通识或模型「推测」当作本次变更中已发生的事实写进报告。
2. **禁止捏造**：不得虚构或补充输入中**不存在**的仓库路径、类名、方法名、``qualifiedName``、``filePath``、提交哈希、作者、行数统计、依赖版本、CVE 编号、线上事故、监控结论、测试结果或任何具体数字与专有名词；**不得**编造传播链 hop、图数据库命中情况或 API 行为。
3. **引用边界**：凡涉及代码位置、节点、建议动作，须能在输入字段中找到依据（如 ``investigation_targets``、``action_suggestions``、``identification``、``judgment`` 等）；若输入未给出具体路径或动作，只写方向性表述并明确写「输入未提供具体位置/步骤」，**不得**自行补全为看似真实的细节。
4. **不确定表述**：证据不足、字段缺失时，使用保守措辞（如「依据当前输入无法判断」「结论以识别节点输出为准」），**禁止**用肯定语气掩盖信息缺口。

一、输入说明（JSON 片段）

summaryText（string）
来自摘要节点；可能为空。**仅作你撰写风险分析时的内部参考**，用于与 ``identification`` / ``judgment`` 等对齐语境；**禁止**在 ``report`` 中出现「摘要」「概述」类段落或对 ``summaryText`` 的复述、摘抄。

<<<SUMMARY_TEXT_JSON>>>

riskJudgments（array）
来自 ``dify_risk_identification_prompt_builder`` 所约定 LLM 输出的 ``risk_judgments``；元素字段可能含：
``risk_index``、``risk_name``、``identification``、``judgment``、``verdict``、``priority_after``、``confidence_after``、``investigation_targets``、``action_suggestions`` 等。请按 ``risk_index`` 升序（若缺失则按数组顺序）覆盖**全部**条目，不得遗漏。

<<<RISK_JUDGMENTS_JSON>>>

二、report 正文（仅风险分析；禁止摘要）

``report`` 中**只写**按序编号的风险条目，**不要**写总标题、**不要**写摘要、**不要**写开场白或结语套话。每条风险须严格按下列版式（换行符在 JSON 中写作 ``\\n``；条目之间用**一个**空行分隔即可，即连续两个换行 ``\\n\\n``，**不要**连续多个空行）：

首行固定形态（半角数字序号 + 半角句点 + 半角空格；名称与评级外使用中文全角括号「（」「）」）：
``序号``. ``（`` + 该条 ``risk_name``（与输入**完全一致**，不得改写） + ``）（`` + 评级 + ``）``
下一行起为**正文**（须单独起段：综合 ``identification`` 与 ``judgment`` 说明指什么、证据支持何种结论；自然语言交代 ``verdict``、``priority_after``、``confidence_after`` 的含义，枚举词可保留英文但须用中文解释语义）
再换行后为**排查建议**（须单独起段：合并 ``investigation_targets`` 与 ``action_suggestions`` 为可执行短句，避免堆砌字段名）

示例形态（仅说明结构；勿照抄措辞）：``1. （鉴权相关方法变更）（高优先级）`` 后换行写正文，再换行写排查建议；下一条为 ``2. （另一风险名称）（中优先级）``，以此类推。

其中「评级」为简短中文短语，优先依据 ``priority_after`` 与 ``verdict`` 概括；若缺失则写 ``（输入未提供评级）`` 等保守表述。序号从 1 起，与排序后的条目一一对应。

若某条缺少部分字段，在正文或建议中用保守措辞说明「输入未提供」，不得臆测或补全虚构细节。

三、版式与编码硬性约束

1. ``report`` 字符串：使用汉字、数字、常用中文标点、阿拉伯数字序号与半角句点（如 ``1.``）、换行符；**禁止** Markdown（包括但不限于 ``#``、``##``、星号强调、``-`` / ``*`` 列表、反引号、代码围栏、链接语法）、**禁止** HTML 标签与实体。
2. 变量名、方法名、类名、``qualifiedName``、``filePath``、枚举值等**本身为英文**的可原样保留，周围用中文说明即可。
3. ``report`` 的字符总长度（按 Unicode 码点计，含标点、空格与换行符）**不得超过 3000**；若素材过多，**优先压缩**各条正文的重复表述与次要细节，但**不得删除**任一风险条目，且每条须保留至少一条可执行排查建议。
4. 语气专业、克制、面向开发者；避免耸人听闻或与输入证据不符的断言。

四、输出格式（必须严格遵守）

1. 最终回复**只能是合法 JSON**，根对象**须且仅须**含一个键（小写键名、值为字符串）：``report``。**不要**输出 ``title`` 键或任何总标题字段。
2. ``report``：仅含上述编号风险正文；换行须按 JSON 字符串规则转义为 ``\\n``。
3. 字符串值须符合 JSON 转义规则，保证可被标准 JSON 解析器一次解析成功。
4. **不要**在 JSON 前后输出说明文字、井号标题或代码围栏；除上述 JSON 文本外不得有任何额外字符。

五、输出形状示例（字段名与类型须一致；内容为示意）

{"report":"1. （鉴权相关方法变更）（高优先级）\\n……分析……\\n……排查建议……\\n\\n2. （另一风险名称）（中优先级）\\n……\\n……"}
"""


def build_developer_report_prompt(
    summary: Any,
    risk_judgments: Any,
) -> dict:
    """
    将摘要正文与 ``risk_judgments`` 嵌入模版，返回 ``{"prompt": "..."}``。

    :param summary: 摘要字符串或含 ``summary`` 的对象（可与 ``title`` 同存）；摘要仅嵌入模版供模型参考，不要求出现在输出中。
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

    :param summary: 摘要字符串或含 ``summary`` 的对象（可含 ``title``）；字符串若为整段 JSON 则先解析；摘要仅用于 prompt 内参考。
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

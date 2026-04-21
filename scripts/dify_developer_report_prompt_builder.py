# -*- coding: utf-8 -*-
"""
将「推送给开发者的整体变更报告」LLM 所需说明与两段结构化输入拼成一条 prompt 字符串。

用法（Dify「代码」节点）：
1. 上游 A：摘要节点 LLM 输出，根对象含 ``summary``（string）等，或直接传入摘要字符串；摘要**仅作风险叙述的事实对齐参考**，不要求写入最终报告。
2. 上游 B：风险识别与判断节点 LLM 输出，根对象含 ``risk_judgments``（array），或直接传入该数组。
3. 返回 ``{"prompt": "..."}``，供下游 LLM 节点生成 JSON：根对象**仅**含 ``report``（**HTML 邮件正文片段**，用于由服务端包入 ``<body>`` 后发送，非整页 HTML）。

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
4. **不确定表述**：证据不足、字段缺失时，使用保守措辞（如「依据当前输入无法判断」），**禁止**用肯定语气掩盖信息缺口；与「二」中**禁止结论性收尾**一致，不得用「结论以…为准」类话术充当正文结论。

一、输入说明（JSON 片段）

summaryText（string）
来自摘要节点；可能为空。**仅作你撰写风险分析时的内部参考**，用于与 ``identification`` / ``judgment`` 等对齐语境；**禁止**在 ``report`` 中出现「摘要」「概述」类段落或对 ``summaryText`` 的复述、摘抄。

<<<SUMMARY_TEXT_JSON>>>

riskJudgments（array）
来自 ``dify_risk_identification_prompt_builder`` 所约定 LLM 输出的 ``risk_judgments``；元素字段可能含：
``risk_index``、``risk_name``、``identification``、``judgment``、``verdict``、``priority_after``、``confidence_after``、``investigation_targets``、``action_suggestions`` 等。输出 ``report`` 时须**重新排序**后覆盖**全部**条目，不得遗漏：**主序**按 ``priority_after``（如 Px）中 **x 数字升序**（P1 先于 P2，数字越小越优先）；无法解析或缺失的条目排在**已可解析条目之后**，相互间保持输入数组中的相对顺序。**次序**在同一 ``priority_after``（含同缺失档）内按 ``confidence_after`` **数值降序**（越大越靠前）；缺失置信度的排在**同档内有数值者之后**。**``priority_after`` 与 ``confidence_after`` 均相同**（含均缺失且同属一档）的多条，顺序可任意（等价随机），但不得漏项。

<<<RISK_JUDGMENTS_JSON>>>

二、report 正文（HTML 邮件片段；仅风险分析；禁止摘要）

``report`` 为 **HTML 片段**：将来由发送端插入已有 HTML 文档的 ``<body>`` 与 ``</body>`` **之间**（``head``/``body``/``html``/``DOCTYPE`` 及 charset 等已由服务端统一提供）。你**只生成 body 内正文**，**禁止**输出 ``<!DOCTYPE``、``<html``、``<head``、``<body``、``</body>``、``</html>``、``<meta charset`` 等文档壳或重复声明编码。

内容要求：``report`` 中**只呈现**按序编号的风险条目，**不要**总标题、**不要**摘要段落、**不要**开场白/结语套话。条目顺序须遵守上文「重新排序」规则，覆盖 **全部** ``risk_judgments``，不得遗漏。

每条风险在 HTML 中须体现三块信息（可用多行 ``<tr><td>`` 或段落性 ``<td>`` 分段，保持阅读顺序清晰）：

1. **标题行**（半角数字序号 + 半角句点 + 半角空格）：``序号``. ``risk_name``（ … ）。其中 ``risk_name`` 与输入**完全一致**，不得改写；紧跟其后的**一对全角括号「（」「）」内仅写** ``priority_after`` 与 ``confidence_after``：**``priority_after``** 以半角形式写出（如 ``P2``）；**置信度**固定写为「置信度为」+ 半角数字（与输入一致，如 ``0.35``）；二者用全角逗号「，」分隔；左括号后保留半角空格，整体示例：（ P2，置信度为 0.35）。**不要**在括号内或 ``risk_name`` 与括号之间再写「高优先级」「中优先级」等赘述。缺失 ``priority_after`` 或 ``confidence_after`` 时，括号内对应位置写保守说明（如「输入未提供优先级」「输入未提供置信度」），不得臆造 Px 或数值。
2. **分析**：综合 ``identification`` 与 ``judgment`` 做证据与因果说明；**禁止**给出结论性收尾或裁决式表述（例如「结论为…」「综上判定为…」「最终结论…」「verdict 为…」及对 ``verdict`` 的显式复述/翻译当作定论）；**禁止**在分析段再次重复标题括号内的 Px 与置信度数字（避免冗余）。可客观转述证据强弱、信息缺口，但不以「结论」话术收束。
3. **排查建议**：合并 ``investigation_targets`` 与 ``action_suggestions`` 为可执行短句，避免堆砌字段名

**类名与方法名书写（强制）**：正文中**禁止**使用 ``类名#方法名`` 形式指代代码位置；一律写为「**类名**的**方法名**()」：无参方法写空括号 ``()``；有参方法写半角括号及参数表，与输入或可追溯字段一致即可（示例：``InfrastructureConfig的restTemplate()``、``DifyWorkflowHttpConfig的difyWorkflowRestTemplate(DifyWorkflowProperties)``）。若输入本身仅有 ``#`` 记法，输出前须改写为上述「的…()」形式。

序号从 1 起（排序后的第一条为 1）。字段缺失时在对应块内说明「输入未提供」，不得臆测细节。

三、HTML 邮件兼容与版式（优先级高；简单坚固优于花哨）
**A. 布局与 CSS**

1. **以 ``<table>`` 为主布局**：用嵌套 ``<table>`` 搭出结构；最外层可用 ``width="100%"`` 的表格，内部再嵌 **主内容区** 宽度 **650px**（600–800px 范围内；优先 650px）的表格承载正文。**不要**用 ``<div>`` 做主要分栏布局。
2. **行内样式为主**：``width``、``padding``、``margin``、``background-color``、``color``、``font-size``、``line-height``、``font-family``、``text-align``、``border`` 等关键样式写在对应标签的 ``style="..."`` 中。
3. **慎用 ``<style>``**：你的片段位于 ``body`` 内；即便写 ``<style>`` 也可能被 Gmail 等剥离。**不要**依赖 ``<style>`` 呈现关键版式；若使用，仅作非关键增强（如部分客户端的媒体查询），且不得作为唯一手段。
4. **禁止外部样式表**：不得 ``<link rel="stylesheet"`` 或引用外部 CSS。
5. **少用** ``position``/``float``/``flex``/``grid``/``transform``/``filter`` 等现代布局与特效作为结构依赖；**避免**依赖 ``background-image``（Outlook 等支持差）；需要色块背景优先 ``bgcolor`` 与 ``style`` 中的 ``background-color`` 在 ``<td>`` 上配合使用。

**B. 设计与内容**

1. 主内容区固定宽度（如上 650px），外层表格可 ``width="100%"`` 且内容区 ``align="center"``，使桌面与移动端观感稳定。
2. **图片**（若无可靠绝对 URL 则**不要**插入 ``<img>``，用文字排版即可）：若使用图片，须 **https 绝对 URL**；``<img>`` 上写清 ``width``/``height``（属性）与 ``alt``；**禁止**「整封邮件一张大图」式排版，须图文/文字可独立阅读。
3. **字体与颜色**：``font-family`` 使用网络安全字体栈，例如 ``Arial, Helvetica, sans-serif`` 或 ``Georgia, Times New Roman, serif``，并以 ``sans-serif``/``serif`` 结尾；颜色用 **6 位十六进制**（如 ``#333333``），保证对比度易读。

**C. 交互与链接**

1. **禁止 JavaScript**：不得出现 ``<script>`` 及任何脚本、事件属性依赖脚本的交互。
2. **不用动画**：不依赖 CSS 动画/过渡。
3. **链接**：若写链接，须 **https 完整绝对 URL**；为 ``<a>`` 设置明确的 ``color`` 与 ``text-decoration``（如保留可识别的下划线样式），避免用户看不出可点击。

四、编码与其它硬性约束

1. ``report`` 为 **HTML 字符串**（片段）。正文中**不要**使用 Markdown 语法（如 ``#``、``##``、``**``、``-`` 列表、反引号围栏等）代替 HTML 结构；代码/标识符如需强调，用 ``<span style="...">`` 等行内标签即可。
2. 变量名、方法名、类名、``qualifiedName``、``filePath``、枚举值等**本身为英文**的可原样保留，周围用中文说明即可。
3. ``report`` 的字符总长度（按 Unicode 码点计，含标签、样式、标点与空白）**不得超过 8000**；若素材过多，**优先压缩**重复表述与次要装饰性 HTML，但**不得删除**任一风险条目，且每条须保留至少一条可执行排查建议。
4. 语气专业、克制、面向开发者；避免耸人听闻或与输入证据不符的断言。
5. JSON 内嵌 HTML 时，须正确转义 JSON 特殊字符（尤其是属性中的双引号写作 ``\"``、反斜杠写作 ``\\`` 等），保证整段回复可被标准 JSON 解析器**一次**解析成功。

五、输出格式（必须严格遵守）

1. 最终回复**只能是合法 JSON**，根对象**须且仅须**含一个键（小写键名、值为字符串）：``report``。**不要**输出 ``title`` 键或任何总标题字段。
2. ``report``：仅含上述 **HTML 片段**（无文档最外层标签）。
3. **不要**在 JSON 前后输出说明文字、井号标题或 Markdown 代码围栏；除上述 JSON 文本外不得有任何额外字符。

六、输出形状示例（字段名与类型须一致；``report`` 内为高度简化的 table 示意，真实输出须满足第三节约束并写满全部风险条目）

{"report":"<table role=\\"presentation\\" width=\\"100%\\" cellpadding=\\"0\\" cellspacing=\\"0\\" border=\\"0\\" style=\\"margin:0;padding:0;\\"><tr><td align=\\"center\\" style=\\"padding:16px 8px;\\"><table role=\\"presentation\\" width=\\"650\\" cellpadding=\\"0\\" cellspacing=\\"0\\" border=\\"0\\" style=\\"max-width:650px;font-family:Arial,Helvetica,sans-serif;color:#333333;\\"><tr><td style=\\"font-size:16px;line-height:1.5;padding-bottom:12px;\\"><strong>1. 鉴权相关方法变更（ P1，置信度为 0.82）</strong></td></tr><tr><td style=\\"font-size:14px;line-height:1.6;padding-bottom:12px;\\">……分析（无结论性收尾；代码位置用「类名的method()」）……</td></tr><tr><td style=\\"font-size:14px;line-height:1.6;padding-bottom:24px;border-bottom:1px solid #eeeeee;\\">……排查建议……</td></tr><tr><td style=\\"height:16px;\\"></td></tr><tr><td style=\\"font-size:16px;line-height:1.5;padding-bottom:12px;\\"><strong>2. 另一风险名称（ P2，置信度为 0.45）</strong></td></tr><tr><td style=\\"font-size:14px;line-height:1.6;\\">……</td></tr></table></td></tr></table>"}
"""


def build_developer_report_prompt(
    summary: Any,
    risk_judgments: Any,
) -> dict:
    """
    将摘要正文与 ``risk_judgments`` 嵌入模版，返回 ``{"prompt": "..."}``。

    :param summary: 摘要字符串或含 ``summary`` 的对象（可与 ``title`` 同存）；摘要仅嵌入模版供模型参考，不要求出现在输出中。
    :param risk_judgments: 判断数组或 ``{"risk_judgments":[...]}``。
    :return: ``{"prompt": "..."}``，下游 LLM 应输出仅含 ``report``（HTML 邮件正文片段）的 JSON。
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
    :return: ``{"prompt": "..."}``，供下游生成 HTML 片段写入 ``report``。
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

# -*- coding: utf-8 -*-
"""
将 **纯文本摘要** 与 **HTML 邮件正文片段** 拼成一段可交给 ``MailApi`` / ``HtmlMailPort`` 的 ``htmlBody``（服务端会再包一层含 charset 的 ``<html><head>…<body>``）。

用法（Dify「代码」节点）：
1. ``summary``：上游摘要节点输出的纯文本（可含换行）。
2. ``report``：开发者报告等节点输出的 HTML 片段（表格布局等，**不要**含最外层 ``<html>``/``<body>``）。
3. 返回 ``{"result": "..."}``，将 ``result`` 作为发信请求体中的 HTML 正文字段即可。

说明：纯文本会先 ``html.escape``，并把换行转为 ``<br>``，再与 ``report`` 用 **一个** ``<br>`` 衔接（等价于原先纯文本拼接时的换行分隔），避免摘要中的 ``<>&`` 破坏整段 HTML。

当摘要与报告 **同时存在** 时，摘要多包一层与常见开发者报告 HTML 一致的「全宽居中栏 + 650px 内层 + Arial」表格，避免上半截整宽靠左、下半截居中窄栏造成的版式割裂（问题来自两段 HTML 结构不同，而非转义逻辑）。
"""

from __future__ import annotations

import html
from typing import Any


def _coerce_str(value: Any) -> str:
    if value is None:
        return ""
    if isinstance(value, str):
        return value
    return str(value)


def plain_summary_to_html_fragment(summary: str) -> str:
    """
    将纯文本摘要转为可嵌入 HTML 邮件正文的片段（转义 + 换行转 ``<br>``）。
    """
    s = summary or ""
    if not s:
        return ""
    return html.escape(s, quote=False).replace("\n", "<br>")


# 与 Dify 报告节点常见外层表格一致，便于摘要与报告上下对齐、同字体同栏宽。
_MAIL_COLUMN_OUTER = (
    '<table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" '
    'style="margin:0;padding:0;">'
    '<tr><td align="center" style="padding:0 8px 8px 8px;">'
    '<table role="presentation" width="650" cellpadding="0" cellspacing="0" border="0" '
    'style="max-width:650px;font-family:Arial,Helvetica,sans-serif;color:#333333;">'
    '<tr><td style="font-size:14px;line-height:1.6;text-align:left;">'
    "{inner}"
    "</td></tr></table></td></tr></table>"
)


def _wrap_fragment_in_report_style_column(fragment: str) -> str:
    """将已生成的 HTML 片段包在与典型 report 相同的居中窄栏内（仅在有配对 report 时使用）。"""
    if not fragment:
        return ""
    return _MAIL_COLUMN_OUTER.format(inner=fragment)


def merge_mail_html_body(summary: Any, report: Any) -> dict:
    """
    拼接摘要与报告 HTML，供发信接口使用。

    :param summary: 纯文本摘要（可为 ``None``）。
    :param report: HTML 正文片段（可为 ``None``）。
    :return: ``{"result": 合并后的 HTML 字符串}``；两段均空时返回空字符串。
    """
    head = plain_summary_to_html_fragment(_coerce_str(summary))
    tail = _coerce_str(report).strip() if report is not None else ""

    if head and tail:
        body = _wrap_fragment_in_report_style_column(head) + "<br>" + tail
    else:
        body = head or tail
    return {"result": body}


def main(summary: Any, report: Any) -> dict:
    """Dify 代码节点入口；参数名与上游 camelCase / 约定对齐。"""
    return merge_mail_html_body(summary, report)

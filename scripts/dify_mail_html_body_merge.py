# -*- coding: utf-8 -*-
"""
将 **纯文本摘要** 与 **HTML 邮件正文片段** 拼成一段可交给 ``MailApi`` / ``HtmlMailPort`` 的 ``htmlBody``（服务端会再包一层含 charset 的 ``<html><head>…<body>``）。

用法（Dify「代码」节点）：
1. ``summary``：上游摘要节点输出的纯文本（可含换行）。
2. ``report``：开发者报告等节点输出的 HTML 片段（表格布局等，**不要**含最外层 ``<html>``/``<body>``）。
3. 返回 ``{"result": "..."}``，将 ``result`` 作为发信请求体中的 HTML 正文字段即可。

说明：纯文本会先 ``html.escape``，并把换行转为 ``<br>``，再与 ``report`` 用 **一个** ``<br>`` 衔接（等价于原先纯文本拼接时的换行分隔），避免摘要中的 ``<>&`` 破坏整段 HTML。
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
        body = head + "<br>" + tail
    else:
        body = head or tail
    return {"result": body}


def main(summary: Any, report: Any) -> dict:
    """Dify 代码节点入口；参数名与上游 camelCase / 约定对齐。"""
    return merge_mail_html_body(summary, report)

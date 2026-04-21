#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
调用评估服务 ``MailApi``：``POST /api/v1/mail/html``，发送 HTML 邮件。

请求体与 ``SendHtmlMailRequest`` 一致：``to``、``subject``、``htmlBody``。
服务端需已配置 ``spring.mail.host``，否则 ``MailController`` 不会注册，接口不可用。

在其它 Python 代码中 ``import`` 后调用 ``main(...)``；需要命令行解析时可调用 ``cli()``。

服务根地址固定为 ``http://md9bdeeb.natappfree.cc``（与内网穿透一致）。
"""

from __future__ import annotations

import argparse
import json
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any

BASE_URL = "http://md9bdeeb.natappfree.cc"
MAIL_HTML_PATH = "/api/v1/mail/html"
HTTP_TIMEOUT_SEC = 60.0


def main(
    to: str,
    subject: str,
    html_body: str,
) -> dict[str, Any]:
    """
    向评估服务提交发送 HTML 邮件请求。

    :param to: 收件人邮箱
    :param subject: 邮件主题（可为空字符串）
    :param html_body: HTML 片段（服务端会包裹文档壳并指定 UTF-8）
    :return: ``{"status_code": int, "body": dict | str}``；``body`` 在 JSON 解析失败时为原始响应文本
    """
    base = BASE_URL.rstrip("/")
    url = f"{base}{MAIL_HTML_PATH}"
    payload = {
        "to": to.strip(),
        "subject": subject if subject is not None else "",
        "htmlBody": html_body,
    }
    data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(
        url,
        data=data,
        method="POST",
        headers={
            "Content-Type": "application/json; charset=utf-8",
            "Accept": "application/json",
        },
    )
    try:
        with urllib.request.urlopen(req, timeout=HTTP_TIMEOUT_SEC) as resp:
            raw = resp.read().decode("utf-8")
            status = resp.getcode() or 200
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", errors="replace")
        status = e.code
    try:
        body: dict[str, Any] | str = json.loads(raw) if raw.strip() else {}
    except json.JSONDecodeError:
        body = raw
    return {"status_code": status, "body": body}


def _parse_args(argv: list[str] | None) -> argparse.Namespace:
    p = argparse.ArgumentParser(
        description="调用评估服务 POST /api/v1/mail/html 发送 HTML 邮件",
    )
    p.add_argument("--to", required=True, help="收件人邮箱")
    p.add_argument("--subject", default="", help="邮件主题")
    g = p.add_mutually_exclusive_group(required=True)
    g.add_argument("--html-body", help="HTML 片段（直接传字符串）")
    g.add_argument(
        "--html-file",
        type=Path,
        help="从文件读取 HTML 片段（UTF-8）",
    )
    return p.parse_args(argv)


def cli(argv: list[str] | None = None) -> int:
    args = _parse_args(argv)
    html = args.html_body if args.html_body is not None else args.html_file.read_text(encoding="utf-8")
    result = main(args.to, args.subject, html)
    status = result["status_code"]
    body = result["body"]
    if isinstance(body, dict):
        print(json.dumps(body, ensure_ascii=False, indent=2))
    else:
        print(body)
    return 0 if 200 <= status < 300 else 1

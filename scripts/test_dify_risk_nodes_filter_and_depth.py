# -*- coding: utf-8 -*-
"""本地运行（仓库根目录）：python3 scripts/test_dify_risk_nodes_filter_and_depth.py"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from dify_risk_nodes_filter_and_depth import (
    build_risk_propagation_request_body,
    filter_risks_and_build_propagation_input,
    main,
)


def test_filter_and_depth():
    sample = json.dumps(
        {
            "risks": [
                {
                    "risk_name": "高置信",
                    "risk_priority": "P1",
                    "nodes": [
                        {"kind": "METHOD", "qualifiedName": "a#m1", "filePath": "/p/A.java"},
                        {"kind": "METHOD", "qualifiedName": "a#m1", "filePath": "/p/A.java"},
                    ],
                    "confidence": 0.6,
                },
                {"risk_name": "低置信", "risk_priority": "P2", "nodes": [], "confidence": 0.3},
            ]
        },
        ensure_ascii=False,
    )
    r = filter_risks_and_build_propagation_input(sample)
    assert r["propagationMaxDepth"] == 30
    assert isinstance(r["nodes"], str)
    assert len(json.loads(r["nodes"])) == 1

    sample50 = {
        "risks": [
            {
                "risk_name": "x",
                "risk_priority": "P1",
                "nodes": [{"kind": "METHOD", "qualifiedName": f"m{i}", "filePath": ""} for i in range(50)],
                "confidence": 0.9,
            }
        ]
    }
    r50 = filter_risks_and_build_propagation_input(sample50)
    assert r50["propagationMaxDepth"] == 20

    sample51 = {
        "risks": [
            {
                "risk_name": "x",
                "risk_priority": "P1",
                "nodes": [{"kind": "METHOD", "qualifiedName": f"m{i}", "filePath": ""} for i in range(51)],
                "confidence": 0.9,
            }
        ]
    }
    r51 = filter_risks_and_build_propagation_input(sample51)
    assert r51["propagationMaxDepth"] == 10
    print("ok filter", r51["stats"])


def test_main_accepts_dict_without_json_loads():
    obj = {"risks": [{"risk_name": "x", "risk_priority": "P1", "nodes": [], "confidence": 0.9}]}
    body = main(obj)
    assert body == {"nodes": "[]", "propagationMaxDepth": 30}
    assert set(body.keys()) == {"nodes", "propagationMaxDepth"}


def test_request_body_subset():
    sample = json.dumps({"risks": []}, ensure_ascii=False)
    empty_body = build_risk_propagation_request_body(sample)
    assert empty_body == {"nodes": "[]", "propagationMaxDepth": 30}
    assert list(main(sample).keys()) == ["nodes", "propagationMaxDepth"]
    print("ok api subset", empty_body)


if __name__ == "__main__":
    test_filter_and_depth()
    test_main_accepts_dict_without_json_loads()
    test_request_body_subset()

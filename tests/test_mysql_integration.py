#!/usr/bin/env python3
"""
MySQL 迁移集成测试 - 验证所有 API 端点在 MySQL 下正常工作
使用真实 API 调用，无 mock data
"""
import requests
import time
import json
import os
import sys

BASE = "http://localhost:8080/api"
PROJECT_ID = None
DOC_ID = None
PASS = 0
FAIL = 0

def test(num, desc, method, url, **kwargs):
    global PASS, FAIL
    print(f"\n[TEST {num}] {desc}")
    print(f"  {method} {url}")
    try:
        if method == "GET":
            r = requests.get(url, timeout=30, **kwargs)
        elif method == "POST":
            r = requests.post(url, timeout=60, **kwargs)
        elif method == "PUT":
            r = requests.put(url, timeout=30, **kwargs)
        elif method == "DELETE":
            r = requests.delete(url, timeout=30, **kwargs)
        else:
            print(f"  Result: SKIP - 未知方法 {method}")
            return None

        ok = 200 <= r.status_code < 300
        status = "PASS" if ok else "FAIL"
        if ok:
            PASS += 1
        else:
            FAIL += 1

        body = r.text[:300] if r.text else "(empty)"
        print(f"  Status: HTTP {r.status_code}")
        print(f"  Result: {status}")
        if not ok:
            print(f"  Body: {body}")
        return r
    except Exception as e:
        FAIL += 1
        print(f"  Result: FAIL - {e}")
        return None


def main():
    global PROJECT_ID, DOC_ID

    print("=" * 60)
    print("  MySQL 迁移集成测试")
    print(f"  后端: {BASE}")
    print(f"  时间: {time.strftime('%Y-%m-%d %H:%M:%S')}")
    print("=" * 60)

    # 1. Health check
    r = test(1, "健康检查 - 验证 MySQL 连接", "GET", f"{BASE}/health")
    if r and r.status_code == 200:
        data = r.json()
        db_type = data.get("database", {}).get("type", "?")
        db_status = data.get("database", {}).get("status", "?")
        print(f"  DB Type: {db_type}, DB Status: {db_status}")
        if db_type != "MySQL":
            print(f"  WARNING: 期望 MySQL，实际 {db_type}")

    # 2. 列出项目
    r = test(2, "列出项目", "GET", f"{BASE}/projects")
    if r and r.status_code == 200:
        projects = r.json().get("data", [])
        print(f"  项目数量: {len(projects)}")
        for p in projects:
            print(f"    - [{p.get('id')}] {p.get('name')} ({p.get('status')})")
            if "生物科技" in str(p.get("name", "")):
                PROJECT_ID = p.get("id")

    # 3. 如果没有找到项目，创建一个
    if not PROJECT_ID:
        r = test(3, "创建项目 '生物科技与健康'", "POST", f"{BASE}/projects/",
                 json={"name": "生物科技与健康", "description": "生物科技行业投资分析报告"})
        if r and r.status_code == 200:
            PROJECT_ID = r.json().get("id")
            print(f"  新项目 ID: {PROJECT_ID}")
    else:
        print(f"\n[TEST 3] 项目已存在 (ID={PROJECT_ID})，跳过创建")
        print(f"  Result: PASS")

    if not PROJECT_ID:
        print("\nFATAL: 无法获取项目 ID，后续测试无法继续")
        sys.exit(1)

    headers = {"X-Project-Id": str(PROJECT_ID)}

    # 4. 项目详情
    test(4, f"获取项目详情 (ID={PROJECT_ID})", "GET",
         f"{BASE}/projects/{PROJECT_ID}/detail", headers=headers)

    # 5. 上传 PDF 文件
    pdf_path = os.path.join(os.path.dirname(os.path.dirname(__file__)),
                            "Test-Project", "1普翔医疗（清洗过）.pdf")
    if os.path.exists(pdf_path):
        with open(pdf_path, "rb") as f:
            r = test(5, "上传 PDF: 1普翔医疗（清洗过）.pdf", "POST",
                     f"{BASE}/upload/",
                     files={"file": ("1普翔医疗（清洗过）.pdf", f, "application/pdf")},
                     data={"projectId": str(PROJECT_ID), "categoryL1": "report", "docType": "pdf"},
                     headers={"X-Project-Id": str(PROJECT_ID)})
        if r and r.status_code == 200:
            data = r.json()
            # Response format: {"message":"...","id":N} or {"data":{"id":N}}
            if isinstance(data, dict):
                DOC_ID = data.get("id") or (data.get("data", {}).get("id") if isinstance(data.get("data"), dict) else None)
            print(f"  文档 ID: {DOC_ID}")
    else:
        print(f"\n[TEST 5] PDF 文件不存在: {pdf_path}")
        print(f"  Result: SKIP")

    # 6. 检查上传任务状态
    if DOC_ID:
        for attempt in range(1, 6):
            r = test(f"6.{attempt}", f"检查上传任务状态 (尝试 {attempt}/5)",
                     "GET", f"{BASE}/upload/task/{DOC_ID}", headers=headers)
            if r and r.status_code == 200:
                status = r.json().get("status", r.json().get("data", {}).get("status", ""))
                print(f"  任务状态: {status}")
                if status in ("done", "completed", "extracted"):
                    break
            time.sleep(5)
    else:
        print("\n[TEST 6] 无文档 ID，跳过任务状态检查")

    # 7. 列出文档
    r = test(7, "列出项目文档", "GET",
             f"{BASE}/documents", params={"projectId": PROJECT_ID}, headers=headers)
    if r and r.status_code == 200:
        data = r.json()
        records = data.get("data", {}).get("records", data.get("records", []))
        print(f"  文档数量: {len(records)}")

    # 8. 列出知识词条
    r = test(8, "列出知识词条", "GET",
             f"{BASE}/knowledge-entries", params={"projectId": PROJECT_ID}, headers=headers)
    if r and r.status_code == 200:
        data = r.json()
        records = data.get("data", {}).get("records", data.get("records", []))
        print(f"  词条数量: {len(records)}")

    # 9. 向量索引统计
    test(9, "向量索引统计", "GET", f"{BASE}/vector/stats", headers=headers)

    # 10. QA 智能问答
    r = test(10, "智能问答 - 普翔医疗主营业务", "POST",
             f"{BASE}/qa-chat/ask",
             json={"question": "普翔医疗的主营业务是什么?"},
             headers={**headers, "Content-Type": "application/json"})
    if r and r.status_code == 200:
        data = r.json()
        answer = str(data.get("data", {}).get("answer", data.get("answer", "")))[:150]
        print(f"  回答摘要: {answer}...")

    # 11. 知识图谱
    test(11, "知识图谱数据", "GET",
         f"{BASE}/kg/graph", params={"projectId": PROJECT_ID}, headers=headers)

    # 12. 系统设置
    test(12, "获取系统设置", "GET", f"{BASE}/settings", headers=headers)

    # 13. LLM 配置列表
    r = test(13, "LLM 配置列表", "GET", f"{BASE}/llm-configs", headers=headers)
    if r and r.status_code == 200:
        data = r.json()
        configs = data if isinstance(data, list) else data.get("data", [])
        print(f"  LLM 配置数量: {len(configs)}")

    # 14. Dashboard 统计
    test(14, "Dashboard 全局统计", "GET", f"{BASE}/dashboard/stats", headers=headers)

    # 15. 统一搜索
    test(15, "统一搜索 '医疗'", "GET",
         f"{BASE}/search", params={"q": "医疗"}, headers=headers)

    # 16. 决策 CRUD
    r = test(16, "创建决策", "POST", f"{BASE}/decisions/",
             json={"title": "测试决策", "decisionType": "investment",
                   "score": 8.5, "projectId": PROJECT_ID},
             headers={**headers, "Content-Type": "application/json"})
    decision_id = None
    if r and r.status_code == 200:
        decision_id = r.json().get("id") or r.json().get("data", {}).get("id") if isinstance(r.json().get("data"), dict) else r.json().get("id")
    if decision_id:
        test(16.1, f"获取决策 (ID={decision_id})", "GET",
             f"{BASE}/decisions/{decision_id}", headers=headers)
        test(16.2, f"更新决策 (ID={decision_id})", "PUT",
             f"{BASE}/decisions/{decision_id}",
             json={"title": "测试决策-已更新", "score": 9.0},
             headers={**headers, "Content-Type": "application/json"})
        test(16.3, f"删除决策 (ID={decision_id})", "DELETE",
             f"{BASE}/decisions/{decision_id}", headers=headers)

    # 17. 文件类型列表
    test(17, "获取支持的文件类型", "GET", f"{BASE}/upload/file-types", headers=headers)

    # 18. 资料库列表
    test(18, "获取资料库列表", "GET", f"{BASE}/upload/libraries", headers=headers)

    # Summary
    print("\n" + "=" * 60)
    print(f"  测试完成！")
    print(f"  PASS: {PASS}")
    print(f"  FAIL: {FAIL}")
    print(f"  总计: {PASS + FAIL}")
    print("=" * 60)

    if FAIL > 0:
        sys.exit(1)


if __name__ == "__main__":
    main()

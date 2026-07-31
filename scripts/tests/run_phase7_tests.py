#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""issueFlow Phase 7 接口测试实跑脚本（与 phase7.postman_collection.json 断言等价）。

用法::

    python run_phase7_tests.py [--base-url http://10.55.3.23:18082/api]

设计说明：
- 只做读操作，不写入/修改任何生产数据。
- 每条断言独立记录，单条失败不影响其余用例继续执行。
- 无法在当前数据条件下验证的断言标记为 SKIP，不计入失败（例如 admin 无邮箱时的脱敏值校验）。
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import dataclass, field
from typing import Any, Callable

import requests

DEFAULT_BASE_URL = "http://10.55.3.23:18082/api"
TIMEOUT = 20

PASS, FAIL, SKIP = "PASS", "FAIL", "SKIP"


@dataclass
class Check:
    """单条断言结果。"""

    name: str
    status: str
    actual: str = ""
    reason: str = ""


@dataclass
class CaseResult:
    """单个用例（一次请求 + N 条断言）的结果。"""

    code: str
    title: str
    request: str
    checks: list[Check] = field(default_factory=list)
    evidence: str = ""
    raw: str = ""

    @property
    def status(self) -> str:
        if any(c.status == FAIL for c in self.checks):
            return FAIL
        if not self.checks:
            return FAIL
        return PASS

    def check(self, name: str, ok: bool, actual: Any = "", reason: str = "") -> None:
        self.checks.append(
            Check(name, PASS if ok else FAIL, _short(actual), reason)
        )

    def skip(self, name: str, actual: Any = "", reason: str = "") -> None:
        self.checks.append(Check(name, SKIP, _short(actual), reason))


def _short(value: Any, limit: int = 220) -> str:
    text = value if isinstance(value, str) else json.dumps(value, ensure_ascii=False)
    return text if len(text) <= limit else text[:limit] + "…"


class Phase7Suite:
    """Phase 7 测试套件执行器。"""

    def __init__(self, base_url: str) -> None:
        self.base_url = base_url.rstrip("/")
        self.session = requests.Session()
        self.token: str | None = None
        self.results: list[CaseResult] = []

    # ---------------- 基础设施 ----------------

    def _headers(self) -> dict[str, str]:
        return {"Authorization": f"Bearer {self.token}"} if self.token else {}

    def _run(self, case: CaseResult, method: str, path: str, **kwargs: Any) -> dict | None:
        """发请求并做通用断言（HTTP 200 + 业务 code 200），返回 JSON body。"""
        url = f"{self.base_url}{path}"
        case.request = f"{method} {url}"
        try:
            resp = self.session.request(
                method, url, headers=self._headers(), timeout=TIMEOUT, **kwargs
            )
        except requests.RequestException as exc:  # 网络层失败
            case.check("HTTP 可达", False, str(exc), "请求异常")
            return None

        case.raw = resp.text
        case.check("HTTP 200", resp.status_code == 200, resp.status_code)
        try:
            body = resp.json()
        except ValueError:
            case.check("响应为合法 JSON", False, resp.text[:200])
            return None
        case.check("业务 code = 200", body.get("code") == 200, body.get("code"))
        return body

    def add(self, code: str, title: str, fn: Callable[[CaseResult], None]) -> CaseResult:
        case = CaseResult(code=code, title=title, request="")
        try:
            fn(case)
        except Exception as exc:  # 断言逻辑自身异常也要落盘，避免整轮中断
            case.check("用例执行无异常", False, f"{type(exc).__name__}: {exc}")
        self.results.append(case)
        return case

    # ---------------- 用例 ----------------

    def t1_login(self, case: CaseResult) -> None:
        url = f"{self.base_url}/auth/login"
        case.request = f"POST {url}"
        resp = self.session.post(
            url,
            json={"username": "admin", "password": "admin123"},
            timeout=TIMEOUT,
        )
        case.raw = resp.text
        case.check("HTTP 200", resp.status_code == 200, resp.status_code)
        body = resp.json()
        case.check("业务 code = 200", body.get("code") == 200, body.get("code"))
        token = (body.get("data") or {}).get("token")
        case.check("data.token 非空", bool(token), f"len={len(token or '')}")
        self.token = token
        case.evidence = f"token 长度={len(token or '')}, 用户={((body.get('data') or {}).get('userInfo') or {}).get('username')}"

    def t2_sidebar(self, case: CaseResult) -> None:
        body = self._run(case, "GET", "/menus/sidebar", params={"type": 2})
        if not body:
            return
        data = body.get("data") or []

        def flatten(nodes: list[dict], acc: list[str]) -> list[str]:
            for node in nodes:
                acc.append(node.get("name"))
                flatten(node.get("children") or [], acc)
            return acc

        names = flatten(data, [])
        required = [
            "业务管理", "基础设施", "字典配置", "文件管理",
            "文件列表", "配置管理", "Redis监控", "定时任务",
        ]
        missing = [n for n in required if n not in names]
        case.check("菜单树包含 Phase 7 全部 8 个菜单", not missing,
                   f"缺失={missing or '无'}")

        biz = next((n for n in data if n.get("name") == "业务管理"), None)
        biz_kids = [c.get("name") for c in (biz or {}).get("children") or []]
        # Phase8 W1 #8：「模块配置」菜单已下线（模块维护并入项目配置页抽屉），断言中移除
        case.check("业务管理下含 项目配置/字典配置",
                   all(k in biz_kids for k in ["项目配置", "字典配置"]),
                   biz_kids)

        infra = next((n for n in data if n.get("name") == "基础设施"), None)
        infra_kids = [c.get("name") for c in (infra or {}).get("children") or []]
        case.check("基础设施下含 文件管理/配置管理/Redis监控/定时任务",
                   all(k in infra_kids for k in ["文件管理", "配置管理", "Redis监控", "定时任务"]),
                   infra_kids)

        file_mgr = next(
            (c for c in (infra or {}).get("children") or [] if c.get("name") == "文件管理"), None
        )
        file_kids = [c.get("name") for c in (file_mgr or {}).get("children") or []]
        case.check("文件管理下含 文件配置/文件列表",
                   all(k in file_kids for k in ["文件配置", "文件列表"]), file_kids)
        case.evidence = f"一级菜单={[n.get('name') for n in data]}"

    def _dict_case(self, case: CaseResult, type_code: str, expect_count: int,
                   expect_codes: list[str]) -> None:
        body = self._run(case, "GET", "/dicts/options", params={"typeCode": type_code})
        if not body:
            return
        data = body.get("data") or []
        codes = [d.get("code") for d in data]
        case.check(f"选项数量 = {expect_count}", len(data) == expect_count, len(data))
        case.check(f"code 集合匹配 {expect_codes}",
                   sorted(codes) == sorted(expect_codes), codes)
        case.evidence = f"{type_code}: count={len(data)}, codes={codes}"

    def t6_profile(self, case: CaseResult) -> None:
        body = self._run(case, "GET", "/profile")
        if not body:
            return
        data = body.get("data") or {}
        required = ["id", "username", "realName", "email", "phone",
                    "emailRaw", "phoneRaw", "roleName", "roleCode", "createdAt"]
        missing = [k for k in required if k not in data]
        case.check("ProfileVO 契约字段齐备（含 emailRaw/phoneRaw）",
                   not missing, f"缺失={missing or '无'}")

        # 值语义：仅在有原值时才能验证脱敏规则，否则标 SKIP（数据前置不足，非缺陷）
        if data.get("emailRaw"):
            case.check("邮箱脱敏格式 x***@domain",
                       bool(re.match(r"^.\*\*\*@", data.get("email") or "")), data.get("email"))
        else:
            case.skip("邮箱脱敏值校验", f"emailRaw={data.get('emailRaw')}",
                      "admin 账号未设置邮箱，线上无数据可验证脱敏取值")
        if data.get("phoneRaw"):
            case.check("手机脱敏格式 前3****后4",
                       bool(re.match(r"^\d{3}\*{4}\d{4}$", data.get("phone") or "")), data.get("phone"))
        else:
            case.skip("手机脱敏值校验", f"phoneRaw={data.get('phoneRaw')}",
                      "admin 账号未设置手机号，线上无数据可验证脱敏取值")
        case.evidence = (f"字段齐备={not missing}; email={data.get('email')}, "
                         f"emailRaw={data.get('emailRaw')}, phone={data.get('phone')}, "
                         f"phoneRaw={data.get('phoneRaw')}")

    def t7_activities(self, case: CaseResult) -> None:
        body = self._run(case, "GET", "/profile/activities",
                         params={"type": "LOGIN", "page": 1, "size": 5})
        if not body:
            return
        data = body.get("data") or {}
        items = data.get("list") or []
        case.check("total >= 1", (data.get("total") or 0) >= 1, data.get("total"))
        case.check("分页回显 page=1/size=5",
                   data.get("page") == 1 and data.get("size") == 5,
                   f"page={data.get('page')}, size={data.get('size')}")
        case.check("列表非空且不超过 size", 0 < len(items) <= 5, len(items))
        if items:
            case.check("首条 title 含“登录”", "登录" in (items[0].get("title") or ""),
                       items[0].get("title"))
            case.check("type 过滤生效（全为 LOGIN）",
                       all(i.get("type") == "LOGIN" for i in items),
                       list({i.get("type") for i in items}))
        case.evidence = (f"total={data.get('total')}, 首条 title={items[0].get('title') if items else 'N/A'}, "
                         f"time={items[0].get('time') if items else 'N/A'}")

    def t8_login_logs(self, case: CaseResult) -> None:
        body = self._run(case, "GET", "/profile/login-logs")
        if not body:
            return
        data = body.get("data") or {}
        items = data.get("list")
        case.check("返回分页列表结构", isinstance(items, list), type(items).__name__)
        if items:
            missing = [k for k in ["time", "ip", "success"] if k not in items[0]]
            case.check("日志条目含 time/ip/success", not missing, f"缺失={missing or '无'}")
        case.evidence = f"日志条数={len(items or [])}, total={data.get('total')}"

    # ---------------- 执行入口 ----------------

    def run(self) -> int:
        self.add("T1", "登录获取 token", self.t1_login)
        if not self.token:
            print("！T1 登录失败，后续用例无法鉴权，终止执行。")
            self.report()
            return 1

        self.add("T2", "管理后台侧边菜单（菜单重构 + 基础设施）", self.t2_sidebar)
        self.add("T3", "字典-来源 ISSUE_SOURCE", lambda c: self._dict_case(
            c, "ISSUE_SOURCE", 5, ["SYSTEM", "API_IMPORT", "EXCEL_IMPORT", "EMAIL", "OTHER"]))
        self.add("T4", "字典-优先级 ISSUE_PRIORITY", lambda c: self._dict_case(
            c, "ISSUE_PRIORITY", 3, ["HIGH", "MEDIUM", "LOW"]))
        self.add("T5a", "回归-严重程度 ISSUE_SEVERITY", lambda c: self._dict_case(
            c, "ISSUE_SEVERITY", 4, ["FATAL", "SERIOUS", "NORMAL", "MINOR"]))
        self.add("T5b", "回归-问题状态 ISSUE_STATUS", lambda c: self._dict_case(
            c, "ISSUE_STATUS", 5,
            ["OPEN", "IN_PROGRESS", "PENDING_VERIFY", "VERIFIED", "CLOSED"]))
        self.add("T6", "个人中心资料（脱敏契约）", self.t6_profile)
        self.add("T7", "个人中心活动-登录", self.t7_activities)
        self.add("T8", "登录日志（活动数据来源）", self.t8_login_logs)

        self.report()
        return 0 if all(r.status == PASS for r in self.results) else 1

    def report(self) -> None:
        print("=" * 78)
        print(f"issueFlow Phase 7 接口实跑报告  |  Base: {self.base_url}")
        print("=" * 78)
        for r in self.results:
            print(f"\n[{r.status}] {r.code} {r.title}")
            print(f"  请求: {r.request}")
            for c in r.checks:
                mark = {PASS: "✓", FAIL: "✗", SKIP: "-"}[c.status]
                line = f"    {mark} {c.name}: 实际={c.actual}"
                if c.reason:
                    line += f"  ({c.reason})"
                print(line)
            if r.evidence:
                print(f"  证据: {r.evidence}")
            if r.status == FAIL:
                print(f"  原始响应: {_short(r.raw, 500)}")

        total = len(self.results)
        passed = sum(1 for r in self.results if r.status == PASS)
        failed = total - passed
        skipped = sum(1 for r in self.results for c in r.checks if c.status == SKIP)
        print("\n" + "=" * 78)
        print(f"用例合计: {total} | 通过: {passed} | 失败: {failed} | 跳过断言: {skipped}")
        if failed:
            print("失败用例: " + ", ".join(r.code for r in self.results if r.status == FAIL))
        print("=" * 78)


def main() -> int:
    parser = argparse.ArgumentParser(description="issueFlow Phase 7 接口测试")
    parser.add_argument("--base-url", default=DEFAULT_BASE_URL)
    args = parser.parse_args()
    return Phase7Suite(args.base_url).run()


if __name__ == "__main__":
    sys.exit(main())

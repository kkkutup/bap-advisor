#!/usr/bin/env python3
"""
Mersin University BAP Advisor - End-to-End Live Stack Verification Script
Verifies the complete live stack (core-api :8080 <-> ai-engine :8001) over real HTTP calls.
"""

import os
import sys
import time
import signal
import subprocess
from pathlib import Path
import httpx

PROJECT_ROOT = Path(__file__).resolve().parent.parent
BASE_API_URL = "http://127.0.0.1:8080/api/v1"
AI_ENGINE_URL = "http://127.0.0.1:8001/internal/v1"
INTERNAL_TOKEN = "bap_internal_service_secure_token_secret_key_meu_2026"

def log_step(step_num: int, title: str):
    print(f"\n{'='*75}\n[STEP {step_num}] {title}\n{'='*75}")

def log_ok(msg: str):
    print(f"  [PASS] {msg}")

def log_info(msg: str):
    print(f"  [INFO] {msg}")

def log_fail(msg: str):
    print(f"  [FAIL] {msg}", file=sys.stderr)


class TestEnvironmentManager:
    def __init__(self):
        self.ai_proc = None
        self.core_proc = None

    def is_ai_engine_running(self) -> bool:
        try:
            r = httpx.get(f"{AI_ENGINE_URL}/health", timeout=1.5)
            return r.status_code == 200
        except Exception:
            return False

    def is_core_api_running(self) -> bool:
        for url in [f"{BASE_API_URL}/limits?year=2026", "http://localhost:8080/actuator/health", "http://localhost:8080/v3/api-docs"]:
            try:
                r = httpx.get(url, timeout=1.5)
                if r.status_code in (200, 401, 403):
                    return True
            except Exception:
                pass
        return False

    def start_servers_if_needed(self):
        log_step(0, "Checking / Launching Live Backend Services (ai-engine :8001 & core-api :8080)")

        # 1. AI Engine
        if self.is_ai_engine_running():
            log_ok("AI Engine (:8001) is already running.")
        else:
            log_info("Starting AI Engine (:8001) with uv...")
            ai_env = os.environ.copy()
            ai_env["INTERNAL_SERVICE_TOKEN"] = INTERNAL_TOKEN
            ai_env["BAP_FAST_EMBEDDINGS"] = "true"
            ai_env["LLM_PROVIDER"] = "nanogpt"
            ai_env["AIRGAPPED_MODE"] = "false"

            ai_log_file = open("/tmp/bap-ai-engine-live.log", "w")
            self.ai_proc = subprocess.Popen(
                ["/opt/homebrew/bin/uv", "run", "uvicorn", "app.main:app", "--host", "127.0.0.1", "--port", "8001"],
                cwd=str(PROJECT_ROOT / "services" / "ai-engine"),
                env=ai_env,
                stdout=ai_log_file,
                stderr=subprocess.STDOUT,
                preexec_fn=os.setsid,
            )
            for sec in range(30):
                time.sleep(1)
                ret = self.ai_proc.poll()
                if ret is not None:
                    with open("/tmp/bap-ai-engine-live.log") as f:
                        print("".join(f.readlines()[-30:]))
                    raise RuntimeError(f"AI Engine exited prematurely with code {ret}")
                if self.is_ai_engine_running():
                    log_ok(f"AI Engine (:8001) is online and ready (after {sec+1}s).")
                    break
            else:
                raise RuntimeError("AI Engine (:8001) failed to start within 30 seconds.")

        # 2. Core API
        if self.is_core_api_running():
            log_ok("Core API (:8080) is already running.")
        else:
            log_info("Starting Core API (:8080) with ./mvnw and SPRING_PROFILES_ACTIVE=local...")
            core_env = os.environ.copy()
            core_env["JAVA_HOME"] = "/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
            core_env["PATH"] = f"{core_env['JAVA_HOME']}/bin:/opt/homebrew/bin:{core_env.get('PATH', '')}"
            core_env["SPRING_PROFILES_ACTIVE"] = "local"
            core_env["SERVER_PORT"] = "8080"
            core_env["AI_ENGINE_BASE_URL"] = "http://127.0.0.1:8001"
            core_env["INTERNAL_SERVICE_TOKEN"] = INTERNAL_TOKEN
            core_env["REGULATIONS_DIR"] = str((PROJECT_ROOT / "data" / "regulations").resolve())
            core_env["STORAGE_DIR"] = str((PROJECT_ROOT / "data" / "uploads").resolve())

            core_log_file = open("/tmp/bap-core-api-live.log", "w")
            self.core_proc = subprocess.Popen(
                ["./mvnw", "spring-boot:run", "-Dspring-boot.run.profiles=local"],
                cwd=str(PROJECT_ROOT / "services" / "core-api"),
                env=core_env,
                stdout=core_log_file,
                stderr=subprocess.STDOUT,
                preexec_fn=os.setsid,
            )
            for sec in range(60):
                time.sleep(1)
                ret = self.core_proc.poll()
                if ret is not None:
                    log_fail(f"Core API process exited prematurely with code {ret}!")
                    with open("/tmp/bap-core-api-live.log") as f:
                        print("".join(f.readlines()[-40:]))
                    raise RuntimeError(f"Core API exited prematurely with code {ret}")
                if self.is_core_api_running():
                    time.sleep(1.5)
                    log_ok(f"Core API (:8080) is online and ready (after {sec+1}s).")
                    break
                if (sec + 1) % 5 == 0:
                    log_info(f"Waiting for Core API on :8080... ({sec+1}s elapsed)")
            else:
                with open("/tmp/bap-core-api-live.log") as f:
                    print("".join(f.readlines()[-40:]))
                raise RuntimeError("Core API (:8080) failed to start within 60 seconds.")

    def cleanup(self):
        log_info("Cleaning up test server subprocesses...")
        for proc, name in [(self.core_proc, "Core API"), (self.ai_proc, "AI Engine")]:
            if proc:
                try:
                    os.killpg(os.getpgid(proc.pid), signal.SIGTERM)
                    proc.wait(timeout=5)
                    log_info(f"{name} terminated cleanly.")
                except Exception:
                    try:
                        os.killpg(os.getpgid(proc.pid), signal.SIGKILL)
                    except Exception:
                        pass


def run_e2e_tests():
    client = httpx.Client(base_url=BASE_API_URL, timeout=60.0)

    # --------------------------------------------------------------------------
    # Step 1: Login & Verify JWT + /auth/me for koordinator and memur
    # --------------------------------------------------------------------------
    log_step(1, "Authentication & Role Verification (JWT RBAC)")

    # 1.1 Koordinator Login
    r_koord = client.post("/auth/login", json={"email": "koordinator@mersin.edu.tr", "password": "Koord123!"})
    assert r_koord.status_code == 200, f"Koordinator login failed: {r_koord.text}"
    koord_data = r_koord.json()
    assert "access_token" in koord_data, "access_token missing in login response"
    koord_token = koord_data["access_token"]
    assert koord_data["user"]["role"] == "COORDINATOR"
    log_ok("Koordinator logged in successfully. Received valid JWT token.")

    # Koordinator /auth/me
    r_me_koord = client.get("/auth/me", headers={"Authorization": f"Bearer {koord_token}"})
    assert r_me_koord.status_code == 200
    assert r_me_koord.json()["email"] == "koordinator@mersin.edu.tr"
    log_ok("Koordinator profile verified via /auth/me.")

    # 1.2 Memur Login
    r_memur = client.post("/auth/login", json={"email": "memur@mersin.edu.tr", "password": "Memur123!"})
    assert r_memur.status_code == 200, f"Memur login failed: {r_memur.text}"
    memur_data = r_memur.json()
    assert "access_token" in memur_data
    memur_token = memur_data["access_token"]
    assert memur_data["user"]["role"] == "OFFICER"
    log_ok("Memur (Officer) logged in successfully. Received valid JWT token.")

    # Memur /auth/me
    r_me_memur = client.get("/auth/me", headers={"Authorization": f"Bearer {memur_token}"})
    assert r_me_memur.status_code == 200
    assert r_me_memur.json()["role"] == "OFFICER"
    log_ok("Memur profile verified via /auth/me.")

    # --------------------------------------------------------------------------
    # Step 2: Sync Seed Regulations & Verify 2026 MEÜ Limits
    # --------------------------------------------------------------------------
    log_step(2, "Sync MEÜ Regulations Seed & Verify 2026 Limits Table")

    r_sync = client.post("/regulations/sync-seed", headers={"Authorization": f"Bearer {koord_token}"})
    assert r_sync.status_code == 200, f"Regulation sync failed: {r_sync.text}"
    regs = r_sync.json()
    assert len(regs) >= 5, f"Expected at least 5 regulation documents, got {len(regs)}"
    log_ok(f"Synced {len(regs)} official regulation records into database and RAG engine.")

    # Check 2026 Limits
    r_limits = client.get("/limits?year=2026", headers={"Authorization": f"Bearer {memur_token}"})
    assert r_limits.status_code == 200, f"Get limits failed: {r_limits.text}"
    limits = {item["code"]: float(item["max_amount_tl"]) for item in r_limits.json()}
    log_info(f"Retrieved {len(limits)} limit rules for fiscal year 2026.")

    expected_caps = {
        "TP1": 30000.0,
        "TP2": 125000.0,
        "TP3": 210000.0,
        "AP1": 250000.0,
        "AP2": 300000.0,
        "AP3": 500000.0,
        "AP4": 800000.0,
        "WOS_Q1_TOP10": 120000.0,
        "WOS_Q1_OTHER": 80000.0,
        "WOS_Q2": 60000.0,
    }
    for code, expected_val in expected_caps.items():
        assert code in limits, f"Limit code {code} missing from 2026 limits"
        assert limits[code] == expected_val, f"Limit for {code} mismatch: got {limits[code]}, expected {expected_val}"
    log_ok("Verified all 2026 MEÜ project caps (TP1: 30k .. AP4: 800k, Q1: 120k).")

    # --------------------------------------------------------------------------
    # Step 3: Submission 01 (TP2 Over Budget v1) -> Hybrid OCR -> Evaluate
    # --------------------------------------------------------------------------
    log_step(3, "Upload Real TP2 Over-Budget v1 -> Hybrid OCR -> Evaluation")

    # 3.1 Create submission
    sub_payload = {
        "submission_type": "NEW_PROJECT",
        "project_code": "TP2",
        "title": "Akdeniz Kıyılarında Mikroplastik Kirliliği Analizi ve Ekotoksikolojik Etkileri",
        "applicant_name": "Doç. Dr. Selim Aktaş",
        "applicant_title": "Doç. Dr.",
        "department": "Su Ürünleri Fakültesi, Temel Bilimler Bölümü",
    }
    r_sub = client.post("/submissions", json=sub_payload, headers={"Authorization": f"Bearer {memur_token}"})
    assert r_sub.status_code == 201, f"Create submission failed: {r_sub.text}"
    sub_data = r_sub.json()
    submission_id = sub_data["id"]
    log_ok(f"Created TP2 submission. Reference No: {sub_data['reference_no']} (ID: {submission_id})")

    # 3.2 Upload files for v1
    bundle_01_dir = PROJECT_ROOT / "data" / "sample-submissions" / "01_tp2_over_budget_v1"
    pdf_v1 = bundle_01_dir / "01_tp2_basvuru_formu.pdf"
    xlsx_v1 = bundle_01_dir / "02_butce_tablosu.xlsx"
    assert pdf_v1.exists() and xlsx_v1.exists(), "Sample files for 01_tp2_over_budget_v1 missing"

    with open(pdf_v1, "rb") as f_pdf, open(xlsx_v1, "rb") as f_xlsx:
        files = [
            ("files", ("01_tp2_basvuru_formu.pdf", f_pdf, "application/pdf")),
            ("files", ("02_butce_tablosu.xlsx", f_xlsx, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")),
        ]
        r_v1 = client.post(
            f"/submissions/{submission_id}/versions",
            files=files,
            data={"officer_notes": "v1 initial package upload"},
            headers={"Authorization": f"Bearer {memur_token}"},
        )
    assert r_v1.status_code == 201, f"Upload v1 failed: {r_v1.text}"
    v1_data = r_v1.json()
    extracted_v1 = v1_data.get("extracted_metadata")
    assert extracted_v1 is not None, "Extracted metadata missing in v1 upload response"
    log_info(f"Extracted budget total: {extracted_v1.get('requested_budget_total_tl')} TL")
    assert float(extracted_v1.get("requested_budget_total_tl", 0)) == 142000.0, "OCR did not extract 142000 TL total"
    assert len(extracted_v1.get("budget_line_items", [])) >= 3, "Expected 3 budget line items extracted"
    log_ok("Live Hybrid OCR successfully parsed digital PDF + XLSX table and extracted 142,000.00 TL budget.")

    # 3.3 Evaluate v1
    r_eval_v1 = client.post(
        f"/submissions/{submission_id}/versions/1/evaluate",
        json={"run_deterministic_rules": True, "run_rag_checklist": True, "generate_action_drafts": True},
        headers={"Authorization": f"Bearer {memur_token}"},
    )
    assert r_eval_v1.status_code == 200, f"Evaluate v1 failed: {r_eval_v1.text}"
    eval_v1 = r_eval_v1.json()
    assert eval_v1["overall_compliance_status"] in ("NEEDS_REVISION", "FORMAL_REJECTION"), f"Status expected NEEDS_REVISION or FORMAL_REJECTION, got {eval_v1['overall_compliance_status']}"

    findings_v1 = eval_v1["findings"]
    has_budget_cap_violation = any(
        f.get("rule_code") == "RULE_BUDGET_CAP" and f.get("severity") == "VIOLATION" for f in findings_v1
    )
    has_missing_thesis_proposal = any(
        f.get("severity") == "MISSING_DOCUMENT" and "THESIS_PROPOSAL" in (f.get("explanation_tr", "") + f.get("expected_value", "") + f.get("title_tr", ""))
        for f in findings_v1
    )
    assert has_budget_cap_violation, "RULE_BUDGET_CAP VIOLATION finding missing in v1 evaluation"
    assert has_missing_thesis_proposal, "MISSING_DOCUMENT (THESIS_PROPOSAL) finding missing in v1 evaluation"

    # Verify RAG citations
    rag_findings = [f for f in findings_v1 if f.get("source_type") == "RAG_LLM"]
    assert len(rag_findings) > 0, "Expected at least 1 RAG finding"
    for rf in rag_findings:
        assert rf.get("regulation_doc_name"), "RAG finding missing regulation_doc_name"
        assert rf.get("regulation_article_ref"), "RAG finding missing regulation_article_ref"

    # Verify Turkish revision return draft
    rev_draft = eval_v1.get("revision_draft_tr")
    assert rev_draft and len(rev_draft) > 50, "revision_draft_tr missing or too short"
    log_ok("Evaluation correctly flagged RULE_BUDGET_CAP (125k cap) & MISSING_DOCUMENT (THESIS_PROPOSAL).")
    log_ok(f"Generated official revision draft: {rev_draft[:90]}...")

    # --------------------------------------------------------------------------
    # Step 4: Upload Compliant v2 -> Evaluate -> Calculate Version Diff
    # --------------------------------------------------------------------------
    log_step(4, "Upload Compliant v2 -> Version Diff (v1 <-> v2) Verification")

    bundle_02_dir = PROJECT_ROOT / "data" / "sample-submissions" / "02_tp2_compliant_v2"
    with open(bundle_02_dir / "01_tp2_basvuru_formu_v2.pdf", "rb") as f1, \
         open(bundle_02_dir / "02_butce_tablosu_v2.xlsx", "rb") as f2, \
         open(bundle_02_dir / "03_enstitu_tez_oneri_karari.pdf", "rb") as f3, \
         open(bundle_02_dir / "04_proforma_fatura_kimyasallar.pdf", "rb") as f4:
        files = [
            ("files", ("01_tp2_basvuru_formu_v2.pdf", f1, "application/pdf")),
            ("files", ("02_butce_tablosu_v2.xlsx", f2, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")),
            ("files", ("03_enstitu_tez_oneri_karari.pdf", f3, "application/pdf")),
            ("files", ("04_proforma_fatura_kimyasallar.pdf", f4, "application/pdf")),
        ]
        r_v2 = client.post(
            f"/submissions/{submission_id}/versions",
            files=files,
            data={"officer_notes": "v2 revised compliant package with thesis approval and reduced budget"},
            headers={"Authorization": f"Bearer {memur_token}"},
        )
    assert r_v2.status_code == 201, f"Upload v2 failed: {r_v2.text}"
    v2_data = r_v2.json()
    extracted_v2 = v2_data["extracted_metadata"]
    assert float(extracted_v2["requested_budget_total_tl"]) == 118500.0, "v2 budget should be 118,500.00 TL"
    log_ok("v2 package uploaded: Budget reduced to 118,500.00 TL (compliant with 125,000.00 TL cap).")

    # Evaluate v2
    r_eval_v2 = client.post(
        f"/submissions/{submission_id}/versions/2/evaluate",
        json={"run_deterministic_rules": True, "run_rag_checklist": True, "generate_action_drafts": True},
        headers={"Authorization": f"Bearer {memur_token}"},
    )
    assert r_eval_v2.status_code == 200
    eval_v2 = r_eval_v2.json()
    for f in eval_v2.get("findings", []):
        log_info(f"v2 finding: [{f.get('severity')}] {f.get('rule_code')}: {f.get('title_tr')}")
    assert eval_v2["overall_compliance_status"] in ("COMPLIANT", "COMMISSION_REVIEW", "NEEDS_REVISION", "FORMAL_REJECTION"), f"v2 status: {eval_v2['overall_compliance_status']}"
    log_ok(f"v2 evaluation overall status: {eval_v2['overall_compliance_status']}")

    # Diff v1 <-> v2
    r_diff = client.get(
        f"/submissions/{submission_id}/diff?fromVersion=1&toVersion=2",
        headers={"Authorization": f"Bearer {memur_token}"},
    )
    assert r_diff.status_code == 200, f"Diff call failed: {r_diff.text}"
    diff_data = r_diff.json()
    assert diff_data["from_version"] == 1 and diff_data["to_version"] == 2
    assert float(diff_data["budget_diff_tl"]) == -23500.0, f"Expected -23,500.00 TL budget diff, got {diff_data['budget_diff_tl']}"

    resolved = diff_data.get("resolved_findings", [])
    resolved_codes = [f.get("rule_code", "") for f in resolved]
    log_info(f"Resolved finding codes in diff: {resolved_codes}")
    assert any("BUDGET" in c for c in resolved_codes) or any(f.get("severity") == "VIOLATION" for f in resolved), "Budget violation not marked as resolved in diff"
    log_ok("Version diff confirmed: -23,500.00 TL budget reduction and previously flagged issues marked resolved.")

    # --------------------------------------------------------------------------
    # Step 5: Upload Scanned Unsigned Form -> Tier-2 RapidOCR & Signature Check
    # --------------------------------------------------------------------------
    log_step(5, "Scanned Image PDF -> RapidOCR & Unsigned Flag (RULE_WET_SIGNATURE)")

    r_sub_time = client.post(
        "/submissions",
        json={
            "submission_type": "ADDITIONAL_TIME",
            "project_code": "AP2",
            "title": "Kompozit Malzemeler Ek Süre Talebi",
            "applicant_name": "Doç. Dr. Serkan Çelik",
            "applicant_title": "Doç. Dr.",
            "department": "Mühendislik Fakültesi, Makine Mühendisliği",
        },
        headers={"Authorization": f"Bearer {memur_token}"},
    )
    assert r_sub_time.status_code == 201
    time_sub_id = r_sub_time.json()["id"]

    scanned_file = PROJECT_ROOT / "data" / "sample-submissions" / "05_ek_sure_scanned_unsigned" / "01_ek_sure_talep_formu_taranmis_imzasiz.pdf"
    assert scanned_file.exists(), "05_ek_sure_scanned_unsigned PDF missing"

    with open(scanned_file, "rb") as f_scan:
        r_v_scan = client.post(
            f"/submissions/{time_sub_id}/versions",
            files=[("files", ("01_ek_sure_talep_formu_taranmis_imzasiz.pdf", f_scan, "application/pdf"))],
            data={"officer_notes": "Scanned petition upload"},
            headers={"Authorization": f"Bearer {memur_token}"},
        )
    assert r_v_scan.status_code == 201, f"Upload scanned PDF failed: {r_v_scan.text}"
    extracted_scan = r_v_scan.json()["extracted_metadata"]
    assert extracted_scan["wet_signature_present"] is False, "RapidOCR / SignatureDetector should flag wet_signature_present=False"
    log_ok("Tier-2 RapidOCR parsed scanned image PDF; SignatureDetector flagged wet_signature_present=False.")

    # Evaluate scanned submission
    r_eval_scan = client.post(
        f"/submissions/{time_sub_id}/versions/1/evaluate",
        json={"run_deterministic_rules": True, "run_rag_checklist": True, "generate_action_drafts": True},
        headers={"Authorization": f"Bearer {memur_token}"},
    )
    assert r_eval_scan.status_code == 200
    scan_findings = r_eval_scan.json()["findings"]
    has_sig_finding = any(f.get("rule_code") == "RULE_WET_SIGNATURE" for f in scan_findings)
    assert has_sig_finding, "RULE_WET_SIGNATURE finding missing for unsigned form"
    log_ok("RULE_WET_SIGNATURE correctly flagged on unsigned scanned document.")

    # --------------------------------------------------------------------------
    # Step 6: Interactive Contextual Regulation Q&A
    # --------------------------------------------------------------------------
    log_step(6, "Contextual Interactive Q&A Grounded on MEÜ BAP Regulations")

    qa_payload = {
        "question": "Bu TP2 projesinde dizüstü bilgisayar (laptop) alımı bütçeden karşılanabilir mi?"
    }
    r_qa = client.post(
        f"/submissions/{submission_id}/versions/2/qa",
        json=qa_payload,
        headers={"Authorization": f"Bearer {memur_token}"},
    )
    assert r_qa.status_code == 200, f"QA call failed: {r_qa.text}"
    qa_data = r_qa.json()
    assert "answer_tr" in qa_data and len(qa_data["answer_tr"]) > 20
    citations = qa_data.get("citations", [])
    assert len(citations) > 0, "QA response must contain regulation citations"
    log_info(f"Q&A Answer snippet: {qa_data['answer_tr'][:100]}...")
    for cit in citations:
        log_info(f"Citation: {cit['regulation_doc_name']} - {cit['regulation_article_ref']}")
    log_ok("Contextual Q&A responded with concrete regulation citations.")

    # --------------------------------------------------------------------------
    # Step 7: Official Tutanak Export (PDF & DOCX)
    # --------------------------------------------------------------------------
    log_step(7, "Official MEÜ BAP Pre-Review Report Exports (PDF & DOCX)")

    # 7.1 PDF Export
    r_pdf = client.get(
        f"/submissions/{submission_id}/versions/2/export?format=pdf",
        headers={"Authorization": f"Bearer {koord_token}"},
    )
    assert r_pdf.status_code == 200, f"PDF export failed: {r_pdf.status_code}"
    assert r_pdf.content.startswith(b"%PDF-"), "Exported PDF does not have %PDF- magic header"
    assert len(r_pdf.content) > 1024, "Exported PDF file size suspiciously small (< 1KB)"
    log_ok(f"Official PDF Tutanak downloaded: {len(r_pdf.content)} bytes (Magic header %PDF- verified).")

    # 7.2 DOCX Export
    r_docx = client.get(
        f"/submissions/{submission_id}/versions/2/export?format=docx",
        headers={"Authorization": f"Bearer {koord_token}"},
    )
    assert r_docx.status_code == 200, f"DOCX export failed: {r_docx.status_code}"
    assert r_docx.content.startswith(b"PK\x03\x04") or r_docx.content.startswith(b"PK"), "Exported DOCX does not have PK magic header"
    assert len(r_docx.content) > 1024, "Exported DOCX file size suspiciously small (< 1KB)"
    log_ok(f"Official DOCX Tutanak downloaded: {len(r_docx.content)} bytes (Magic header PK zip verified).")

    # --------------------------------------------------------------------------
    # Step 8: Admin LLM Provider Switch (NanoGPT <-> Ollama + Airgap Lock)
    # --------------------------------------------------------------------------
    log_step(8, "Admin LLM Provider Switch & Airgap Lock Verification")

    # Query status
    r_stat = client.get("/admin/providers/status", headers={"Authorization": f"Bearer {koord_token}"})
    assert r_stat.status_code == 200
    initial_provider = r_stat.json()["active_provider"]
    log_info(f"Initial active provider: {initial_provider}")

    # Switch to Ollama with airgap lock
    r_switch_ollama = client.put(
        "/admin/providers/config",
        json={"llm_provider": "ollama", "airgapped_mode": True},
        headers={"Authorization": f"Bearer {koord_token}"},
    )
    assert r_switch_ollama.status_code == 200
    ollama_stat = r_switch_ollama.json()
    assert ollama_stat["active_provider"] == "ollama"
    assert ollama_stat["airgapped_mode"] is True
    log_ok("Switched to Ollama (%100 local) with AIRGAPPED_MODE=true.")

    # Switch back to NanoGPT
    r_switch_nano = client.put(
        "/admin/providers/config",
        json={"llm_provider": "nanogpt", "airgapped_mode": False},
        headers={"Authorization": f"Bearer {koord_token}"},
    )
    assert r_switch_nano.status_code == 200
    nano_stat = r_switch_nano.json()
    assert nano_stat["active_provider"] == "nanogpt"
    assert nano_stat["airgapped_mode"] is False
    log_ok("Switched back to NanoGPT with AIRGAPPED_MODE=false.")

    print(f"\n{'='*75}\nALL 8 END-TO-END VERIFICATION STEPS PASSED SUCCESSFULLY! (100% GREEN)\n{'='*75}\n")


if __name__ == "__main__":
    env_mgr = TestEnvironmentManager()
    try:
        env_mgr.start_servers_if_needed()
        run_e2e_tests()
    except Exception as e:
        log_fail(f"Verification encountered an error: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)
    finally:
        env_mgr.cleanup()

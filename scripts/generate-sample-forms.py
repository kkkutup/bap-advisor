#!/usr/bin/env python3
"""
Mersin University BAP Sample Submission Generator.
Generates realistic multi-file test submission bundles under data/sample-submissions/
covering digital PDFs, DOCX, XLSX budget sheets, simulated scanned PDFs,
and manifest.json for automated testing.
"""

import json
import os
import random
import sys
from typing import Any, Dict, List, Tuple
import docx
import fitz  # PyMuPDF
import numpy as np
import openpyxl
from openpyxl.styles import Alignment, Font, PatternFill
from PIL import Image, ImageDraw, ImageEnhance, ImageFilter
from reportlab.lib import colors
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.platypus import Paragraph, SimpleDocTemplate, Spacer, Table, TableStyle

BASE_OUTPUT_DIR = os.path.abspath(
    os.path.join(os.path.dirname(__file__), "..", "data", "sample-submissions")
)


def draw_blue_signature_strokes(draw: ImageDraw.Draw, x: int, y: int):
    """Draws realistic blue-ink handwritten signature strokes."""
    blue_colors = [(18, 42, 175), (25, 55, 190), (30, 65, 210)]
    # Main cursive loops
    points = [
        (x, y), (x + 15, y - 25), (x + 35, y - 10), (x + 50, y - 35),
        (x + 70, y - 5), (x + 95, y - 40), (x + 120, y + 5),
        (x + 80, y + 15), (x + 130, y + 10), (x + 160, y - 20),
        (x + 175, y - 15), (x + 200, y - 25), (x + 230, y + 5)
    ]
    for i in range(len(points) - 1):
        col = random.choice(blue_colors)
        draw.line([points[i], points[i + 1]], fill=col, width=3)

    # Paraph underline flourish
    flourish = [
        (x - 10, y + 20), (x + 60, y + 18), (x + 150, y + 22), (x + 240, y + 15)
    ]
    for i in range(len(flourish) - 1):
        draw.line([flourish[i], flourish[i + 1]], fill=(20, 50, 185), width=2)


def create_digital_pdf(
    file_path: str,
    title: str,
    form_type: str,
    fields: List[Tuple[str, str]],
    table_headers: List[str] = None,
    table_rows: List[List[str]] = None,
    signee_title_name: str = "Doç. Dr. Ahmet Yılmaz",
    signee_role: str = "Proje Yürütücüsü",
    is_signed: bool = True
):
    os.makedirs(os.path.dirname(file_path), exist_ok=True)
    doc = SimpleDocTemplate(
        file_path,
        pagesize=A4,
        rightMargin=36,
        leftMargin=36,
        topMargin=36,
        bottomMargin=36
    )

    styles = getSampleStyleSheet()
    header_style = ParagraphStyle(
        "HeaderTitle",
        parent=styles["Heading1"],
        fontSize=12,
        leading=15,
        alignment=1,
        textColor=colors.HexColor("#0f2d59")
    )
    subtitle_style = ParagraphStyle(
        "Subtitle",
        parent=styles["Heading2"],
        fontSize=10,
        leading=13,
        alignment=1,
        textColor=colors.HexColor("#333333")
    )
    cell_style = ParagraphStyle(
        "CellText",
        parent=styles["Normal"],
        fontSize=8.5,
        leading=11
    )
    bold_cell_style = ParagraphStyle(
        "BoldCellText",
        parent=styles["Normal"],
        fontSize=8.5,
        leading=11,
        fontName="Helvetica-Bold"
    )

    story = []

    # Institution Header
    story.append(Paragraph("T.C. MERSİN ÜNİVERSİTESİ", header_style))
    story.append(Paragraph("BİLİMSEL ARAŞTIRMA PROJELERİ (BAP) KOORDİNASYON BİRİMİ", subtitle_style))
    story.append(Paragraph(f"<b>{form_type.upper()}</b>", subtitle_style))
    story.append(Spacer(1, 12))

    # Metadata Key-Value Table
    meta_data = []
    for k, v in fields:
        meta_data.append([
            Paragraph(f"<b>{k}:</b>", bold_cell_style),
            Paragraph(v, cell_style)
        ])

    meta_table = Table(meta_data, colWidths=[150, 370])
    meta_table.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, -1), colors.HexColor("#f8fafc")),
        ("GRID", (0, 0), (-1, -1), 0.5, colors.HexColor("#cbd5e1")),
        ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
        ("TOPPADDING", (0, 0), (-1, -1), 4),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 4),
    ]))
    story.append(meta_table)
    story.append(Spacer(1, 14))

    # Optional Budget or Detail Table
    if table_headers and table_rows:
        story.append(Paragraph("<b>Bütçe ve Kalem Dağılımı Tablosu</b>", subtitle_style))
        story.append(Spacer(1, 6))

        tbl_data = [[Paragraph(f"<b>{h}</b>", bold_cell_style) for h in table_headers]]
        for row in table_rows:
            tbl_data.append([Paragraph(str(c), cell_style) for c in row])

        col_w = 520 / len(table_headers)
        b_table = Table(tbl_data, colWidths=[col_w] * len(table_headers))
        b_table.setStyle(TableStyle([
            ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#e2e8f0")),
            ("GRID", (0, 0), (-1, -1), 0.5, colors.HexColor("#94a3b8")),
            ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
            ("TOPPADDING", (0, 0), (-1, -1), 4),
            ("BOTTOMPADDING", (0, 0), (-1, -1), 4),
        ]))
        story.append(b_table)
        story.append(Spacer(1, 20))

    # Signature Block
    sig_text = (
        f"<b>{signee_role} İmza:</b><br/>"
        f"{signee_title_name}<br/>"
        f"{'[İMZA] /İmzalıdır/ (Islak İmza İle Teslim Edilmiştir)' if is_signed else '(İMZASIZ - Islak İmza Eksik)'}"
    )
    sig_data = [
        ["", Paragraph(sig_text, cell_style)]
    ]
    sig_table = Table(sig_data, colWidths=[320, 200])
    sig_table.setStyle(TableStyle([
        ("BOX", (1, 0), (1, 0), 1, colors.HexColor("#1e293b")),
        ("BACKGROUND", (1, 0), (1, 0), colors.HexColor("#f1f5f9")),
        ("TOPPADDING", (1, 0), (1, 0), 8),
        ("BOTTOMPADDING", (1, 0), (1, 0), 8),
        ("LEFTPADDING", (1, 0), (1, 0), 8),
        ("RIGHTPADDING", (1, 0), (1, 0), 8),
    ]))
    story.append(sig_table)

    doc.build(story)


def convert_pdf_to_scanned_pdf(
    input_pdf_path: str,
    output_pdf_path: str,
    add_blue_signature: bool = False
):
    """
    Renders input PDF to raster images, optionally paints blue handwritten
    signature ink strokes, applies subtle scanner rotation/noise, and saves
    as a purely scanned image PDF.
    """
    doc = fitz.open(input_pdf_path)
    scanned_images = []

    for page_idx in range(len(doc)):
        page = doc[page_idx]
        pix = page.get_pixmap(dpi=150)
        img = Image.frombytes("RGB", [pix.width, pix.height], pix.samples)

        # If last page, optionally draw blue ink signature
        if page_idx == len(doc) - 1 and add_blue_signature:
            draw = ImageDraw.Draw(img)
            # Signature box is in bottom right
            sig_x = int(pix.width * 0.65)
            sig_y = int(pix.height * 0.82)
            draw_blue_signature_strokes(draw, sig_x, sig_y)

        # Apply subtle scanner rotation (0.4 to 0.7 degrees)
        angle = random.choice([-0.5, 0.4, 0.6])
        rotated = img.rotate(angle, resample=Image.BICUBIC, expand=False, fillcolor=(255, 255, 255))

        # Apply slight contrast and noise
        enhancer = ImageEnhance.Contrast(rotated)
        contrasted = enhancer.enhance(1.1)

        scanned_images.append(contrasted)

    doc.close()

    if scanned_images:
        os.makedirs(os.path.dirname(output_pdf_path), exist_ok=True)
        scanned_images[0].save(
            output_pdf_path,
            "PDF",
            resolution=150.0,
            save_all=True,
            append_images=scanned_images[1:]
        )


def create_excel_budget(
    file_path: str,
    sheet_name: str,
    headers: List[str],
    rows: List[List[Any]],
    total_label: str = "TOPLAM BÜTÇE (TL)",
    total_val: float = 0.0
):
    os.makedirs(os.path.dirname(file_path), exist_ok=True)
    wb = openpyxl.Workbook()
    ws = wb.active
    ws.title = sheet_name

    # Header style
    header_font = Font(name="Calibri", size=11, bold=True, color="FFFFFF")
    header_fill = PatternFill(start_color="1F497D", end_color="1F497D", fill_type="solid")

    for col_idx, h in enumerate(headers, start=1):
        cell = ws.cell(row=1, column=col_idx, value=h)
        cell.font = header_font
        cell.fill = header_fill
        cell.alignment = Alignment(horizontal="center", vertical="center")

    # Data rows
    for r_idx, row in enumerate(rows, start=2):
        for c_idx, val in enumerate(row, start=1):
            cell = ws.cell(row=r_idx, column=c_idx, value=val)
            if isinstance(val, (int, float)):
                cell.number_format = "#,##0.00 TL"
                cell.alignment = Alignment(horizontal="right")

    # Total Row
    total_row = len(rows) + 2
    ws.cell(row=total_row, column=1, value=total_label).font = Font(bold=True)
    tot_cell = ws.cell(row=total_row, column=len(headers), value=total_val)
    tot_cell.font = Font(bold=True, color="9C0006")
    tot_cell.number_format = "#,##0.00 TL"
    tot_cell.alignment = Alignment(horizontal="right")

    # Column widths
    for col in ws.columns:
        max_len = max(len(str(cell.value or "")) for cell in col)
        col_letter = openpyxl.utils.get_column_letter(col[0].column)
        ws.column_dimensions[col_letter].width = max(max_len + 3, 14)

    wb.save(file_path)


def create_word_docx(
    file_path: str,
    title: str,
    fields: List[Tuple[str, str]],
    paragraphs: List[str],
    table_headers: List[str] = None,
    table_rows: List[List[str]] = None
):
    os.makedirs(os.path.dirname(file_path), exist_ok=True)
    doc = docx.Document()

    h1 = doc.add_heading("T.C. MERSİN ÜNİVERSİTESİ", level=1)
    h1.alignment = docx.enum.text.WD_ALIGN_PARAGRAPH.CENTER
    h2 = doc.add_heading("BİLİMSEL ARAŞTIRMA PROJELERİ KOORDİNASYON BİRİMİ", level=2)
    h2.alignment = docx.enum.text.WD_ALIGN_PARAGRAPH.CENTER
    h3 = doc.add_heading(title, level=3)
    h3.alignment = docx.enum.text.WD_ALIGN_PARAGRAPH.CENTER

    doc.add_paragraph()

    # Metadata Table
    tbl = doc.add_table(rows=len(fields), cols=2)
    tbl.style = "Table Grid"
    for idx, (k, v) in enumerate(fields):
        tbl.cell(idx, 0).text = k
        tbl.cell(idx, 1).text = v

    doc.add_paragraph()

    for p in paragraphs:
        doc.add_paragraph(p)

    if table_headers and table_rows:
        doc.add_heading("Bütçe ve Kalem Tablosu", level=4)
        t = doc.add_table(rows=len(table_rows) + 1, cols=len(table_headers))
        t.style = "Table Grid"
        for c_idx, h in enumerate(table_headers):
            t.cell(0, c_idx).text = h
        for r_idx, row in enumerate(table_rows, start=1):
            for c_idx, val in enumerate(row):
                t.cell(r_idx, c_idx).text = str(val)

    doc.save(file_path)


def generate_all_sample_bundles():
    manifest = {"bundles": {}}

    # --------------------------------------------------------------------------
    # Bundle 1: 01_tp2_over_budget_v1
    # --------------------------------------------------------------------------
    b1_dir = os.path.join(BASE_OUTPUT_DIR, "01_tp2_over_budget_v1")
    b1_pdf = os.path.join(b1_dir, "01_tp2_basvuru_formu.pdf")
    b1_xlsx = os.path.join(b1_dir, "02_butce_tablosu.xlsx")

    create_digital_pdf(
        file_path=b1_pdf,
        title="YÜKSEK LİSANS TEZ PROJESİ BAŞVURU FORMU",
        form_type="BAP Proje Başvuru Formu (TP2)",
        fields=[
            ("Proje Türü", "TP2 - Yüksek Lisans Tez Projeleri"),
            ("Proje Yürütücüsü", "Doç. Dr. Ahmet Yılmaz"),
            ("Görevi / Birimi", "Fen Fakültesi, Kimya Bölümü"),
            ("Proje Başlığı", "Nanopartikül Katkılı Biyosensör Sistemlerinin Geliştirilmesi"),
            ("Talep Edilen Toplam Bütçe", "142.000,00 TL"),
            ("Talep Edilen Süre", "24 Ay"),
            ("Tez Öğrencisi", "Arş. Gör. Zeynep Kaya"),
        ],
        table_headers=["Sıra", "Kalem Adı", "Kategori", "Miktar", "Birim Fiyat", "Toplam Tutar (TL)"],
        table_rows=[
            ["1", "Kimyasal ve Biyolojik Sarf Reaktifler", "Sarf Malzeme", "3 Kutu", "30.000,00 TL", "90.000,00 TL"],
            ["2", "Spektrofotometre Analiz Hizmeti", "Hizmet Alımı", "1 Paket", "32.000,00 TL", "32.000,00 TL"],
            ["3", "Saha ve Numune Toplama Yolluğu", "Yolluk / Seyahat", "10 Gün", "2.000,00 TL", "20.000,00 TL"],
        ],
        is_signed=True
    )

    create_excel_budget(
        file_path=b1_xlsx,
        sheet_name="Bütçe Dağılımı",
        headers=["Kalem No", "Malzeme / Hizmet Tanımı", "Kategori", "Miktar", "Birim", "Birim Fiyat (TL)", "Toplam Tutar (TL)"],
        rows=[
            [1, "Kimyasal ve Biyolojik Sarf Reaktifler", "CONSUMABLE", 3, "Kutu", 30000.0, 90000.0],
            [2, "Spektrofotometre Analiz Hizmeti", "SERVICE_PURCHASE", 1, "Paket", 32000.0, 32000.0],
            [3, "Saha ve Numune Toplama Yolluğu", "TRAVEL", 10, "Gün", 2000.0, 20000.0]
        ],
        total_val=142000.0
    )

    manifest["bundles"]["01_tp2_over_budget_v1"] = {
        "submission_type": "NEW_PROJECT",
        "project_code": "TP2",
        "applicant_name": "Ahmet Yılmaz",
        "applicant_title": "Doç. Dr.",
        "department": "Kimya Bölümü",
        "requested_budget_total_tl": 142000.0,
        "cap_limit_tl": 125000.0,
        "requested_duration_months": 24,
        "wet_signature_present": True,
        "expected_violations": [
            "RULE_BUDGET_CAP (142.000 TL > 125.000 TL tavanı)",
            "RULE_REQUIRED_ATTACHMENTS (Eksik Belge: THESIS_PROPOSAL Enstitü Tez Öneri Kararı)"
        ]
    }

    # --------------------------------------------------------------------------
    # Bundle 2: 02_tp2_compliant_v2
    # --------------------------------------------------------------------------
    b2_dir = os.path.join(BASE_OUTPUT_DIR, "02_tp2_compliant_v2")
    b2_pdf = os.path.join(b2_dir, "01_tp2_basvuru_formu_v2.pdf")
    b2_xlsx = os.path.join(b2_dir, "02_butce_tablosu_v2.xlsx")
    b2_thesis = os.path.join(b2_dir, "03_enstitu_tez_oneri_karari.pdf")
    b2_proforma = os.path.join(b2_dir, "04_proforma_fatura_kimyasallar.pdf")

    create_digital_pdf(
        file_path=b2_pdf,
        title="YÜKSEK LİSANS TEZ PROJESİ BAŞVURU FORMU (REVİZYON V2)",
        form_type="BAP Proje Başvuru Formu (TP2)",
        fields=[
            ("Proje Türü", "TP2 - Yüksek Lisans Tez Projeleri"),
            ("Proje Yürütücüsü", "Doç. Dr. Ahmet Yılmaz"),
            ("Görevi / Birimi", "Fen Fakültesi, Kimya Bölümü"),
            ("Proje Başlığı", "Nanopartikül Katkılı Biyosensör Sistemlerinin Geliştirilmesi"),
            ("Talep Edilen Toplam Bütçe", "118.500,00 TL"),
            ("Talep Edilen Süre", "24 Ay"),
            ("Tez Öğrencisi", "Arş. Gör. Zeynep Kaya"),
        ],
        table_headers=["Sıra", "Kalem Adı", "Kategori", "Miktar", "Birim Fiyat", "Toplam Tutar (TL)"],
        table_rows=[
            ["1", "Kimyasal ve Biyolojik Sarf Reaktifler", "Sarf Malzeme", "2 Kutu", "35.000,00 TL", "70.000,00 TL"],
            ["2", "Spektrofotometre Analiz Hizmeti", "Hizmet Alımı", "1 Paket", "28.500,00 TL", "28.500,00 TL"],
            ["3", "Saha ve Numune Toplama Yolluğu", "Yolluk / Seyahat", "10 Gün", "2.000,00 TL", "20.000,00 TL"],
        ],
        is_signed=True
    )

    create_excel_budget(
        file_path=b2_xlsx,
        sheet_name="Bütçe Dağılımı V2",
        headers=["Kalem No", "Malzeme / Hizmet Tanımı", "Kategori", "Miktar", "Birim", "Birim Fiyat (TL)", "Toplam Tutar (TL)"],
        rows=[
            [1, "Kimyasal ve Biyolojik Sarf Reaktifler", "CONSUMABLE", 2, "Kutu", 35000.0, 70000.0],
            [2, "Spektrofotometre Analiz Hizmeti", "SERVICE_PURCHASE", 1, "Paket", 28500.0, 28500.0],
            [3, "Saha ve Numune Toplama Yolluğu", "TRAVEL", 10, "Gün", 2000.0, 20000.0]
        ],
        total_val=118500.0
    )

    create_digital_pdf(
        file_path=b2_thesis,
        title="FEN BİLİMLERİ ENSTİTÜSÜ MÜDÜRLÜĞÜ",
        form_type="Enstitü Yönetim Kurulu Tez Önerisi Kabul Kararı",
        fields=[
            ("Enstitü", "Fen Bilimleri Enstitüsü"),
            ("Karar Tarih ve No", "15.01.2026 - Karar No: 2026/04"),
            ("Öğrenci Adı Soyadı", "Zeynep Kaya"),
            ("Danışman Öğretim Üyesi", "Doç. Dr. Ahmet Yılmaz"),
            ("Tez Konusu ve Başlığı", "Nanopartikül Katkılı Biyosensör Sistemlerinin Geliştirilmesi"),
            ("Yönetim Kurulu Kararı", "Tez önerisi oy birliği ile kabul edilmiştir.")
        ],
        signee_title_name="Prof. Dr. Enstitü Müdürü",
        signee_role="Enstitü Müdürü",
        is_signed=True
    )

    create_digital_pdf(
        file_path=b2_proforma,
        title="PROFORMA FATURA VE FİYAT TEKLİF MEKTUBU",
        form_type="Proforma Fatura (Teklif No: PF-2026-9041)",
        fields=[
            ("Teklif Veren Firma", "Mersin Kimya & Analitik Cihazlar Ltd. Şti."),
            ("Firma Vergi No", "6190284712"),
            ("Geçerlilik Tarihi", "90 Gün"),
            ("Teklif Verilen Birim", "Mersin Üniversitesi BAP Koordinatörlüğü"),
            ("KDV Durumu", "KDV Dahil"),
            ("Toplam Teklif Tutarı", "70.000,00 TL")
        ],
        table_headers=["Sıra", "Malzeme Tanımı ve Kodu", "Miktar", "Birim", "Birim Fiyat", "Tutar (TL)"],
        table_rows=[
            ["1", "Analitik Saflıkta Kimyasal Reaktif Kiti", "2", "Kutu", "35.000,00 TL", "70.000,00 TL"]
        ],
        signee_title_name="Firma Yetkilisi",
        signee_role="Yetkili Kaşe / İmza",
        is_signed=True
    )

    manifest["bundles"]["02_tp2_compliant_v2"] = {
        "submission_type": "NEW_PROJECT",
        "project_code": "TP2",
        "applicant_name": "Ahmet Yılmaz",
        "requested_budget_total_tl": 118500.0,
        "cap_limit_tl": 125000.0,
        "requested_duration_months": 24,
        "wet_signature_present": True,
        "attachments": ["APPLICATION_FORM", "BUDGET_TABLE", "THESIS_PROPOSAL", "PROFORMA_INVOICE"],
        "expected_compliance": "COMPLIANT (Bütçe tavanı içinde, tüm ek belgeler mevcut ve imzalı)"
    }

    # --------------------------------------------------------------------------
    # Bundle 3: 03_ap2_ethics_and_duration_violation
    # --------------------------------------------------------------------------
    b3_dir = os.path.join(BASE_OUTPUT_DIR, "03_ap2_ethics_and_duration_violation")
    b3_docx = os.path.join(b3_dir, "01_ap2_proje_onerisi.docx")
    b3_xlsx = os.path.join(b3_dir, "02_ap2_butce_ve_yolluk.xlsx")

    create_word_docx(
        file_path=b3_docx,
        title="BİREYSEL ARAŞTIRMA PROJESİ ÖNERİ FORMU (AP2)",
        fields=[
            ("Proje Türü", "AP2 - Bireysel Araştırma Projeleri"),
            ("Proje Yürütücüsü", "Prof. Dr. Mehmet Kaya"),
            ("Görevi / Birimi", "Tıp Fakültesi, Halk Sağlığı Anabilim Dalı"),
            ("Proje Başlığı", "Mersin Bölgesi Kronik Hastalarda Yaşam Kalitesi Değerlendirmesi"),
            ("Talep Edilen Toplam Bütçe", "285.000,00 TL"),
            ("Talep Edilen Süre", "42 Ay"),  # VIOLATION: > 36 Ay
        ],
        paragraphs=[
            "Proje Yöntemi ve Materyal:",
            "Bu çalışma kapsamında Mersin ilindeki 250 diyabet ve hipertansiyon hastasından rutin kan örneği alınarak biyokimyasal parametreler ölçülecektir.",
            "Ayrıca katılımcı hastalara yüz yüze 45 soruluk anket ve klinik görüşme formu uygulanacaktır.",
            "Elde edilen hasta verisi istatistiksel analiz yöntemleriyle değerlendirilecektir.",
            "İmza Beyanı: [İMZA] /İmzalıdır/ Proje Yürütücüsü Prof. Dr. Mehmet Kaya"
        ],
        table_headers=["Sıra", "Kalem Adı", "Kategori", "Toplam Tutar (TL)"],
        table_rows=[
            ["1", "Biyokimyasal Test Kitleri", "CONSUMABLE", "150.000,00 TL"],
            ["2", "Anket Uygulama ve Veri Girişi Hizmet Alımı", "SERVICE_PURCHASE", "79.000,00 TL"],
            ["3", "Saha ve Yolluk Gündelik Kalemi", "TRAVEL", "56.000,00 TL"],
        ]
    )

    create_excel_budget(
        file_path=b3_xlsx,
        sheet_name="AP2 Bütçe ve Yolluk",
        headers=["No", "Kalem Tanımı", "Kategori", "Gün Sayısı", "Günlük Tutar (TL)", "Toplam Tutar (TL)"],
        rows=[
            [1, "Biyokimyasal Test Kitleri", "CONSUMABLE", "", "", 150000.0],
            [2, "Anket Uygulama Hizmet Alımı", "SERVICE_PURCHASE", "", "", 79000.0],
            [3, "Saha Araştırması Yolluk ve Gündelik", "TRAVEL", 20, 2800.0, 56000.0]  # VIOLATION: 2800 > H-Cetveli
        ],
        total_val=285000.0
    )

    manifest["bundles"]["03_ap2_ethics_and_duration_violation"] = {
        "submission_type": "NEW_PROJECT",
        "project_code": "AP2",
        "applicant_name": "Mehmet Kaya",
        "applicant_title": "Prof. Dr.",
        "requested_budget_total_tl": 285000.0,
        "requested_duration_months": 42,
        "expected_violations": [
            "RULE_DURATION_MAX_36M (42 Ay talep edildi > 36 Ay azami süre)",
            "RULE_REQUIRED_ATTACHMENTS (Eksik Belge: ETHICS_APPROVAL Hasta anketi ve kan örneği için etik kurul belgesi zorunludur)",
            "RULE_PER_DIEM_HCETVELI (Günlük 2.800 TL gündelik 2026 H Cetveli tavanını aşmaktadır)"
        ]
    }

    # --------------------------------------------------------------------------
    # Bundle 4: 04_open_access_wos_q1_valid
    # --------------------------------------------------------------------------
    b4_dir = os.path.join(BASE_OUTPUT_DIR, "04_open_access_wos_q1_valid")
    b4_pdf = os.path.join(b4_dir, "01_acik_erisim_talep_formu.pdf")
    b4_proof = os.path.join(b4_dir, "02_wos_jcr_q1_kanit_belgesi.pdf")
    b4_invoice = os.path.join(b4_dir, "03_yayinevi_kabul_ve_apc_fatura.pdf")

    create_digital_pdf(
        file_path=b4_pdf,
        title="AÇIK ERİŞİM YAYIN BASIM ÜCRETİ DESTEK TALEP FORMU",
        form_type="Açık Erişim Destek Başvurusu (WOS Q1 %10)",
        fields=[
            ("Talep Türü", "Açık Erişim Yayın Basım Ücreti Desteği (Open Access)"),
            ("Başvuran Öğretim Üyesi", "Prof. Dr. Ayşe Demir"),
            ("Görevi / Birimi", "Mühendislik Fakültesi, Biyomühendislik Bölümü"),
            ("Makale Başlığı", "Novel Targeted Drug Delivery Vehicles in Oncology"),
            ("Dergi Adı", "Journal of Controlled Release"),
            ("Dergi Çeyreklik Dilimi", "WoS Q1 (%10'luk dilim - Top %10)"),
            ("Talep Edilen Destek Tutarı", "110.000,00 TL (KDV Dahil)"),
            ("BAP Proje Teşekkür Beyanı", "Makalede Mersin Üniversitesi BAP Birimine açıkça teşekkür edilmiştir.")
        ],
        is_signed=True
    )

    create_digital_pdf(
        file_path=b4_proof,
        title="WEB OF SCIENCE - JOURNAL CITATION REPORTS (JCR)",
        form_type="WoS Dergi Çeyreklik Kanıt Belgesi",
        fields=[
            ("Dergi Adı", "Journal of Controlled Release"),
            ("ISSN", "0168-3659"),
            ("Kategori", "Pharmacology & Pharmacy"),
            ("JIF Quartile Score", "Q1 (%10'luk Dilim)"),
            ("JIF Percentile", "94.8% (Top 10%)"),
            ("Veri Yılı", "2024 / 2025 JCR Raporu"),
            ("Adres Doğrulaması", "Tüm yazarların kurum adresi: Mersin University, Mersin, Turkey")
        ],
        signee_title_name="Prof. Dr. Ayşe Demir",
        signee_role="Sorumlu Yazar",
        is_signed=True
    )

    create_digital_pdf(
        file_path=b4_invoice,
        title="ELSEVIER PUBLISHING APC INVOICE & ACCEPTANCE",
        form_type="Makale Kabul ve Proforma Fatura",
        fields=[
            ("Publisher", "Elsevier B.V."),
            ("Article DOI", "10.1016/j.jconrel.2026.01.042"),
            ("Article Processing Charge (APC)", "110.000,00 TL"),
            ("Status", "Accepted for Publication - In Press"),
            ("Payment Reference", "INV-2026-ELSEV-88219"),
            ("KDV", "KDV Dahil")
        ],
        signee_title_name="Elsevier Editorial Office",
        signee_role="Publishing Director",
        is_signed=True
    )

    manifest["bundles"]["04_open_access_wos_q1_valid"] = {
        "submission_type": "OPEN_ACCESS",
        "journal_quartile": "Q1_TOP10",
        "applicant_name": "Ayşe Demir",
        "applicant_title": "Prof. Dr.",
        "requested_budget_total_tl": 110000.0,
        "cap_limit_tl": 120000.0,
        "wet_signature_present": True,
        "expected_compliance": "COMPLIANT (WoS Q1 %10 tavanı 120.000 TL içinde, tüm kanıtlar mevcut)"
    }

    # --------------------------------------------------------------------------
    # Bundle 5: 05_ek_sure_scanned_unsigned
    # --------------------------------------------------------------------------
    b5_dir = os.path.join(BASE_OUTPUT_DIR, "05_ek_sure_scanned_unsigned")
    b5_temp_pdf = os.path.join(b5_dir, "temp_digital_form.pdf")
    b5_scanned_pdf = os.path.join(b5_dir, "01_ek_sure_talep_formu_taranmis_imzasiz.pdf")

    create_digital_pdf(
        file_path=b5_temp_pdf,
        title="BAP PROJESİ EK SÜRE TALEP FORMU",
        form_type="Ek Süre Talep Formu ve Gerekçesi",
        fields=[
            ("Talep Türü", "Ek Süre Talebi (Additional Time)"),
            ("Proje Kodu", "AP2"),
            ("Proje Yürütücüsü", "Doç. Dr. Serkan Çelik"),
            ("Görevi / Birimi", "Mühendislik Fakültesi, Makine Mühendisliği"),
            ("Proje Başlığı", "Yüksek Dayanımlı Kompozit Malzemelerin Yorulma Analizi"),
            ("Mevcut Onaylanan Süre", "24 Ay"),
            ("Talep Edilen Ek Süre", "18 Ay"),  # VIOLATION: 24 + 18 = 42 Ay > 36 Ay
            ("Toplam Proje Süresi", "42 Ay"),
            ("Gerekçe", "Deprem ve tedarik zinciri aksaklıkları nedeniyle test düzeneği teslimi gecikmiştir.")
        ],
        signee_title_name="Doç. Dr. Serkan Çelik",
        signee_role="Proje Yürütücüsü",
        is_signed=False  # Unsigned!
    )

    convert_pdf_to_scanned_pdf(b5_temp_pdf, b5_scanned_pdf, add_blue_signature=False)
    if os.path.exists(b5_temp_pdf):
        os.remove(b5_temp_pdf)

    manifest["bundles"]["05_ek_sure_scanned_unsigned"] = {
        "submission_type": "ADDITIONAL_TIME",
        "project_code": "AP2",
        "applicant_name": "Serkan Çelik",
        "existing_duration_months": 24,
        "requested_duration_months": 18,
        "total_duration_months": 42,
        "is_scanned": True,
        "wet_signature_present": False,
        "expected_violations": [
            "RULE_DURATION_MAX_36M (24 + 18 = 42 Ay > 36 Ay azami süre)",
            "RULE_WET_SIGNATURE (Islak imza kutusu taranmış formda boş bırakılmıştır)"
        ]
    }

    # --------------------------------------------------------------------------
    # Bundle 5b: 05b_ek_sure_scanned_signed
    # --------------------------------------------------------------------------
    b5b_dir = os.path.join(BASE_OUTPUT_DIR, "05b_ek_sure_scanned_signed")
    b5b_temp_pdf = os.path.join(b5b_dir, "temp_digital_form.pdf")
    b5b_scanned_pdf = os.path.join(b5b_dir, "01_ek_sure_talep_formu_taranmis_imzali.pdf")

    create_digital_pdf(
        file_path=b5b_temp_pdf,
        title="BAP PROJESİ EK SÜRE TALEP FORMU",
        form_type="Ek Süre Talep Formu ve Gerekçesi",
        fields=[
            ("Talep Türü", "Ek Süre Talebi (Additional Time)"),
            ("Proje Kodu", "AP2"),
            ("Proje Yürütücüsü", "Doç. Dr. Serkan Çelik"),
            ("Görevi / Birimi", "Mühendislik Fakültesi, Makine Mühendisliği"),
            ("Proje Başlığı", "Yüksek Dayanımlı Kompozit Malzemelerin Yorulma Analizi"),
            ("Mevcut Onaylanan Süre", "24 Ay"),
            ("Talep Edilen Ek Süre", "12 Ay"),  # 24 + 12 = 36 Ay (Compliant duration)
            ("Toplam Proje Süresi", "36 Ay"),
            ("Gerekçe", "Deney düzeneği kalibrasyonu için ek süreye ihtiyaç duyulmaktadır.")
        ],
        signee_title_name="Doç. Dr. Serkan Çelik",
        signee_role="Proje Yürütücüsü",
        is_signed=True
    )

    convert_pdf_to_scanned_pdf(b5b_temp_pdf, b5b_scanned_pdf, add_blue_signature=True)
    if os.path.exists(b5b_temp_pdf):
        os.remove(b5b_temp_pdf)

    manifest["bundles"]["05b_ek_sure_scanned_signed"] = {
        "submission_type": "ADDITIONAL_TIME",
        "project_code": "AP2",
        "applicant_name": "Serkan Çelik",
        "existing_duration_months": 24,
        "requested_duration_months": 12,
        "total_duration_months": 36,
        "is_scanned": True,
        "wet_signature_present": True,
        "expected_compliance": "COMPLIANT (36 Ay süre sınırı içinde, mavi mürekkepli ıslak imza mevcut)"
    }

    # Write Manifest File
    manifest_path = os.path.join(BASE_OUTPUT_DIR, "manifest.json")
    with open(manifest_path, "w", encoding="utf-8") as f:
        json.dump(manifest, f, ensure_ascii=False, indent=2)

    print(f"Sample bundles successfully generated in {BASE_OUTPUT_DIR}")
    print(f"Manifest written to {manifest_path}")


if __name__ == "__main__":
    generate_all_sample_bundles()

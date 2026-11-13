import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { submissionsApi } from '../api/submissionsApi';
import { SubmissionType } from '../types/api';
import {
  UploadCloud,
  FileText,
  CheckCircle2,
  AlertCircle,
  File,
  X,
  Type,
  Sparkles,
  ArrowRight,
  ArrowLeft,
  ShieldCheck,
  Check,
} from 'lucide-react';

export const NewSubmissionPage: React.FC = () => {
  const navigate = useNavigate();

  // Wizard Step (1: Meta & Type, 2: Upload Files, 3: Selectable Modules)
  const [currentStep, setCurrentStep] = useState<1 | 2 | 3>(1);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Step 1: Form Fields
  const [submissionType, setSubmissionType] = useState<SubmissionType>('NEW_PROJECT');
  const [projectCode, setProjectCode] = useState('TP2');
  const [title, setTitle] = useState('');
  const [applicantName, setApplicantName] = useState('');
  const [applicantTitle, setApplicantTitle] = useState('Doç. Dr.');
  const [department, setDepartment] = useState('');
  const [externalBap2Id, setExternalBap2Id] = useState('');

  // Step 2: Files & Raw Text
  const [uploadMode, setUploadMode] = useState<'files' | 'raw_text'>('files');
  const [files, setFiles] = useState<File[]>([]);
  const [rawText, setRawText] = useState('');
  const [dragActive, setDragActive] = useState(false);

  // Step 3: Selectable Evaluation Modules (Explicit user requirement)
  const [moduleDeterministic, setModuleDeterministic] = useState(true);
  const [moduleRagChecklist, setModuleRagChecklist] = useState(true);
  const [moduleActionDrafts, setModuleActionDrafts] = useState(true);
  const [moduleInteractiveQa, setModuleInteractiveQa] = useState(true);
  const [moduleReportExport, setModuleReportExport] = useState(true);
  const [enableVisionFallback, setEnableVisionFallback] = useState(false);

  // Auto-fill template helper
  const handleAutoFillSample = (sampleType: 'TP2' | 'AP2' | 'OPEN_ACCESS') => {
    if (sampleType === 'TP2') {
      setSubmissionType('NEW_PROJECT');
      setProjectCode('TP2');
      setTitle('Akdeniz Kıyılarında Mikroplastik Kirliliğinin Biyoakümülasyon Analizi');
      setApplicantName('Doç. Dr. Selim Aktaş');
      setApplicantTitle('Doç. Dr.');
      setDepartment('Su Ürünleri Fakültesi');
      setExternalBap2Id('BAP2-99824');
    } else if (sampleType === 'AP2') {
      setSubmissionType('NEW_PROJECT');
      setProjectCode('AP2');
      setTitle('Yeni Nesil Grafen Takviyeli Biyouyumlu İmplant Yüzey Kaplamaları');
      setApplicantName('Prof. Dr. Ayşe Tuncer');
      setApplicantTitle('Prof. Dr.');
      setDepartment('Metalurji ve Malzeme Mühendisliği');
      setExternalBap2Id('BAP2-99712');
    } else {
      setSubmissionType('OPEN_ACCESS');
      setProjectCode('WOS_Q2');
      setTitle('Solar Cell Efficiency Improvement via Nano-Doped Perovskite Layers');
      setApplicantName('Dr. Öğr. Üyesi Mehmet Baran');
      setApplicantTitle('Dr. Öğr. Üyesi');
      setDepartment('Fen Fakültesi / Fizik');
      setExternalBap2Id('BAP2-99630');
    }
  };

  // Drag-and-drop handlers
  const handleDrag = (e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    if (e.type === 'dragenter' || e.type === 'dragover') {
      setDragActive(true);
    } else if (e.type === 'dragleave') {
      setDragActive(false);
    }
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setDragActive(false);
    if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
      const droppedFiles = Array.from(e.dataTransfer.files);
      setFiles((prev) => [...prev, ...droppedFiles]);
    }
  };

  const handleFileInput = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files.length > 0) {
      const selected = Array.from(e.target.files);
      setFiles((prev) => [...prev, ...selected]);
    }
  };

  const handleRemoveFile = (index: number) => {
    setFiles((prev) => prev.filter((_, i) => i !== index));
  };

  const handleFinalSubmit = async () => {
    setError(null);
    setLoading(true);
    try {
      // 1. Create base submission
      const finalTitle = title.trim() || 'Belgeden Otomatik Çıkarılacak';
      const finalApplicant = applicantName.trim() || 'Belgeden Otomatik Çıkarılacak';
      const sub = await submissionsApi.createSubmission({
        submission_type: submissionType,
        project_code: projectCode || null,
        title: finalTitle,
        applicant_name: finalApplicant,
        applicant_title: applicantTitle,
        department: department.trim() || null,
        external_bap2_id: externalBap2Id.trim() || null,
      });

      // 2. Prepare upload package
      const formData = new FormData();
      if (files.length > 0) {
        files.forEach((f) => formData.append('files', f));
      } else {
        // If raw text or empty, create synthetic text file
        const blob = new Blob([rawText || 'Örnek başvuru metni'], { type: 'text/plain' });
        formData.append('files', blob, 'Basvuru_Metni.txt');
      }
      formData.append('enable_vision_fallback', String(enableVisionFallback));
      formData.append(
        'officer_notes',
        `Seçilen Modüller: Deterministik=${moduleDeterministic}, RAG=${moduleRagChecklist}, İade/Sevk=${moduleActionDrafts}, QA=${moduleInteractiveQa}, Export=${moduleReportExport}`
      );

      // 3. Create version (initiates OCR extraction)
      await submissionsApi.createSubmissionVersion(sub.id, formData);

      // 4. Forward directly to Side-by-Side OCR review
      navigate(`/submissions/${sub.id}/versions/1/review`);
    } catch (err: any) {
      setError(err?.message || 'Başvuru paketi yüklenirken hata oluştu.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="max-w-4xl mx-auto space-y-6">
      {/* Wizard Header */}
      <div>
        <h2 className="text-xl font-bold text-slate-900 tracking-tight">
          Yeni BAP Başvuru Paketi ve Modül Seçim Sihirbazı
        </h2>
        <p className="text-xs text-slate-500 mt-1">
          BAP başvuru belgelerini yükleyin, OCR çıkarımını başlatın ve denetim modüllerini seçin
        </p>
      </div>

      {/* Stepper Bar */}
      <div className="bg-white rounded-xl border border-slate-200 p-4 shadow-xs">
        <div className="grid grid-cols-3 gap-2">
          <button
            onClick={() => setCurrentStep(1)}
            className={`flex items-center gap-2 p-2 rounded-lg text-left text-xs font-semibold transition-colors ${
              currentStep === 1
                ? 'bg-meu-50 text-meu-800 border border-meu-200'
                : 'text-slate-500 hover:bg-slate-50'
            }`}
          >
            <span className={`w-5 h-5 rounded-full flex items-center justify-center text-[11px] font-bold ${
              currentStep === 1 ? 'bg-meu-800 text-white' : 'bg-slate-200 text-slate-700'
            }`}>
              1
            </span>
            <span>Talep Türü ve Kimlik</span>
          </button>

          <button
            onClick={() => setCurrentStep(2)}
            className={`flex items-center gap-2 p-2 rounded-lg text-left text-xs font-semibold transition-colors ${
              currentStep === 2
                ? 'bg-meu-50 text-meu-800 border border-meu-200'
                : 'text-slate-500 hover:bg-slate-50'
            }`}
          >
            <span className={`w-5 h-5 rounded-full flex items-center justify-center text-[11px] font-bold ${
              currentStep === 2 ? 'bg-meu-800 text-white' : 'bg-slate-200 text-slate-700'
            }`}>
              2
            </span>
            <span>Dosya ve Belge Paketi</span>
          </button>

          <button
            onClick={() => setCurrentStep(3)}
            className={`flex items-center gap-2 p-2 rounded-lg text-left text-xs font-semibold transition-colors ${
              currentStep === 3
                ? 'bg-meu-50 text-meu-800 border border-meu-200'
                : 'text-slate-500 hover:bg-slate-50'
            }`}
          >
            <span className={`w-5 h-5 rounded-full flex items-center justify-center text-[11px] font-bold ${
              currentStep === 3 ? 'bg-meu-800 text-white' : 'bg-slate-200 text-slate-700'
            }`}>
              3
            </span>
            <span>Denetim Modülleri</span>
          </button>
        </div>
      </div>

      {error && (
        <div className="rounded-lg bg-rose-50 p-3.5 border border-rose-200 text-xs text-rose-700 flex items-center gap-2">
          <AlertCircle className="w-4 h-4 shrink-0" />
          <span>{error}</span>
        </div>
      )}

      {/* Step 1: Request Type and Metadata */}
      {currentStep === 1 && (
        <div className="bg-white rounded-xl border border-slate-200 p-6 shadow-xs space-y-6">
          <div className="flex items-center justify-between border-b border-slate-100 pb-3">
            <div>
              <h3 className="text-sm font-bold text-slate-900">1. Adım: Başvuru ve Yürütücü Bilgileri</h3>
              <p className="text-xs text-slate-500 mt-0.5">
                BAP Otomasyonundan veya fiziki teslim formundan alınan temel başvuru tanımları
              </p>
            </div>
            {/* Auto-fill buttons */}
            <div className="flex items-center gap-1.5">
              <span className="text-[11px] text-slate-400 font-medium">Hızlı Doldur:</span>
              <button
                type="button"
                onClick={() => handleAutoFillSample('TP2')}
                className="px-2 py-1 text-[11px] font-semibold rounded bg-slate-100 hover:bg-slate-200 text-slate-700 transition-colors"
              >
                TP2 Örneği
              </button>
              <button
                type="button"
                onClick={() => handleAutoFillSample('AP2')}
                className="px-2 py-1 text-[11px] font-semibold rounded bg-slate-100 hover:bg-slate-200 text-slate-700 transition-colors"
              >
                AP2 Örneği
              </button>
              <button
                type="button"
                onClick={() => handleAutoFillSample('OPEN_ACCESS')}
                className="px-2 py-1 text-[11px] font-semibold rounded bg-slate-100 hover:bg-slate-200 text-slate-700 transition-colors"
              >
                WoS Açık Erişim
              </button>
            </div>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            {/* Request Type */}
            <div>
              <label className="block text-xs font-semibold text-slate-700 mb-1">
                BAP Talep Türü <span className="text-rose-500">*</span>
              </label>
              <select
                value={submissionType}
                onChange={(e) => setSubmissionType(e.target.value as SubmissionType)}
                className="w-full py-2 px-3 text-xs border border-slate-300 rounded-lg focus:ring-2 focus:ring-meu-500 focus:border-meu-500 bg-white"
              >
                <option value="NEW_PROJECT">Yeni Proje Başvurusu</option>
                <option value="OPEN_ACCESS">Açık Erişim Yayın Basım Ücreti (APC)</option>
                <option value="ADDITIONAL_BUDGET">Ek Bütçe Talebi</option>
                <option value="ADDITIONAL_TIME">Ek Süre Talebi</option>
                <option value="PROCUREMENT_ADVANCE">Satın Alma / Avans / Yolluk Talebi</option>
              </select>
            </div>

            {/* Project Code */}
            <div>
              <label className="block text-xs font-semibold text-slate-700 mb-1">
                Proje Kodu / Destek Türü <span className="text-rose-500">*</span>
              </label>
              <select
                value={projectCode}
                onChange={(e) => setProjectCode(e.target.value)}
                className="w-full py-2 px-3 text-xs border border-slate-300 rounded-lg focus:ring-2 focus:ring-meu-500 focus:border-meu-500 bg-white font-mono"
              >
                <option value="TP1">TP1: Lisans Öğrencisi Araştırma Projesi (30.000 TL)</option>
                <option value="TP2">TP2: Yüksek Lisans Tez Projeleri (125.000 TL)</option>
                <option value="TP3">TP3: Doktora / Uzmanlık Projeleri (210.000 TL)</option>
                <option value="AP1">AP1: Kariyer Başlangıç Araştırma Projeleri (250.000 TL)</option>
                <option value="AP2">AP2: Bireysel Araştırma Projeleri (300.000 TL)</option>
                <option value="AP3">AP3: Öncelikli Alan Araştırma Projeleri (500.000 TL)</option>
                <option value="AP4">AP4: Kapsamlı Araştırma Projeleri (800.000 TL)</option>
                <option value="AP7">AP7: Hızlı Destek Araştırma Projeleri (100.000 TL)</option>
                <option value="AP9">AP9: Yarışmalara Katılım Desteği (5.000 TL)</option>
                <option value="WOS_Q1_TOP10">WOS_Q1_TOP10: WoS Q1 %10 Dilim (120.000 TL)</option>
                <option value="WOS_Q1_OTHER">WOS_Q1_OTHER: WoS Q1 Diğer (80.000 TL)</option>
                <option value="WOS_Q2">WOS_Q2: WoS Q2 Tam Metin Makale (60.000 TL)</option>
              </select>
            </div>

            {/* Applicant Name */}
            <div>
              <label className="block text-xs font-semibold text-slate-700 mb-1">
                Yürütücü Adı Soyadı <span className="text-slate-400 font-normal">(Boş bırakılırsa OCR çıkarır)</span>
              </label>
              <input
                type="text"
                value={applicantName}
                onChange={(e) => setApplicantName(e.target.value)}
                placeholder="Ör. Doç. Dr. Selim Aktaş veya boş bırakın"
                className="w-full py-2 px-3 text-xs border border-slate-300 rounded-lg focus:ring-2 focus:ring-meu-500 focus:border-meu-500 bg-white"
              />
            </div>

            {/* Applicant Title */}
            <div>
              <label className="block text-xs font-semibold text-slate-700 mb-1">
                Yürütücü Unvanı
              </label>
              <select
                value={applicantTitle}
                onChange={(e) => setApplicantTitle(e.target.value)}
                className="w-full py-2 px-3 text-xs border border-slate-300 rounded-lg focus:ring-2 focus:ring-meu-500 focus:border-meu-500 bg-white"
              >
                <option value="Prof. Dr.">Prof. Dr.</option>
                <option value="Doç. Dr.">Doç. Dr.</option>
                <option value="Dr. Öğr. Üyesi">Dr. Öğr. Üyesi</option>
                <option value="Öğr. Gör. Dr.">Öğr. Gör. Dr.</option>
                <option value="Arş. Gör. Dr.">Arş. Gör. Dr.</option>
              </select>
            </div>

            {/* Department */}
            <div className="sm:col-span-2">
              <label className="block text-xs font-semibold text-slate-700 mb-1">
                Fakülte / Bölüm / Anabilim Dalı
              </label>
              <input
                type="text"
                value={department}
                onChange={(e) => setDepartment(e.target.value)}
                placeholder="Ör. Su Ürünleri Fakültesi / Deniz Biyolojisi AD"
                className="w-full py-2 px-3 text-xs border border-slate-300 rounded-lg focus:ring-2 focus:ring-meu-500 focus:border-meu-500 bg-white"
              />
            </div>

            {/* Title */}
            <div className="sm:col-span-2">
              <label className="block text-xs font-semibold text-slate-700 mb-1">
                Proje / Talep Başlığı <span className="text-slate-400 font-normal">(Boş bırakılırsa OCR çıkarır)</span>
              </label>
              <input
                type="text"
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                placeholder="Ör. Akdeniz Kıyılarında Mikroplastik Analizi veya boş bırakın"
                className="w-full py-2 px-3 text-xs border border-slate-300 rounded-lg focus:ring-2 focus:ring-meu-500 focus:border-meu-500 bg-white font-medium"
              />
            </div>

            {/* BAP2 ID */}
            <div>
              <label className="block text-xs font-semibold text-slate-700 mb-1">
                Harici bap2.mersin.edu.tr Talep No (Opsiyonel)
              </label>
              <input
                type="text"
                value={externalBap2Id}
                onChange={(e) => setExternalBap2Id(e.target.value)}
                placeholder="Ör. BAP2-99824"
                className="w-full py-2 px-3 text-xs border border-slate-300 rounded-lg focus:ring-2 focus:ring-meu-500 focus:border-meu-500 bg-white font-mono"
              />
            </div>
          </div>

          <div className="flex justify-end pt-4 border-t border-slate-100">
            <button
              type="button"
              onClick={() => setCurrentStep(2)}
              className="inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-meu-900 hover:bg-meu-800 text-white text-xs font-semibold shadow-xs transition-colors"
            >
              <span>Belge Yüklemeye Geç</span>
              <ArrowRight className="w-4 h-4" />
            </button>
          </div>
        </div>
      )}

      {/* Step 2: Upload Files & Text */}
      {currentStep === 2 && (
        <div className="bg-white rounded-xl border border-slate-200 p-6 shadow-xs space-y-6">
          <div className="flex items-center justify-between border-b border-slate-100 pb-3">
            <div>
              <h3 className="text-sm font-bold text-slate-900">2. Adım: Başvuru Belgesi ve Ekleri</h3>
              <p className="text-xs text-slate-500 mt-0.5">
                PDF (dijital/taranmış), Word (DOCX), Excel (XLSX), Görsel (JPG/PNG) veya ZIP yükleyin
              </p>
            </div>
            {/* Mode switch */}
            <div className="inline-flex rounded-lg border border-slate-200 p-0.5 bg-slate-100 text-xs">
              <button
                type="button"
                onClick={() => setUploadMode('files')}
                className={`px-3 py-1 rounded-md font-semibold transition-colors ${
                  uploadMode === 'files' ? 'bg-white shadow-xs text-meu-900' : 'text-slate-500'
                }`}
              >
                Çoklu Dosya Yükle
              </button>
              <button
                type="button"
                onClick={() => setUploadMode('raw_text')}
                className={`px-3 py-1 rounded-md font-semibold transition-colors ${
                  uploadMode === 'raw_text' ? 'bg-white shadow-xs text-meu-900' : 'text-slate-500'
                }`}
              >
                Düz Metin Yapıştır
              </button>
            </div>
          </div>

          {uploadMode === 'files' ? (
            <div className="space-y-4">
              {/* Drag and Drop Zone */}
              <div
                onDragEnter={handleDrag}
                onDragLeave={handleDrag}
                onDragOver={handleDrag}
                onDrop={handleDrop}
                className={`border-2 border-dashed rounded-xl p-8 text-center transition-colors ${
                  dragActive
                    ? 'border-meu-600 bg-meu-50/50'
                    : 'border-slate-300 hover:border-slate-400 bg-slate-50/40'
                }`}
              >
                <div className="max-w-md mx-auto space-y-3">
                  <div className="w-12 h-12 rounded-full bg-meu-100 text-meu-700 flex items-center justify-center mx-auto">
                    <UploadCloud className="w-6 h-6" />
                  </div>
                  <div>
                    <label className="text-xs font-bold text-meu-700 hover:text-meu-800 cursor-pointer">
                      <span>Dosyaları seçmek için tıklayın</span>
                      <input
                        type="file"
                        multiple
                        accept=".pdf,.docx,.xlsx,.xls,.png,.jpg,.jpeg,.zip"
                        onChange={handleFileInput}
                        className="hidden"
                      />
                    </label>
                    <span className="text-xs text-slate-500"> veya buraya sürükleyip bırakın</span>
                  </div>
                  <p className="text-[11px] text-slate-400">
                    Desteklenen: PDF (Islak imzalı taranmış/dijital), DOCX, XLSX bütçe tablosu, ZIP paketi (Azami 50MB)
                  </p>
                </div>
              </div>

              {/* Uploaded Files List */}
              {files.length > 0 && (
                <div className="space-y-2">
                  <p className="text-xs font-semibold text-slate-700">Yüklenecek Belgeler ({files.length}):</p>
                  <div className="divide-y divide-slate-100 border border-slate-200 rounded-lg overflow-hidden bg-slate-50/30">
                    {files.map((file, idx) => (
                      <div key={idx} className="flex items-center justify-between p-2.5 text-xs">
                        <div className="flex items-center gap-2 truncate">
                          <File className="w-4 h-4 text-meu-600 shrink-0" />
                          <span className="font-medium text-slate-800 truncate">{file.name}</span>
                          <span className="text-[10px] text-slate-400 shrink-0">
                            ({(file.size / (1024 * 1024)).toFixed(2)} MB)
                          </span>
                        </div>
                        <button
                          type="button"
                          onClick={() => handleRemoveFile(idx)}
                          className="p-1 rounded text-slate-400 hover:text-rose-600 hover:bg-rose-50 transition-colors"
                        >
                          <X className="w-4 h-4" />
                        </button>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          ) : (
            <div className="space-y-2">
              <label className="block text-xs font-semibold text-slate-700">
                Serbest Başvuru / Form Metni (Test ve OCR Simülasyonu)
              </label>
              <textarea
                rows={10}
                value={rawText}
                onChange={(e) => setRawText(e.target.value)}
                placeholder="Örnek: Mersin Üniversitesi BAP Birimine... TP2 Yüksek Lisans Tez Projesi Başvurusu... Talep Edilen Bütçe: 124.000 TL..."
                className="w-full p-3 text-xs font-mono border border-slate-300 rounded-lg focus:ring-2 focus:ring-meu-500 focus:border-meu-500 bg-white"
              />
            </div>
          )}

          <div className="flex justify-between pt-4 border-t border-slate-100">
            <button
              type="button"
              onClick={() => setCurrentStep(1)}
              className="inline-flex items-center gap-1.5 px-3 py-2 rounded-lg border border-slate-300 hover:bg-slate-50 text-slate-700 text-xs font-semibold transition-colors"
            >
              <ArrowLeft className="w-4 h-4" />
              <span>Geri</span>
            </button>
            <button
              type="button"
              onClick={() => setCurrentStep(3)}
              className="inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-meu-900 hover:bg-meu-800 text-white text-xs font-semibold shadow-xs transition-colors"
            >
              <span>Modül Seçimine Geç</span>
              <ArrowRight className="w-4 h-4" />
            </button>
          </div>
        </div>
      )}

      {/* Step 3: Selectable Evaluation Modules */}
      {currentStep === 3 && (
        <div className="bg-white rounded-xl border border-slate-200 p-6 shadow-xs space-y-6">
          <div className="border-b border-slate-100 pb-3">
            <h3 className="text-sm font-bold text-slate-900">3. Adım: Çalıştırılacak Ön İnceleme Modülleri</h3>
            <p className="text-xs text-slate-500 mt-0.5">
              İhtiyacınıza göre deterministik kural, mevzuat RAG checklist ve rapor üretim modüllerini işaretleyin
            </p>
          </div>

          <div className="space-y-3">
            {/* Module 1: Deterministic Rules */}
            <label className="flex items-start gap-3 p-3.5 rounded-xl border border-slate-200 hover:bg-slate-50/60 cursor-pointer transition-colors">
              <input
                type="checkbox"
                checked={moduleDeterministic}
                onChange={(e) => setModuleDeterministic(e.target.checked)}
                className="w-4 h-4 mt-0.5 text-meu-600 rounded border-slate-300 focus:ring-meu-500"
              />
              <div className="space-y-0.5">
                <div className="text-xs font-bold text-slate-900 flex items-center gap-2">
                  <span>Deterministik Sayısal Limit, Süre (&le; 36 Ay), H-Cetveli ve Islak İmza Kontrolü</span>
                  <span className="px-1.5 py-0.2 bg-blue-100 text-blue-800 text-[10px] font-bold rounded">
                    Önerilen
                  </span>
                </div>
                <p className="text-[11px] text-slate-500">
                  2026 tavanları (TP1–AP9), aritmetik kalem toplamı tutarlılığı, 36 ay azami süre ve imza varlığını denetler.
                </p>
              </div>
            </label>

            {/* Module 2: RAG Checklist */}
            <label className="flex items-start gap-3 p-3.5 rounded-xl border border-slate-200 hover:bg-slate-50/60 cursor-pointer transition-colors">
              <input
                type="checkbox"
                checked={moduleRagChecklist}
                onChange={(e) => setModuleRagChecklist(e.target.checked)}
                className="w-4 h-4 mt-0.5 text-meu-600 rounded border-slate-300 focus:ring-meu-500"
              />
              <div className="space-y-0.5">
                <div className="text-xs font-bold text-slate-900 flex items-center gap-2">
                  <span>Mevzuat Madde Atıflı RAG Uygunluk Listesi (Checklist)</span>
                  <span className="px-1.5 py-0.2 bg-cyan-100 text-cyan-800 text-[10px] font-bold rounded">
                    Yönerge Madde Atıflı
                  </span>
                </div>
                <p className="text-[11px] text-slate-500">
                  MEÜ BAP Yönergesi, Uygulama Usul ve Esasları ve Açık Erişim kurallarına göre madde atıflı denetim tablosu oluşturur.
                </p>
              </div>
            </label>

            {/* Module 3: Action Drafts */}
            <label className="flex items-start gap-3 p-3.5 rounded-xl border border-slate-200 hover:bg-slate-50/60 cursor-pointer transition-colors">
              <input
                type="checkbox"
                checked={moduleActionDrafts}
                onChange={(e) => setModuleActionDrafts(e.target.checked)}
                className="w-4 h-4 mt-0.5 text-meu-600 rounded border-slate-300 focus:ring-meu-500"
              />
              <div className="space-y-0.5">
                <div className="text-xs font-bold text-slate-900">
                  <span>Hazır Aksiyon Metinleri (Yürütücü İade Notu & Profesör Kurulu Sevk Özeti)</span>
                </div>
                <p className="text-[11px] text-slate-500">
                  İhlal durumunda yürütücüye doğrudan kopyalanabilir iade yazısı, uygunluk durumunda kurula sevk özeti üretir.
                </p>
              </div>
            </label>

            {/* Module 4: Interactive QA */}
            <label className="flex items-start gap-3 p-3.5 rounded-xl border border-slate-200 hover:bg-slate-50/60 cursor-pointer transition-colors">
              <input
                type="checkbox"
                checked={moduleInteractiveQa}
                onChange={(e) => setModuleInteractiveQa(e.target.checked)}
                className="w-4 h-4 mt-0.5 text-meu-600 rounded border-slate-300 focus:ring-meu-500"
              />
              <div className="space-y-0.5">
                <div className="text-xs font-bold text-slate-900">
                  <span>Etkileşimli Mevzuat & Dosya Soru-Cevap Paneli</span>
                </div>
                <p className="text-[11px] text-slate-500">
                  Görevlinin başvuru ve MEÜ mevzuatına dair takip soruları sorabileceği iç RAG sohbet çekmecesini etkinleştirir.
                </p>
              </div>
            </label>

            {/* Module 5: Official Report Export */}
            <label className="flex items-start gap-3 p-3.5 rounded-xl border border-slate-200 hover:bg-slate-50/60 cursor-pointer transition-colors">
              <input
                type="checkbox"
                checked={moduleReportExport}
                onChange={(e) => setModuleReportExport(e.target.checked)}
                className="w-4 h-4 mt-0.5 text-meu-600 rounded border-slate-300 focus:ring-meu-500"
              />
              <div className="space-y-0.5">
                <div className="text-xs font-bold text-slate-900">
                  <span>Resmi Kurumsal PDF / DOCX Ön İnceleme Tutanağı Hazırlama</span>
                </div>
                <p className="text-[11px] text-slate-500">
                  Mersin Üniversitesi antetli resmi indirme çıktılarını hazır tutar.
                </p>
              </div>
            </label>

            {/* Vision Fallback Option */}
            <div className="pt-2">
              <label className="flex items-start gap-3 p-3 rounded-lg bg-amber-50/60 border border-amber-200 cursor-pointer">
                <input
                  type="checkbox"
                  checked={enableVisionFallback}
                  onChange={(e) => setEnableVisionFallback(e.target.checked)}
                  className="w-4 h-4 mt-0.5 text-amber-600 rounded border-slate-300 focus:ring-amber-500"
                />
                <div className="space-y-0.5">
                  <div className="text-xs font-bold text-amber-900">
                    Bozuk / Düşük Çözünürlüklü Taramalarda Vision-LLM OCR Desteği Kullan
                  </div>
                  <p className="text-[11px] text-amber-700">
                    Standart kural tabanlı Docling/RapidOCR yetersiz kaldığında devreye girer.
                  </p>
                </div>
              </label>
            </div>
          </div>

          <div className="flex justify-between pt-4 border-t border-slate-100">
            <button
              type="button"
              onClick={() => setCurrentStep(2)}
              className="inline-flex items-center gap-1.5 px-3 py-2 rounded-lg border border-slate-300 hover:bg-slate-50 text-slate-700 text-xs font-semibold transition-colors"
            >
              <ArrowLeft className="w-4 h-4" />
              <span>Geri</span>
            </button>
            <button
              type="button"
              disabled={loading}
              onClick={handleFinalSubmit}
              className="inline-flex items-center gap-2 px-5 py-2.5 rounded-lg bg-emerald-700 hover:bg-emerald-800 text-white text-xs font-bold shadow-md transition-colors cursor-pointer"
            >
              <Check className="w-4 h-4" />
              <span>{loading ? 'OCR Başlatılıyor...' : 'Başvuruyu Kaydet ve OCR İncelemesine Başla'}</span>
            </button>
          </div>
        </div>
      )}
    </div>
  );
};

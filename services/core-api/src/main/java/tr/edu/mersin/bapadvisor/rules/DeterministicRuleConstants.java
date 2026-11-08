package tr.edu.mersin.bapadvisor.rules;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Map;

public final class DeterministicRuleConstants {

    private DeterministicRuleConstants() {
        // utility class
    }

    // Rule Codes
    public static final String RULE_BUDGET_CAP = "RULE_BUDGET_CAP";
    public static final String RULE_BUDGET_ARITHMETIC = "RULE_BUDGET_ARITHMETIC";
    public static final String RULE_OPEN_ACCESS_CAP = "RULE_OPEN_ACCESS_CAP";
    public static final String RULE_DURATION_MAX_36M = "RULE_DURATION_MAX_36M";
    public static final String RULE_WET_SIGNATURE = "RULE_WET_SIGNATURE";
    public static final String RULE_REQUIRED_ATTACHMENTS = "RULE_REQUIRED_ATTACHMENTS";
    public static final String RULE_PER_DIEM_AND_PROCUREMENT = "RULE_PER_DIEM_AND_PROCUREMENT";
    public static final String RULE_APPLICANT_ELIGIBILITY = "RULE_APPLICANT_ELIGIBILITY";

    // Regulation Document Names
    public static final String DOC_USUL_ESASLAR = "Mersin Üniversitesi Bilimsel Araştırma Projeleri Uygulama Usul ve Esasları";
    public static final String DOC_YONERGE = "Mersin Üniversitesi Bilimsel Araştırma Projeleri Yönergesi";
    public static final String DOC_H_CETVELI = "2026 Yılı Merkezi Yönetim Bütçe Kanunu H Cetveli (Harcırah)";
    public static final String DOC_KIK = "4734 Sayılı Kamu İhale Kanunu (Doğrudan Temin Esasları)";
    public static final String DOC_ACIK_ERISIM = "Mersin Üniversitesi BAP Açık Erişim Yayın Desteği Esasları";

    // Fallback 2026 MEÜ Project Budget Caps (TRY)
    public static final Map<String, BigDecimal> DEFAULT_PROJECT_BUDGET_CAPS = Map.of(
            "TP1", new BigDecimal("30000.00"),
            "TP2", new BigDecimal("125000.00"),
            "TP3", new BigDecimal("210000.00"),
            "AP1", new BigDecimal("250000.00"),
            "AP2", new BigDecimal("300000.00"),
            "AP3", new BigDecimal("500000.00"),
            "AP4", new BigDecimal("800000.00"),
            "AP7", new BigDecimal("100000.00"),
            "AP9", new BigDecimal("5000.00")
    );

    // Fallback 2026 MEÜ Project Duration Caps (Months)
    public static final Map<String, Integer> DEFAULT_PROJECT_DURATION_CAPS = Map.of(
            "TP1", 12,
            "TP2", 24,
            "TP3", 36,
            "AP1", 24,
            "AP2", 36,
            "AP3", 36,
            "AP4", 36,
            "AP7", 12,
            "AP9", 6
    );

    // Fallback 2026 Open Access Publication Caps (TRY)
    public static final BigDecimal CAP_OPEN_ACCESS_Q1_TOP10 = new BigDecimal("120000.00");
    public static final BigDecimal CAP_OPEN_ACCESS_Q1_OTHER = new BigDecimal("80000.00");
    public static final BigDecimal CAP_OPEN_ACCESS_Q2 = new BigDecimal("60000.00");

    // Absolute Maximum Duration for Non-Thesis Projects
    public static final int MAX_DURATION_NON_THESIS_MONTHS = 36;

    // Direct Procurement and Purchasing Thresholds
    public static final BigDecimal DIRECT_PURCHASE_THRESHOLD_TL = new BigDecimal("620000.00");
    public static final BigDecimal PROFORMA_THRESHOLD_TL = new BigDecimal("25000.00");
    public static final int PROFORMA_MIN_COUNT = 3;
    public static final BigDecimal TECHNICAL_SPEC_THRESHOLD_TL = new BigDecimal("50000.00");

    // 2026 H-Cetveli Daily Rates (Domestic Travel / Per Diem in TRY)
    public static final BigDecimal PER_DIEM_PROFESSOR_TL = new BigDecimal("750.00");
    public static final BigDecimal PER_DIEM_ASSOC_PROF_TL = new BigDecimal("680.00");
    public static final BigDecimal PER_DIEM_ASST_PROF_TL = new BigDecimal("620.00");
    public static final BigDecimal PER_DIEM_INSTRUCTOR_TL = new BigDecimal("540.00");
    public static final BigDecimal PER_DIEM_STUDENT_OTHER_TL = new BigDecimal("500.00");

    // Arithmetic Tolerance (1.00 TL)
    public static final BigDecimal ARITHMETIC_TOLERANCE_TL = new BigDecimal("1.00");

    // Guardrail Notice
    public static final String GUARDRAIL_NOTICE = "Bu ön inceleme yalnızca şekli, idari ve mali mevzuat kontrolüdür. " +
            "Projenin bilimsel değeri, akademik özgünlüğü ve araştırma niteliği BAP Komisyonu yetkisindedir.";

    /**
     * Formats a BigDecimal as Turkish currency string: "125.000,00 TL"
     */
    public static String formatTl(BigDecimal amount) {
        if (amount == null) {
            return "0,00 TL";
        }
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(new Locale("tr", "TR"));
        symbols.setGroupingSeparator('.');
        symbols.setDecimalSeparator(',');
        DecimalFormat df = new DecimalFormat("#,##0.00", symbols);
        return df.format(amount) + " TL";
    }
}

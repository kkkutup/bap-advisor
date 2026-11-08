package tr.edu.mersin.bapadvisor.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import tr.edu.mersin.bapadvisor.common.RuleCategory;
import tr.edu.mersin.bapadvisor.common.UserRole;
import tr.edu.mersin.bapadvisor.limit.ProjectLimitConfig;
import tr.edu.mersin.bapadvisor.limit.ProjectLimitConfigRepository;
import tr.edu.mersin.bapadvisor.regulation.RegulationRecord;
import tr.edu.mersin.bapadvisor.regulation.RegulationRecordRepository;
import tr.edu.mersin.bapadvisor.user.User;
import tr.edu.mersin.bapadvisor.user.UserRepository;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.UUID;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final ProjectLimitConfigRepository limitConfigRepository;
    private final RegulationRecordRepository regulationRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    public DataInitializer(
            UserRepository userRepository,
            ProjectLimitConfigRepository limitConfigRepository,
            RegulationRecordRepository regulationRepository,
            PasswordEncoder passwordEncoder,
            ObjectMapper objectMapper
    ) {
        this.userRepository = userRepository;
        this.limitConfigRepository = limitConfigRepository;
        this.regulationRepository = regulationRepository;
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(String... args) {
        seedUsersIfEmpty();
        seedLimitsIfEmpty();
        seedRegulationsIfEmpty();
    }

    private void seedUsersIfEmpty() {
        if (userRepository.count() == 0) {
            log.info("Seeding default system users...");
            userRepository.save(new User(
                    UUID.fromString("11111111-1111-1111-1111-111111111111"),
                    "admin@mersin.edu.tr",
                    passwordEncoder.encode("Admin123!"),
                    "Sistem Yöneticisi",
                    UserRole.ADMIN
            ));

            userRepository.save(new User(
                    UUID.fromString("22222222-2222-2222-2222-222222222222"),
                    "koordinator@mersin.edu.tr",
                    passwordEncoder.encode("Koord123!"),
                    "Prof. Dr. BAP Koordinatörü",
                    UserRole.COORDINATOR
            ));

            userRepository.save(new User(
                    UUID.fromString("33333333-3333-3333-3333-333333333333"),
                    "memur@mersin.edu.tr",
                    passwordEncoder.encode("Memur123!"),
                    "BAP Ön İnceleme Memuru",
                    UserRole.OFFICER
            ));

            userRepository.save(new User(
                    UUID.fromString("44444444-4444-4444-4444-444444444444"),
                    "kurul@mersin.edu.tr",
                    passwordEncoder.encode("Kurul123!"),
                    "BAP Komisyon Üyesi (Profesör Kurulu)",
                    UserRole.COMMISSION_VIEWER
            ));
            log.info("Default users seeded successfully.");
        }
    }

    private void seedLimitsIfEmpty() {
        if (limitConfigRepository.count() == 0) {
            log.info("Seeding 2026 MEÜ BAP limits...");
            Path seedPath = Paths.get("../../contracts/seed-data/meu_2026_limits.json");
            File file = seedPath.toFile();
            if (!file.exists()) {
                seedPath = Paths.get("contracts/seed-data/meu_2026_limits.json");
                file = seedPath.toFile();
            }

            if (file.exists()) {
                try {
                    JsonNode root = objectMapper.readTree(file);
                    int year = root.has("fiscal_year") ? root.get("fiscal_year").asInt() : 2026;
                    LocalDate effectiveDate = LocalDate.of(year, 1, 1);

                    // Project limits
                    if (root.has("project_limits")) {
                        for (JsonNode item : root.get("project_limits")) {
                            ProjectLimitConfig cfg = new ProjectLimitConfig();
                            cfg.setFiscalYear(year);
                            cfg.setCode(item.get("code").asText());
                            cfg.setRuleCategory(RuleCategory.BUDGET);
                            cfg.setTitleTr(item.get("title_tr").asText());
                            cfg.setMaxAmountTl(new BigDecimal(item.get("max_amount_tl").asText()));
                            if (item.has("max_duration_months")) {
                                cfg.setMaxDurationMonths(item.get("max_duration_months").asInt());
                            }
                            cfg.setEffectiveFrom(effectiveDate);
                            cfg.setIsActive(true);
                            if (item.has("conditions")) {
                                cfg.setConditionsJson(item.get("conditions").toString());
                            }
                            limitConfigRepository.save(cfg);
                        }
                    }

                    // Open access limits
                    if (root.has("open_access_limits")) {
                        for (JsonNode item : root.get("open_access_limits")) {
                            ProjectLimitConfig cfg = new ProjectLimitConfig();
                            cfg.setFiscalYear(year);
                            cfg.setCode(item.get("code").asText());
                            cfg.setRuleCategory(RuleCategory.OPEN_ACCESS);
                            cfg.setTitleTr(item.get("title_tr").asText());
                            cfg.setMaxAmountTl(new BigDecimal(item.get("max_amount_tl").asText()));
                            cfg.setEffectiveFrom(effectiveDate);
                            cfg.setIsActive(true);
                            if (item.has("conditions")) {
                                cfg.setConditionsJson(item.get("conditions").toString());
                            }
                            limitConfigRepository.save(cfg);
                        }
                    }

                    // Duration rules
                    ProjectLimitConfig durCfg = new ProjectLimitConfig();
                    durCfg.setFiscalYear(year);
                    durCfg.setCode("MAX_MONTHS_NON_THESIS");
                    durCfg.setRuleCategory(RuleCategory.DURATION);
                    durCfg.setTitleTr("Tez Dışı Projeler Azami Süresi");
                    durCfg.setMaxAmountTl(BigDecimal.ZERO);
                    durCfg.setMaxDurationMonths(36);
                    durCfg.setEffectiveFrom(effectiveDate);
                    durCfg.setIsActive(true);
                    limitConfigRepository.save(durCfg);

                    log.info("Limits seeded from meu_2026_limits.json successfully.");
                    return;
                } catch (Exception e) {
                    log.warn("Failed to load limits from seed json: {}", e.getMessage());
                }
            }

            // Fallback hardcoded 2026 caps
            seedFallbackLimits();
        }
    }

    private void seedFallbackLimits() {
        LocalDate jan1 = LocalDate.of(2026, 1, 1);
        Object[][] defaults = {
                {"TP1", RuleCategory.BUDGET, "Lisans Öğrencisi Araştırma Projesi", 30000.0, 12},
                {"TP2", RuleCategory.BUDGET, "Yüksek Lisans Tez Projeleri", 125000.0, 24},
                {"TP3", RuleCategory.BUDGET, "Doktora / Uzmanlık / Sanatta Yeterlik", 210000.0, 36},
                {"AP1", RuleCategory.BUDGET, "Kariyer Başlangıç Araştırma Projeleri", 250000.0, 24},
                {"AP2", RuleCategory.BUDGET, "Bireysel Araştırma Projeleri", 300000.0, 36},
                {"AP3", RuleCategory.BUDGET, "Öncelikli Alan Araştırma Projeleri", 500000.0, 36},
                {"AP4", RuleCategory.BUDGET, "Kapsamlı Araştırma Projeleri", 800000.0, 36},
                {"AP7", RuleCategory.BUDGET, "Hızlı Destek Araştırma Projeleri", 100000.0, 12},
                {"AP9", RuleCategory.BUDGET, "Yarışmalara Katılım", 5000.0, 6},
                {"WOS_Q1_TOP10", RuleCategory.OPEN_ACCESS, "WoS Q1 (%10luk Dilim) Açık Erişim", 120000.0, null},
                {"WOS_Q1_OTHER", RuleCategory.OPEN_ACCESS, "WoS Q1 (Diğer) Açık Erişim", 80000.0, null},
                {"WOS_Q2", RuleCategory.OPEN_ACCESS, "WoS Q2 Açık Erişim", 60000.0, null},
                {"MAX_MONTHS_NON_THESIS", RuleCategory.DURATION, "Azami Proje Süresi", 0.0, 36}
        };

        for (Object[] row : defaults) {
            ProjectLimitConfig cfg = new ProjectLimitConfig();
            cfg.setFiscalYear(2026);
            cfg.setCode((String) row[0]);
            cfg.setRuleCategory((RuleCategory) row[1]);
            cfg.setTitleTr((String) row[2]);
            cfg.setMaxAmountTl(BigDecimal.valueOf((Double) row[3]));
            if (row[4] != null) {
                cfg.setMaxDurationMonths((Integer) row[4]);
            }
            cfg.setEffectiveFrom(jan1);
            cfg.setIsActive(true);
            limitConfigRepository.save(cfg);
        }
        log.info("Fallback 2026 caps seeded.");
    }

    private void seedRegulationsIfEmpty() {
        if (regulationRepository.count() == 0) {
            log.info("Seeding regulation records...");
            Object[][] regs = {
                    {"yok-bap-yonetmeligi", "Yükseköğretim Kurumları BAP Hakkında Yönetmelik", "YONETMELIK"},
                    {"meu-bap-yonergesi-2025", "23.01.2025 Tarihli Mersin Üniversitesi BAP Yönergesi", "YONERGE"},
                    {"meu-bap-uygulama-esaslari-2025", "18.02.2025 Tarihli MEÜ BAP Uygulama Usul ve Esasları", "ESASLAR"},
                    {"meu-acik-erisim-esaslari", "Açık Erişim (Open Access) Yayın Basım Ücreti Usul ve Esasları", "ESASLAR"},
                    {"meu-ihale-yonergesi", "MEÜ BAP Satın Alma ve İhale Yönergesi", "YONERGE"},
                    {"cb-butce-rehberi-2026", "2026-2028 Dönemi Bütçe Hazırlama Rehberi", "REHBER"},
                    {"h-cetveli-2026", "2026 Yılı H Cetveli (Yurtiçi Gündelik)", "CETVEL"},
                    {"yurtdisi-gundelik-2026", "2026 Yılı Yurtdışı Gündelik Kararı", "CETVEL"}
            };

            for (Object[] r : regs) {
                RegulationRecord rec = new RegulationRecord();
                rec.setSlug((String) r[0]);
                rec.setTitleTr((String) r[1]);
                rec.setDocType((String) r[2]);
                rec.setVersionLabel("2026");
                rec.setIsActive(true);
                regulationRepository.save(rec);
            }
            log.info("Regulation records seeded.");
        }
    }
}

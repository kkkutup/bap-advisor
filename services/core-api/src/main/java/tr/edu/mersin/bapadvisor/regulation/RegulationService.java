package tr.edu.mersin.bapadvisor.regulation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tr.edu.mersin.bapadvisor.client.AiEngineClient;
import tr.edu.mersin.bapadvisor.config.AppProperties;
import tr.edu.mersin.bapadvisor.exception.BadRequestException;
import tr.edu.mersin.bapadvisor.exception.ResourceNotFoundException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RegulationService {

    private static final Logger log = LoggerFactory.getLogger(RegulationService.class);

    private final RegulationRecordRepository regulationRepository;
    private final AiEngineClient aiEngineClient;
    private final Path regulationsDir;

    public RegulationService(RegulationRecordRepository regulationRepository,
                             AiEngineClient aiEngineClient,
                             AppProperties appProperties) {
        this.regulationRepository = regulationRepository;
        this.aiEngineClient = aiEngineClient;
        this.regulationsDir = Paths.get(appProperties.getStorage().getRegulationsDir());
        try {
            Files.createDirectories(this.regulationsDir);
        } catch (IOException e) {
            log.warn("Could not create regulations directory: {}", e.getMessage());
        }
    }

    public List<RegulationDocumentDto> listRegulations() {
        return regulationRepository.findAll().stream()
                .map(RegulationRecord::toDto)
                .toList();
    }

    @Transactional
    public RegulationDocumentDto uploadRegulation(
            MultipartFile file,
            String slug,
            String titleTr,
            String docType,
            String versionLabel
    ) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Mevzuat dosyası boş olamaz.");
        }

        String safeSlug = (slug != null && !slug.isBlank())
                ? slug.trim().toLowerCase().replaceAll("[^a-z0-9_-]", "-")
                : "reg-" + UUID.randomUUID().toString().substring(0, 8);

        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : safeSlug + ".pdf";
        String storageFileName = safeSlug + "_" + System.currentTimeMillis() + "_" + originalFilename;
        Path targetPath = regulationsDir.resolve(storageFileName);

        String sha256;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream is = file.getInputStream()) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            sha256 = HexFormat.of().formatHex(digest.digest());
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            throw new RuntimeException("Dosya kaydedilemedi: " + e.getMessage(), e);
        }

        RegulationRecord record = regulationRepository.findBySlug(safeSlug)
                .orElse(new RegulationRecord());

        record.setSlug(safeSlug);
        record.setTitleTr(titleTr != null ? titleTr : originalFilename);
        record.setDocType(docType != null ? docType : "YONERGE");
        record.setVersionLabel(versionLabel != null ? versionLabel : "Guncel");
        record.setStoragePath(targetPath.toString());
        record.setSha256Hash(sha256);
        record.setIsActive(true);
        record.setIngestedAt(Instant.now());

        RegulationRecord saved = regulationRepository.save(record);

        // Trigger AI engine ingest
        aiEngineClient.ingestRegulation(saved.getId(), targetPath.toString(), saved.getTitleTr(), saved.getDocType(), safeSlug);

        return saved.toDto();
    }

    @Transactional
    public RegulationDocumentDto toggleRegulation(UUID id, boolean isActive) {
        RegulationRecord record = regulationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Mevzuat kaydı bulunamadı: " + id));

        record.setIsActive(isActive);
        RegulationRecord updated = regulationRepository.save(record);
        return updated.toDto();
    }

    @Transactional
    public List<RegulationDocumentDto> syncSeedRegulations() {
        List<RegulationRecord> all = regulationRepository.findAll();
        List<RegulationDocumentDto> results = new ArrayList<>();

        Map<String, String> slugToFileMap = Map.of(
                "yok-bap-yonetmeligi", "Yuksekoretim_Kurumlar_Bilimsel_Aratrma_Projeleri_Hakknda_Yonetmelik.pdf",
                "meu-bap-yonergesi-2025", "23.01.2025_Tarihli_MEU_BAP_Yonergesi.pdf",
                "meu-bap-uygulama-esaslari-2025", "meu_uygulama_esaslari_seed.md",
                "meu-acik-erisim-esaslari", "Ack_Eriim_Open_Access_Yayn_Basm_Ucreti_Destei_Uygulama_Usul_ve_Esaslar.pdf",
                "meu-ihale-yonergesi", "Satn_Alma_ve_hale_Yonetmelii.pdf",
                "cb-butce-rehberi-2026", "20260131-1.pdf",
                "h-cetveli-2026", "8-H-Cetveli_2026Butcesi.pdf",
                "yurtdisi-gundelik-2026", "20260131-1.pdf"
        );

        for (RegulationRecord reg : all) {
            Path targetFile = null;
            if (reg.getStoragePath() != null) {
                Path p = Paths.get(reg.getStoragePath());
                if (Files.exists(p)) {
                    targetFile = p;
                }
            }

            if (targetFile == null && slugToFileMap.containsKey(reg.getSlug())) {
                String candidateName = slugToFileMap.get(reg.getSlug());
                Path p1 = regulationsDir.resolve(candidateName);
                Path p2 = Paths.get("../../data/regulations").resolve(candidateName);
                Path p3 = Paths.get("data/regulations").resolve(candidateName);
                if (Files.exists(p1)) targetFile = p1;
                else if (Files.exists(p2)) targetFile = p2;
                else if (Files.exists(p3)) targetFile = p3;
            }

            if (targetFile != null && Files.exists(targetFile)) {
                reg.setStoragePath(targetFile.toAbsolutePath().toString());
                boolean ingested = aiEngineClient.ingestRegulation(reg.getId(), targetFile.toAbsolutePath().toString(), reg.getTitleTr(), reg.getDocType(), reg.getSlug());
                if (ingested) {
                    reg.setIngestedAt(Instant.now());
                }
                regulationRepository.save(reg);
            }
            results.add(reg.toDto());
        }

        return results;
    }
}

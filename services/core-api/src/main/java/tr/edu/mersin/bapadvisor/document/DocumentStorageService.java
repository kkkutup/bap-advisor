package tr.edu.mersin.bapadvisor.document;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tr.edu.mersin.bapadvisor.config.AppProperties;
import tr.edu.mersin.bapadvisor.exception.ResourceNotFoundException;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class DocumentStorageService {

    private static final Logger log = LoggerFactory.getLogger(DocumentStorageService.class);

    private final Path uploadDir;

    public DocumentStorageService(AppProperties appProperties) {
        this.uploadDir = Paths.get(appProperties.getStorage().getUploadDir());
        try {
            Files.createDirectories(this.uploadDir);
        } catch (IOException e) {
            log.warn("Could not create upload directory: {}", e.getMessage());
        }
    }

    public record StoredFileInfo(
            String originalFileName,
            String mimeType,
            long sizeBytes,
            String storagePath,
            String sha256Hash
    ) {}

    public StoredFileInfo storeFile(MultipartFile file, UUID submissionId, int versionNo) {
        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "document.pdf";
        String extension = "";
        int dotIdx = originalFilename.lastIndexOf('.');
        if (dotIdx >= 0) {
            extension = originalFilename.substring(dotIdx);
        }

        String storedFileName = submissionId + "_v" + versionNo + "_" + UUID.randomUUID() + extension;
        Path targetPath = uploadDir.resolve(storedFileName);

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
            throw new RuntimeException("Dosya diske kaydedilemedi: " + e.getMessage(), e);
        }

        String mimeType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";

        return new StoredFileInfo(
                originalFilename,
                mimeType,
                file.getSize(),
                targetPath.toAbsolutePath().normalize().toString(),
                sha256
        );
    }

    public StoredFileInfo storeRawText(String rawText, UUID submissionId, int versionNo, String docName) {
        String storedFileName = submissionId + "_v" + versionNo + "_" + UUID.randomUUID() + ".txt";
        Path targetPath = uploadDir.resolve(storedFileName);
        byte[] bytes = (rawText != null ? rawText : "").getBytes();

        String sha256;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(bytes);
            sha256 = HexFormat.of().formatHex(digest.digest());
            Files.write(targetPath, bytes);
        } catch (Exception e) {
            throw new RuntimeException("Metin dosyası kaydedilemedi: " + e.getMessage(), e);
        }

        return new StoredFileInfo(
                docName != null ? docName : "ham_metin.txt",
                "text/plain",
                bytes.length,
                targetPath.toAbsolutePath().normalize().toString(),
                sha256
        );
    }

    public Resource loadFileAsResource(String storagePath) {
        try {
            Path filePath = Paths.get(storagePath);
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new ResourceNotFoundException("Dosya bulunamadı veya okunamıyor: " + storagePath);
            }
        } catch (MalformedURLException ex) {
            throw new ResourceNotFoundException("Dosya yolu geçersiz: " + storagePath);
        }
    }
}

package tr.edu.mersin.bapadvisor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Jwt jwt = new Jwt();
    private Security security = new Security();
    private Storage storage = new Storage();
    private AiEngine aiEngine = new AiEngine();

    public static class Jwt {
        private String secret = "super_secret_jwt_signing_key_at_least_256_bits_long_for_hmac_sha256_mersin_bap";
        private int expirationHours = 24;
        private int refreshExpirationDays = 7;

        public String getSecret() { return secret; }
        public void setSecret(String secret) { this.secret = secret; }
        public int getExpirationHours() { return expirationHours; }
        public void setExpirationHours(int expirationHours) { this.expirationHours = expirationHours; }
        public int getRefreshExpirationDays() { return refreshExpirationDays; }
        public void setRefreshExpirationDays(int refreshExpirationDays) { this.refreshExpirationDays = refreshExpirationDays; }
    }

    public static class Security {
        private String internalServiceToken = "bap_internal_service_secure_token_secret_key_meu_2026";
        private String bap2ApiKey = "bap2_integration_secret_api_key_meu_2026";

        public String getInternalServiceToken() { return internalServiceToken; }
        public void setInternalServiceToken(String internalServiceToken) { this.internalServiceToken = internalServiceToken; }
        public String getBap2ApiKey() { return bap2ApiKey; }
        public void setBap2ApiKey(String bap2ApiKey) { this.bap2ApiKey = bap2ApiKey; }
    }

    public static class Storage {
        private String uploadDir = "./data/uploads";
        private String regulationsDir = "./data/regulations";

        public String getUploadDir() { return uploadDir; }
        public void setUploadDir(String uploadDir) { this.uploadDir = uploadDir; }
        public String getRegulationsDir() { return regulationsDir; }
        public void setRegulationsDir(String regulationsDir) { this.regulationsDir = regulationsDir; }
    }

    public static class AiEngine {
        private String baseUrl = "http://localhost:8001";

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    }

    public Jwt getJwt() { return jwt; }
    public void setJwt(Jwt jwt) { this.jwt = jwt; }
    public Security getSecurity() { return security; }
    public void setSecurity(Security security) { this.security = security; }
    public Storage getStorage() { return storage; }
    public void setStorage(Storage storage) { this.storage = storage; }
    public AiEngine getAiEngine() { return aiEngine; }
    public void setAiEngine(AiEngine aiEngine) { this.aiEngine = aiEngine; }
}

package tr.edu.mersin.bapadvisor.regulation;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

public record RegulationDocumentDto(
        @JsonProperty("id") UUID id,
        @JsonProperty("slug") String slug,
        @JsonProperty("title_tr") String titleTr,
        @JsonProperty("doc_type") String docType,
        @JsonProperty("version_label") String versionLabel,
        @JsonProperty("source_url") String sourceUrl,
        @JsonProperty("is_active") Boolean isActive,
        @JsonProperty("ingested_at") Instant ingestedAt
) {}

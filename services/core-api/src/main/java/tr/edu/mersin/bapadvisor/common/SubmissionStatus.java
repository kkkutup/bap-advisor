package tr.edu.mersin.bapadvisor.common;

public enum SubmissionStatus {
    DRAFT,
    OCR_PROCESSING,
    OCR_FAILED,
    EXTRACTION_REVIEW,
    EVALUATING,
    OFFICER_REVIEW,
    REVISION_REQUESTED,
    COMMISSION_REFERRED,
    REJECTED_FORMAL
}

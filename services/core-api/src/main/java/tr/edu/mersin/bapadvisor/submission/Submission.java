package tr.edu.mersin.bapadvisor.submission;

import jakarta.persistence.*;
import tr.edu.mersin.bapadvisor.common.SubmissionStatus;
import tr.edu.mersin.bapadvisor.common.SubmissionType;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "submissions", schema = "core")
public class Submission {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "reference_no", nullable = false, unique = true)
    private String referenceNo;

    @Column(name = "external_bap2_id")
    private String externalBap2Id;

    @Enumerated(EnumType.STRING)
    @Column(name = "submission_type", nullable = false)
    private SubmissionType submissionType;

    @Column(name = "project_code")
    private String projectCode;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "applicant_name", nullable = false)
    private String applicantName;

    @Column(name = "applicant_title")
    private String applicantTitle;

    @Column(name = "department")
    private String department;

    @Column(name = "current_version_no", nullable = false)
    private int currentVersionNo = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SubmissionStatus status = SubmissionStatus.DRAFT;

    @Column(name = "assigned_officer_id")
    private UUID assignedOfficerId;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Submission() {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getReferenceNo() { return referenceNo; }
    public void setReferenceNo(String referenceNo) { this.referenceNo = referenceNo; }
    public String getExternalBap2Id() { return externalBap2Id; }
    public void setExternalBap2Id(String externalBap2Id) { this.externalBap2Id = externalBap2Id; }
    public SubmissionType getSubmissionType() { return submissionType; }
    public void setSubmissionType(SubmissionType submissionType) { this.submissionType = submissionType; }
    public String getProjectCode() { return projectCode; }
    public void setProjectCode(String projectCode) { this.projectCode = projectCode; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getApplicantName() { return applicantName; }
    public void setApplicantName(String applicantName) { this.applicantName = applicantName; }
    public String getApplicantTitle() { return applicantTitle; }
    public void setApplicantTitle(String applicantTitle) { this.applicantTitle = applicantTitle; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public int getCurrentVersionNo() { return currentVersionNo; }
    public void setCurrentVersionNo(int currentVersionNo) { this.currentVersionNo = currentVersionNo; }
    public SubmissionStatus getStatus() { return status; }
    public void setStatus(SubmissionStatus status) { this.status = status; }
    public UUID getAssignedOfficerId() { return assignedOfficerId; }
    public void setAssignedOfficerId(UUID assignedOfficerId) { this.assignedOfficerId = assignedOfficerId; }
    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

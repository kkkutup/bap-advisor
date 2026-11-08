package tr.edu.mersin.bapadvisor.audit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditLog logAction(UUID actorUserId, String actionType, String entityType, String entityId, String beforeJson, String afterJson) {
        AuditLog entry = new AuditLog(actorUserId, actionType, entityType, entityId, beforeJson, afterJson);
        return auditLogRepository.save(entry);
    }
}

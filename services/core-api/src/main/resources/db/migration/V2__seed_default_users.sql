-- Seed default users with BCrypt password hashes
-- admin@mersin.edu.tr / Admin123! (ADMIN)
-- koordinator@mersin.edu.tr / Koord123! (COORDINATOR)
-- memur@mersin.edu.tr / Memur123! (OFFICER)
-- kurul@mersin.edu.tr / Kurul123! (COMMISSION_VIEWER)

INSERT INTO core.users (id, email, password_hash, full_name, role, is_active, created_at)
VALUES
    ('11111111-1111-1111-1111-111111111111', 'admin@mersin.edu.tr', '$2a$10$uU92VcEPSEUriBQ4n9lqQutTaQNwlyd09C381ptp4xxeAoIhsxo6i', 'Sistem Yöneticisi', 'ADMIN', TRUE, CURRENT_TIMESTAMP),
    ('22222222-2222-2222-2222-222222222222', 'koordinator@mersin.edu.tr', '$2a$10$4nrx/H0ZR40OqUPt3b98zOpghxd4PSUYbPwnDiPSQgr7A7K26q9xO', 'Prof. Dr. BAP Koordinatörü', 'COORDINATOR', TRUE, CURRENT_TIMESTAMP),
    ('33333333-3333-3333-3333-333333333333', 'memur@mersin.edu.tr', '$2a$10$rK4hsyG8gYou9Mx.v5/eeefk9bczil7PEa466naADtEePgAqq/gS.', 'BAP Ön İnceleme Memuru', 'OFFICER', TRUE, CURRENT_TIMESTAMP),
    ('44444444-4444-4444-4444-444444444444', 'kurul@mersin.edu.tr', '$2a$10$szkE0b9cs3WC3S52BHFVPevCP5XVEY1i9hanSUOJ1FVDhWmsijTfO', 'BAP Komisyon Üyesi (Profesör Kurulu)', 'COMMISSION_VIEWER', TRUE, CURRENT_TIMESTAMP)
ON CONFLICT (email) DO NOTHING;

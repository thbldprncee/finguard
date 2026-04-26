package com.finguard.audit.controller;

import com.finguard.audit.dto.AuditEventResponse;
import com.finguard.audit.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
@Tag(name = "Audit", description = "Audit trail events")
@SecurityRequirement(name = "bearerAuth")
public class AuditController {

    private final AuditService auditService;

    @GetMapping("/events")
    @PreAuthorize("hasAnyRole('ANALYST', 'ADMIN')")
    @Operation(summary = "Query audit events")
    public Page<AuditEventResponse> getEvents(
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String aggregateId,
            @PageableDefault(size = 50, sort = "createdAt") Pageable pageable) {
        return auditService.getEvents(eventType, aggregateId, pageable);
    }
}

package com.helpdesk.web;

import com.helpdesk.domain.UserAccount;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/organizations")
public class OrganizationApiController {
    public record OrganizationResponse(Long id, String name, String slug) {}

    @GetMapping("/me")
    public OrganizationResponse currentOrganization(@AuthenticationPrincipal UserAccount actor) {
        return new OrganizationResponse(actor.getOrganization().getId(), actor.getOrganization().getName(),
                actor.getOrganization().getSlug());
    }
}
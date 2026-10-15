package com.helpdesk.web;

import com.helpdesk.api.CreateCommentRequest;
import com.helpdesk.api.CreateTicketRequest;
import com.helpdesk.api.TicketResponse;
import com.helpdesk.api.UpdateTicketRequest;
import com.helpdesk.domain.TicketPriority;
import com.helpdesk.domain.TicketStatus;
import com.helpdesk.domain.UserAccount;
import com.helpdesk.domain.UserRole;
import com.helpdesk.service.OrganizationUserService;
import com.helpdesk.service.TicketService;
import java.util.Arrays;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class HelpdeskPageController {
    private final TicketService ticketService;
    private final OrganizationUserService userService;

    public HelpdeskPageController(TicketService ticketService, OrganizationUserService userService) {
        this.ticketService = ticketService;
        this.userService = userService;
    }

    @GetMapping("/")
    public String home() {
        return "redirect:/app/tickets";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/app/tickets")
    public String tickets(@AuthenticationPrincipal UserAccount actor,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) TicketStatus status,
            @RequestParam(required = false) TicketPriority priority,
            @RequestParam(required = false) Long assignedAgentId,
            @RequestParam(defaultValue = "0") int page,
            Model model) {
        int currentPage = Math.max(0, page);
        Page<TicketResponse> results = ticketService.list(actor, status, priority, assignedAgentId,
                q == null || q.isBlank() ? null : q.trim(),
                PageRequest.of(currentPage, 20, Sort.by(Sort.Direction.DESC, "createdAt")));
        addSharedModel(actor, model);
        model.addAttribute("tickets", results);
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("selectedPriority", priority);
        model.addAttribute("selectedAssignee", assignedAgentId);
        return "tickets";
    }

    @GetMapping("/app/tickets/{ticketId}")
    public String ticket(@AuthenticationPrincipal UserAccount actor, @PathVariable Long ticketId, Model model) {
        addSharedModel(actor, model);
        model.addAttribute("ticket", ticketService.get(actor, ticketId));
        return "ticket-detail";
    }

    @PostMapping("/app/tickets")
    public String createTicket(@AuthenticationPrincipal UserAccount actor,
            @Valid @ModelAttribute CreateTicketRequest request,
            RedirectAttributes redirectAttributes) {
        TicketResponse created = ticketService.create(actor, request);
        redirectAttributes.addFlashAttribute("notice", "Ticket created");
        return "redirect:/app/tickets/" + created.id();
    }

    @PostMapping("/app/tickets/{ticketId}/comments")
    public String comment(@AuthenticationPrincipal UserAccount actor, @PathVariable Long ticketId,
            @Valid @ModelAttribute CreateCommentRequest request,
            RedirectAttributes redirectAttributes) {
        ticketService.addComment(actor, ticketId, request);
        redirectAttributes.addFlashAttribute("notice", "Reply added");
        return "redirect:/app/tickets/" + ticketId;
    }

    @PostMapping("/app/tickets/{ticketId}/update")
    public String update(@AuthenticationPrincipal UserAccount actor, @PathVariable Long ticketId,
            @ModelAttribute UpdateTicketRequest request,
            RedirectAttributes redirectAttributes) {
        ticketService.update(actor, ticketId, request);
        redirectAttributes.addFlashAttribute("notice", "Ticket updated");
        return "redirect:/app/tickets/" + ticketId;
    }

    private void addSharedModel(UserAccount actor, Model model) {
        model.addAttribute("currentUser", actor);
        model.addAttribute("isStaff", actor.getRole() != UserRole.CUSTOMER);
        model.addAttribute("statuses", Arrays.asList(TicketStatus.values()));
        model.addAttribute("priorities", Arrays.asList(TicketPriority.values()));
        model.addAttribute("agents", userService.listUsers(actor).stream()
                .filter(user -> user.getRole() != UserRole.CUSTOMER).toList());
    }
}
package com.helpdesk.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helpdesk.domain.UserAccount;
import com.helpdesk.repository.UserAccountRepository;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "spring.datasource.url=jdbc:h2:mem:helpdesk-api-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class HelpdeskApiIntegrationTest {
    private static final String PASSWORD = "Welcome1234!";
    private static final String ADMIN = "admin@acme.test";
    private static final String AGENT = "agent@acme.test";
    private static final String CUSTOMER = "customer@acme.test";
    private static final String NORTHSTAR_CUSTOMER = "customer@northstar.test";
    private static final AtomicLong SUBJECT_SEQUENCE = new AtomicLong();

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserAccountRepository userRepository;

    @Test
    void organizationEndpointReturnsAuthenticatedUsersTenant() throws Exception {
        mvc.perform(get("/api/organizations/me").with(httpBasic(CUSTOMER, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Acme Support"))
                .andExpect(jsonPath("$.slug").value("acme"));
    }

    @Test
    void loginAndTicketPagesRenderForExpectedUsers() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Sign in")));
        mvc.perform(get("/app/tickets").with(httpBasic(CUSTOMER, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("My tickets")));
    }

    @Test
    void userApiListsOnlyTenantUsersAndEnforcesStaffRoles() throws Exception {
        mvc.perform(get("/api/users").with(httpBasic(AGENT, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("admin@acme.test")))
                .andExpect(content().string(not(containsString("northstar.test"))));
        mvc.perform(get("/api/users").with(httpBasic(CUSTOMER, PASSWORD)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanCreateUserButAgentCannotAndDuplicateEmailConflicts() throws Exception {
        String email = "new-user-" + SUBJECT_SEQUENCE.incrementAndGet() + "@acme.test";
        String request = """
                {"displayName":"New Customer","email":"%s","password":"LongPassword123!","role":"CUSTOMER"}
                """.formatted(email);

        mvc.perform(post("/api/users").with(httpBasic(ADMIN, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
        mvc.perform(post("/api/users").with(httpBasic(ADMIN, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/users").with(httpBasic(AGENT, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isForbidden());
        UserAccount saved = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(saved.getPasswordHash()).isNotEqualTo("LongPassword123!");
    }

    @Test
    void ticketApiCreatesSearchesUpdatesAndEnforcesTenantVisibility() throws Exception {
        String subject = "Tenant-check-" + SUBJECT_SEQUENCE.incrementAndGet();
        long ticketId = createTicket(CUSTOMER, subject);

        mvc.perform(get("/api/tickets").queryParam("q", subject).queryParam("status", "OPEN")
                        .queryParam("priority", "HIGH").queryParam("page", "0").queryParam("size", "20")
                        .with(httpBasic(CUSTOMER, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].subject").value(subject))
                .andExpect(jsonPath("$.content[0].status").value("OPEN"));
        mvc.perform(get("/api/tickets").queryParam("q", subject)
                        .with(httpBasic(NORTHSTAR_CUSTOMER, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        long agentId = userRepository.findByEmailIgnoreCase(AGENT).orElseThrow().getId();
        mvc.perform(patch("/api/tickets/{id}", ticketId).with(httpBasic(AGENT, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\",\"priority\":\"URGENT\","
                                + "\"assignedAgentId\":%d}".formatted(agentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.priority").value("URGENT"))
                .andExpect(jsonPath("$.assignedAgentId").value(agentId));

        mvc.perform(get("/api/tickets").queryParam("q", subject).queryParam("status", "IN_PROGRESS")
                        .queryParam("priority", "URGENT").queryParam("assignedAgentId", Long.toString(agentId))
                        .with(httpBasic(AGENT, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        long otherAgentId = userRepository.findByEmailIgnoreCase("agent@northstar.test").orElseThrow().getId();
        mvc.perform(patch("/api/tickets/{id}", ticketId).with(httpBasic(AGENT, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assignedAgentId\":%d}".formatted(otherAgentId)))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/tickets/{id}", ticketId).with(httpBasic(CUSTOMER, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.history.length()").value(3));
        mvc.perform(get("/api/tickets/{id}", ticketId).with(httpBasic(NORTHSTAR_CUSTOMER, PASSWORD)))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/tickets/{id}", ticketId).with(httpBasic(CUSTOMER, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CLOSED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void ticketListAcceptsMissingAndBlankSearchQueries() throws Exception {
        mvc.perform(get("/api/tickets").with(httpBasic(AGENT, PASSWORD)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/tickets").queryParam("q", "   ").with(httpBasic(AGENT, PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    void commentApiDefaultsMissingInternalFlagAndHidesStaffNotesFromCustomers() throws Exception {
        long ticketId = createTicket(CUSTOMER, "Comments-" + SUBJECT_SEQUENCE.incrementAndGet());
        mvc.perform(post("/api/tickets/{id}/comments", ticketId).with(httpBasic(CUSTOMER, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Customer reply\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comments[0].internal").value(false));

        String secret = "staff-only-" + SUBJECT_SEQUENCE.incrementAndGet();
        mvc.perform(post("/api/tickets/{id}/comments", ticketId).with(httpBasic(AGENT, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"%s\",\"internal\":true}".formatted(secret)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(secret)));
        mvc.perform(get("/api/tickets/{id}", ticketId).with(httpBasic(CUSTOMER, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(secret))));
        mvc.perform(post("/api/tickets/{id}/comments", ticketId).with(httpBasic(CUSTOMER, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"forbidden note\",\"internal\":true}"))
                .andExpect(status().isForbidden());
    }

        @Test
        void browserCommentFormTreatsUncheckedInternalFlagAsPublic() throws Exception {
                long ticketId = createTicket(CUSTOMER, "Form-comment-" + SUBJECT_SEQUENCE.incrementAndGet());

                mvc.perform(post("/app/tickets/{id}/comments", ticketId)
                                                .with(httpBasic(CUSTOMER, PASSWORD))
                                                .with(csrf())
                                                .param("body", "Reply submitted from the browser form"))
                                .andExpect(status().is3xxRedirection())
                                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                                                .redirectedUrl("/app/tickets/" + ticketId));
        }

    @Test
    void invalidTicketPayloadReturnsStructuredValidationError() throws Exception {
        mvc.perform(post("/api/tickets").with(httpBasic(CUSTOMER, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"\",\"description\":\"\",\"priority\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.length()").value(3));
    }

        @Test
        void invalidUserPayloadReturnsValidationError() throws Exception {
                mvc.perform(post("/api/users").with(httpBasic(ADMIN, PASSWORD))
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content("{\"displayName\":\"\",\"email\":\"bad-email\","
                                                        + "\"password\":\"short\",\"role\":null}"))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.title").value("Validation failed"));
        }

    private long createTicket(String username, String subject) throws Exception {
        String request = """
                {"subject":"%s","description":"Integration test ticket description","priority":"HIGH"}
                """.formatted(subject);
        MvcResult result = mvc.perform(post("/api/tickets").with(httpBasic(username, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("id").asLong();
    }
}
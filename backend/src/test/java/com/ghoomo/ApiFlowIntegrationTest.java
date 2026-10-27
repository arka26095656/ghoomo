package com.ghoomo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full HTTP flow against H2 with the offline mock LLM. External travel APIs point at a closed port,
 * so the planner falls back to the built-in gazetteer: no network needed.
 */
@SpringBootTest(properties = {
        "app.ai.gemini.api-key=",
        "app.ai.groq.api-key=",
        "app.travel.nominatim-url=http://127.0.0.1:9",
        "app.travel.overpass-url=http://127.0.0.1:9",
        "app.travel.open-meteo-url=http://127.0.0.1:9",
        "app.travel.frankfurter-url=http://127.0.0.1:9",
        "app.rate-limit.chat-per-minute=100"
})
@AutoConfigureMockMvc
class ApiFlowIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void registerGenerateEditAndList() throws Exception {
        MvcResult reg = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Asha\",\"email\":\"asha@example.com\",\"password\":\"supersecret\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String token = json.readTree(reg.getResponse().getContentAsString()).path("token").asText();
        String auth = "Bearer " + token;

        mvc.perform(get("/api/itineraries")).andExpect(status().isUnauthorized());

        MvcResult gen = mvc.perform(post("/api/itineraries/generate").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"Plan 3 days in Jaipur under 15k\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.destination").value("Jaipur"))
                .andExpect(jsonPath("$.days").value(3))
                .andExpect(jsonPath("$.plan.days.length()").value(3))
                .andReturn();
        JsonNode detail = json.readTree(gen.getResponse().getContentAsString());
        long id = detail.path("id").asLong();

        // Edit: rename and drop day 3
        var plan = (com.fasterxml.jackson.databind.node.ObjectNode) detail.path("plan");
        ((com.fasterxml.jackson.databind.node.ArrayNode) plan.path("days")).remove(2);
        String body = json.writeValueAsString(java.util.Map.of("title", "Pink City weekend", "plan", plan));
        mvc.perform(put("/api/itineraries/" + id).header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Pink City weekend"))
                .andExpect(jsonPath("$.days").value(2));

        mvc.perform(get("/api/itineraries").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void rejectsBadRegistration() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"email\":\"nope\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.email").exists());
    }
}

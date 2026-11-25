package com.modvault;

import com.modvault.dal.entity.ApiKey;
import com.modvault.dal.entity.ModelMapping;
import com.modvault.dal.entity.Upstream;
import com.modvault.dal.entity.UpstreamKey;
import com.modvault.dal.entity.UsageRecord;
import com.modvault.dal.mapper.ApiKeyMapper;
import com.modvault.dal.mapper.ModelMappingMapper;
import com.modvault.dal.mapper.UpstreamKeyMapper;
import com.modvault.dal.mapper.UpstreamMapper;
import com.modvault.dal.mapper.UsageRecordMapper;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real PostgreSQL (zonky embedded) + Flyway integration test. This is the test that was
 * missing when the UUID/String mismatch (D1) escaped unit tests. It exercises all five
 * tables' mapper SQL and the admin "create upstream -> set key -> create mapping ->
 * create api key" flow against the real schema.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PostgresIntegrationTest {

    private static final String MASTER_KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private static final String ADMIN_TOKEN = "0123456789012345678901234567890123456789";

    private static final EmbeddedPostgres PG = startPostgres();

    private static EmbeddedPostgres startPostgres() {
        try {
            return EmbeddedPostgres.start();
        } catch (IOException e) {
            throw new RuntimeException("failed to start embedded PostgreSQL", e);
        }
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> "jdbc:postgresql://localhost:" + PG.getPort() + "/postgres");
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
        registry.add("modvault.master-key", () -> MASTER_KEY);
        registry.add("modvault.admin-token", () -> ADMIN_TOKEN);
    }

    @Autowired UpstreamMapper upstreamMapper;
    @Autowired UpstreamKeyMapper upstreamKeyMapper;
    @Autowired ModelMappingMapper modelMappingMapper;
    @Autowired ApiKeyMapper apiKeyMapper;
    @Autowired UsageRecordMapper usageRecordMapper;
    @Autowired MockMvc mvc;

    @Test
    void mapperCrudAcrossAllFiveTables() {
        // upstream: insert -> findById -> update -> delete
        Upstream upstream = new Upstream();
        upstream.setId(UUID.randomUUID());
        upstream.setName("it-upstream");
        upstream.setBaseUrl("https://api.example.com/v1");
        upstream.setEnabled(true);
        upstream.setConnectTimeoutMs(10000);
        upstream.setReadTimeoutMs(120000);
        upstream.setExtraHeaders("{\"X-Tenant\":\"a\"}");
        upstream.setMaxTokensField("max_tokens");
        assertEquals(1, upstreamMapper.insert(upstream));

        Upstream fetched = upstreamMapper.findById(upstream.getId());
        assertNotNull(fetched);
        assertEquals("it-upstream", fetched.getName());
        assertEquals("https://api.example.com/v1", fetched.getBaseUrl());
        assertTrue(fetched.getExtraHeaders().contains("a"), "jsonb extra_headers should round-trip");

        fetched.setEnabled(false);
        assertEquals(1, upstreamMapper.update(fetched));
        assertFalse(upstreamMapper.findById(upstream.getId()).isEnabled());

        // upstream_key: insert -> findById -> findEnabledByUpstreamId -> updateStatus
        UpstreamKey key = new UpstreamKey();
        key.setId(UUID.randomUUID());
        key.setUpstreamId(upstream.getId());
        key.setKeyCiphertext("v1:AAAA");
        key.setKeyMasked("sk-...1234");
        key.setEnabled(true);
        key.setStatus("active");
        assertEquals(1, upstreamKeyMapper.insert(key));
        assertEquals(key.getId(), upstreamKeyMapper.findById(key.getId()).getId());
        assertNotNull(upstreamKeyMapper.findEnabledByUpstreamId(upstream.getId()));
        assertEquals(1, upstreamKeyMapper.updateStatus(key.getId(), false, "unavailable"));

        // model_mapping: insert -> findById -> findEnabledByAnthropicModel -> update -> delete
        ModelMapping mapping = new ModelMapping();
        mapping.setId(UUID.randomUUID());
        mapping.setAnthropicModel("claude-it");
        mapping.setUpstreamId(upstream.getId());
        mapping.setUpstreamModel("gpt-4o");
        mapping.setEnabled(true);
        assertEquals(1, modelMappingMapper.insert(mapping));
        assertEquals("gpt-4o", modelMappingMapper.findById(mapping.getId()).getUpstreamModel());
        assertEquals("gpt-4o", modelMappingMapper.findEnabledByAnthropicModel("claude-it").getUpstreamModel());
        mapping.setEnabled(false);
        assertEquals(1, modelMappingMapper.update(mapping));
        assertNull(modelMappingMapper.findEnabledByAnthropicModel("claude-it"));
        assertEquals(1, modelMappingMapper.delete(mapping.getId()));

        // api_key: insert -> findByHash -> findById -> revoke
        ApiKey apiKey = new ApiKey();
        apiKey.setId(UUID.randomUUID());
        apiKey.setName("it-key");
        apiKey.setKeyHash(com.modvault.service.ApiKeyService.sha256Hex("sk-mv-test"));
        apiKey.setKeyPrefix("sk-mv-te");
        apiKey.setEnabled(true);
        assertEquals(1, apiKeyMapper.insert(apiKey));
        assertEquals(apiKey.getId(), apiKeyMapper.findByHash(com.modvault.service.ApiKeyService.sha256Hex("sk-mv-test")).getId());
        assertEquals(1, apiKeyMapper.revoke(apiKey.getId()));
        assertFalse(apiKeyMapper.findById(apiKey.getId()).isEnabled());

        // usage_record: insert -> findPage
        UsageRecord record = new UsageRecord();
        record.setId(UUID.randomUUID());
        record.setRequestId("req_it");
        record.setApiKeyId(apiKey.getId());
        record.setAnthropicModel("claude-it");
        record.setUpstreamId(upstream.getId());
        record.setUpstreamModel("gpt-4o");
        record.setStatus("success");
        record.setInputTokens(10);
        record.setOutputTokens(5);
        record.setStream(false);
        record.setLatencyMs(42L);
        assertEquals(1, usageRecordMapper.insert(record));
        assertTrue(usageRecordMapper.findPage(10, 0).size() >= 1);

        assertEquals(1, upstreamMapper.delete(upstream.getId()));
    }

    @Test
    void adminFlowCreatesUpstreamKeyMappingAndApiKey() throws Exception {
        // D2: create upstream with only name + base_url (timeouts defaulted)
        String upstreamJson = mvc.perform(post("/admin/api/v1/upstreams")
                        .header("Authorization", "Bearer " + ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"flow-upstream\",\"base_url\":\"https://api.openai.com/v1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("flow-upstream"))
                .andReturn().getResponse().getContentAsString();
        String upstreamId = com.modvault.translate.Json.mapper().readTree(upstreamJson).get("id").asText();

        // set upstream key -> masked, never plaintext
        mvc.perform(put("/admin/api/v1/upstreams/{id}/key", upstreamId)
                        .header("Authorization", "Bearer " + ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"sk-upstream-secret-value\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key_masked").value("sk-...alue"));

        // model mapping -> references the upstream
        mvc.perform(post("/admin/api/v1/model-mappings")
                        .header("Authorization", "Bearer " + ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"anthropic_model\":\"claude-flow\",\"upstream_id\":\"" + upstreamId
                                + "\",\"upstream_model\":\"gpt-4o\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.anthropic_model").value("claude-flow"))
                .andExpect(jsonPath("$.upstream_id").value(upstreamId));

        // api key -> plaintext returned once, then only prefix/mask
        String apiKeyJson = mvc.perform(post("/admin/api/v1/api-keys")
                        .header("Authorization", "Bearer " + ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"team-a\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key").value(org.hamcrest.Matchers.startsWith("sk-mv-")))
                .andReturn().getResponse().getContentAsString();
        String plaintextKey = com.modvault.translate.Json.mapper().readTree(apiKeyJson).get("key").asText();

        // list api keys -> never contains the plaintext
        String listJson = mvc.perform(get("/admin/api/v1/api-keys")
                        .header("Authorization", "Bearer " + ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertFalse(listJson.contains(plaintextKey), "plaintext gateway key leaked in list");

        // usage list is reachable
        mvc.perform(get("/admin/api/v1/usage")
                        .header("Authorization", "Bearer " + ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());

        // upstream list exposes masked key only
        String upstreamList = mvc.perform(get("/admin/api/v1/upstreams")
                        .header("Authorization", "Bearer " + ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertFalse(upstreamList.contains("sk-upstream-secret-value"), "plaintext upstream key leaked in list");
        assertTrue(upstreamList.contains("sk-...alue"), "masked upstream key missing");
    }
}

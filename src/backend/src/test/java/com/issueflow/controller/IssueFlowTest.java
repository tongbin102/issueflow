package com.issueflow.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 问题状态流转端到端测试：
 * 登录(admin) → 创建问题 → 非法流转(1002) → 完整流转(OPEN→IN_PROGRESS→PENDING_VERIFY→VERIFIED→CLOSED)
 * → 校验每步 status 正确、关闭写入 closedAt → 校验历史已写入。
 *
 * <p>依赖：运行前需 MySQL + Redis 可用（推荐先 {@code docker compose up -d}）。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class IssueFlowTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 登录并提取 token */
    private String login() throws Exception {
        String body = "{\"username\":\"admin\",\"password\":\"admin123\"}";
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.at("/data/token").asText();
    }

    /** 从响应体 JSON 路径提取文本 */
    private String extract(MvcResult result, String jsonPointer) throws Exception {
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.at(jsonPointer).asText();
    }

    @Test
    void fullStatusFlowAndHistory() throws Exception {
        String token = login();
        String auth = "Bearer " + token;

        // 1) 创建问题（multipart，issue 部分为 JSON）
        String issueJson = "{\"title\":\"冒烟测试问题\",\"severity\":2,\"description\":\"描述\",\"tags\":\"smoke,qa\"}";
        MockMultipartFile issuePart = new MockMultipartFile(
                "issue", "issue", MediaType.APPLICATION_JSON_VALUE, issueJson.getBytes(StandardCharsets.UTF_8));

        MvcResult createResult = mockMvc.perform(multipart("/api/issues")
                        .file(issuePart)
                        .header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.status").value(0)) // OPEN
                .andReturn();
        Long issueId = Long.valueOf(extract(createResult, "/data/id"));

        // 2) 非法流转：OPEN(0) 直接跳到 VERIFIED(3) → 1002 STATUS_TRANSITION_DENIED
        mockMvc.perform(post("/api/issues/" + issueId + "/status")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toStatus\":3,\"remark\":\"非法\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1002));

        // 3) 完整合法流转（admin 角色可执行全部转移）
        changeStatus(auth, issueId, 1, 0); // OPEN -> IN_PROGRESS
        changeStatus(auth, issueId, 2, 1); // IN_PROGRESS -> PENDING_VERIFY
        changeStatus(auth, issueId, 3, 2); // PENDING_VERIFY -> VERIFIED
        // VERIFIED -> CLOSED（需 remark）
        mockMvc.perform(post("/api/issues/" + issueId + "/status")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toStatus\":4,\"remark\":\"验证通过，关闭\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value(4)) // CLOSED
                .andExpect(jsonPath("$.data.closedAt").isNotEmpty());

        // 4) 历史已写入（至少包含 CREATE + 4 次流转）
        mockMvc.perform(get("/api/issues/" + issueId + "/history")
                        .header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list").isArray())
                .andExpect(jsonPath("$.data.list.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(4)));
    }

    private void changeStatus(String auth, Long issueId, int toStatus, int expectFromStatus) throws Exception {
        mockMvc.perform(post("/api/issues/" + issueId + "/status")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toStatus\":" + toStatus + ",\"remark\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value(toStatus));
    }
}

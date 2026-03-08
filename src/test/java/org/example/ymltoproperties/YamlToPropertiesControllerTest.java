package org.example.ymltoproperties;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(YamlToPropertiesController.class)
class YamlToPropertiesControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void convertsYamlPlainTextToPropertiesPlainText() throws Exception {
        String yaml = """
                server:
                  port: 8081
                spring:
                  application:
                    name: demo-app
                users:
                  - name: alice
                  - name: bob
                """;

        mockMvc.perform(post("/api/yaml/to-properties")
                        .with(csrf())
                        .header("Origin", "http://localhost")
                        .contentType(MediaType.TEXT_PLAIN)
                        .accept(MediaType.TEXT_PLAIN)
                        .content(yaml))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string("""
                        server.port=8081
                        spring.application.name=demo-app
                        users[0].name=alice
                        users[1].name=bob"""));
    }

    @Test
    void keepsYamlKeyOrderInOutputProperties() throws Exception {
        String yaml = """
                z-key: last
                a-key: first
                middle:
                  b: two
                  a: one
                """;

        mockMvc.perform(post("/api/yaml/to-properties")
                        .with(csrf())
                        .header("Origin", "http://localhost")
                        .contentType(MediaType.TEXT_PLAIN)
                        .accept(MediaType.TEXT_PLAIN)
                        .content(yaml))
                .andExpect(status().isOk())
                .andExpect(content().string("""
                        z-key=last
                        a-key=first
                        middle.b=two
                        middle.a=one"""));
    }

    @Test
    void convertsScalarListToIndexedProperties() throws Exception {
        String yaml = """
                app:
                  ports:
                    - 8080
                    - 8081
                  profiles:
                    - dev
                    - qa
                """;

        mockMvc.perform(post("/api/yaml/to-properties")
                        .with(csrf())
                        .header("Origin", "http://localhost")
                        .contentType(MediaType.TEXT_PLAIN)
                        .accept(MediaType.TEXT_PLAIN)
                        .content(yaml))
                .andExpect(status().isOk())
                .andExpect(content().string("""
                        app.ports[0]=8080
                        app.ports[1]=8081
                        app.profiles[0]=dev
                        app.profiles[1]=qa"""));
    }
}

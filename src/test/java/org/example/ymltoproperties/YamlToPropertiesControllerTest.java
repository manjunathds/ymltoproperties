package org.example.ymltoproperties;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.crypto.request-static-key=change-this-static-key",
        "app.security.require-https=false"
})
class YamlToPropertiesControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AesCryptoService aesCryptoService;

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

        String decrypted = performEncryptedConvert(yaml);

        assertThat(decrypted).isEqualTo("""
                server.port=8081
                spring.application.name=demo-app
                users[0].name=alice
                users[1].name=bob""");
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

        String decrypted = performEncryptedConvert(yaml);

        assertThat(decrypted).isEqualTo("""
                z-key=last
                a-key=first
                middle.b=two
                middle.a=one""");
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

        String decrypted = performEncryptedConvert(yaml);

        assertThat(decrypted).isEqualTo("""
                app.ports[0]=8080
                app.ports[1]=8081
                app.profiles[0]=dev
                app.profiles[1]=qa""");
    }

    private String performEncryptedConvert(String yaml) throws Exception {
        String encryptedRequest = aesCryptoService.encryptWithStaticKey(yaml);

        MvcResult result = mockMvc.perform(post("/api/yaml/to-properties")
                        .with(csrf())
                        .header("Origin", "http://localhost")
                        .contentType(MediaType.TEXT_PLAIN)
                        .accept(MediaType.TEXT_PLAIN)
                        .content(encryptedRequest))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(header().exists("X-Response-Key"))
                .andExpect(header().exists("X-Response-Salt"))
                .andReturn();

        String encryptedResponse = result.getResponse().getContentAsString();
        String responseKey = result.getResponse().getHeader("X-Response-Key");
        String responseSalt = result.getResponse().getHeader("X-Response-Salt");

        return aesCryptoService.decryptWithDynamicKey(encryptedResponse, responseKey, responseSalt);
    }
}

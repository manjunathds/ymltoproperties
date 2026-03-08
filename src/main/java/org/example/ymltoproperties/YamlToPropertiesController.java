package org.example.ymltoproperties;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/yaml")
public class YamlToPropertiesController {

    private final Yaml yaml = new Yaml();
    private final AesCryptoService aesCryptoService;

    public YamlToPropertiesController(AesCryptoService aesCryptoService) {
        this.aesCryptoService = aesCryptoService;
    }

    @PostMapping(path = "/to-properties", consumes = MediaType.TEXT_PLAIN_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> convertYamlToProperties(@RequestBody String encryptedYamlContent) {
        try {
            String yamlContent = aesCryptoService.decryptWithStaticKey(encryptedYamlContent);
            Object loaded = yaml.load(yamlContent);

            String propertiesText = "";
            if (loaded != null) {
                Map<String, String> flattened = new LinkedHashMap<>();
                flatten("", loaded, flattened);
                propertiesText = render(flattened);
            }

            AesCryptoService.DynamicEncryptedPayload payload = aesCryptoService.encryptWithDynamicKey(propertiesText);

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Response-Key", payload.responseKey());
            headers.set("X-Response-Salt", payload.responseSalt());

            return ResponseEntity.ok()
                    .headers(headers)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(payload.encryptedBody());
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid encrypted payload or YAML input", ex);
        }
    }

    private void flatten(String prefix, Object value, Map<String, String> output) {
        if (value instanceof Map<?, ?> mapValue) {
            for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
                String key = String.valueOf(entry.getKey());
                String nextPrefix = prefix.isEmpty() ? key : prefix + "." + key;
                flatten(nextPrefix, entry.getValue(), output);
            }
            return;
        }

        if (value instanceof List<?> listValue) {
            for (int i = 0; i < listValue.size(); i++) {
                flatten(prefix + "[" + i + "]", listValue.get(i), output);
            }
            return;
        }

        output.put(prefix, value == null ? "" : String.valueOf(value));
    }

    private String render(Map<String, String> properties) {
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            lines.add(entry.getKey() + "=" + entry.getValue());
        }
        return String.join("\n", lines);
    }
}

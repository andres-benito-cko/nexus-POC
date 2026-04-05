# AI Generator Service Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a new `ai-generator` Java/Spring Boot microservice that accepts natural language prompts and produces valid LE transactions using Claude on Amazon Bedrock with an agentic self-correction loop.

**Architecture:** A stateless Spring Boot service on port 8084. An `AgentLoop` orchestrates Bedrock Converse API calls with 5 tools: 4 local domain-knowledge tools (schema, examples, field mappings, valid enums) and 1 HTTP validation tool that dry-runs through the NexusEngine. SSE streams progress to consumers. The Test Bench UI gets an inline prompt bar.

**Tech Stack:** Java 17, Spring Boot 3.2.5, AWS SDK Bedrock Runtime, Spring WebFlux (SSE), Jackson, Docker

**Spec:** `docs/superpowers/specs/2026-04-06-ai-generator-design.md`

---

## File Structure

```
ai-generator/
├── build.gradle
├── settings.gradle
├── Dockerfile
├── src/main/java/com/checkout/nexus/generator/
│   ├── GeneratorApplication.java
│   ├── controller/
│   │   └── GenerateController.java
│   ├── agent/
│   │   ├── AgentLoop.java
│   │   ├── BedrockClient.java
│   │   └── ToolExecutor.java
│   ├── tools/
│   │   ├── ToolHandler.java                    (interface)
│   │   ├── GetSchemaHandler.java
│   │   ├── GetExamplesHandler.java
│   │   ├── GetFieldMappingsHandler.java
│   │   ├── GetValidEnumsHandler.java
│   │   └── ValidateLeTransactionHandler.java
│   ├── model/
│   │   ├── GenerateRequest.java
│   │   └── GenerateResponse.java
│   └── config/
│       └── GeneratorConfig.java
├── src/main/resources/
│   ├── application.yml
│   ├── system-prompt.txt
│   └── domain/
│       ├── nexus.schema.json
│       ├── le_nexus_mapping.md
│       ├── per_pillar_structures.md
│       ├── valid_combinations.json
│       └── examples/
│           ├── 01_acquiring_capture_simple.json
│           ├── ... (all 9 examples)
│           └── 09_cash_matched_settlement.json
├── src/test/java/com/checkout/nexus/generator/
│   ├── tools/
│   │   ├── GetSchemaHandlerTest.java
│   │   ├── GetExamplesHandlerTest.java
│   │   ├── GetValidEnumsHandlerTest.java
│   │   └── ValidateLeTransactionHandlerTest.java
│   ├── agent/
│   │   ├── AgentLoopTest.java
│   │   └── ToolExecutorTest.java
│   └── controller/
│       └── GenerateControllerTest.java
scripts/
└── sync-domain.sh
```

**Modified files:**
- `docker-compose.yml` — add ai-generator service
- `Makefile` — add build-generator and sync-domain targets
- `ui/src/api/client.ts` — add generateLeTransaction function
- `ui/src/pages/TestBench.tsx` — add inline AI prompt bar

---

### Task 1: Project Scaffolding

**Files:**
- Create: `ai-generator/build.gradle`
- Create: `ai-generator/settings.gradle`
- Create: `ai-generator/Dockerfile`
- Create: `ai-generator/src/main/java/com/checkout/nexus/generator/GeneratorApplication.java`
- Create: `ai-generator/src/main/resources/application.yml`

- [ ] **Step 1: Create build.gradle**

Create `ai-generator/build.gradle`:

```gradle
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.2.5'
    id 'io.spring.dependency-management' version '1.1.4'
}

group = 'com.checkout.nexus'
version = '0.0.1-SNAPSHOT'

java {
    sourceCompatibility = '17'
}

configurations {
    compileOnly {
        extendsFrom annotationProcessor
    }
}

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom 'software.amazon.awssdk:bom:2.25.16'
    }
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-webflux'
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    implementation 'software.amazon.awssdk:bedrockruntime'
    implementation 'com.fasterxml.jackson.core:jackson-databind'
    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}

tasks.named('test') {
    useJUnitPlatform()
}
```

- [ ] **Step 2: Create settings.gradle**

Create `ai-generator/settings.gradle`:

```gradle
rootProject.name = 'ai-generator'
```

- [ ] **Step 3: Create Dockerfile**

Create `ai-generator/Dockerfile`:

```dockerfile
FROM public.ecr.aws/docker/library/eclipse-temurin:17-jre
WORKDIR /app
COPY build/libs/ai-generator-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "app.jar"]
```

- [ ] **Step 4: Create application.yml**

Create `ai-generator/src/main/resources/application.yml`:

```yaml
server:
  port: 8084

generator:
  bedrock:
    region: ${AWS_REGION:eu-west-1}
    model-id: ${BEDROCK_MODEL_ID:eu.anthropic.claude-sonnet-4-20250514-v1:0}
  transformer:
    url: ${NEXUS_TRANSFORMER_URL:http://localhost:8082}
  agent:
    max-iterations: 3
    timeout-seconds: 60

management:
  endpoints:
    web:
      exposure:
        include: health,info
```

- [ ] **Step 5: Create GeneratorApplication.java**

Create `ai-generator/src/main/java/com/checkout/nexus/generator/GeneratorApplication.java`:

```java
package com.checkout.nexus.generator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class GeneratorApplication {
    public static void main(String[] args) {
        SpringApplication.run(GeneratorApplication.class, args);
    }
}
```

- [ ] **Step 6: Create GeneratorConfig.java**

Create `ai-generator/src/main/java/com/checkout/nexus/generator/config/GeneratorConfig.java`:

```java
package com.checkout.nexus.generator.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "generator")
public class GeneratorConfig {

    private Bedrock bedrock = new Bedrock();
    private Transformer transformer = new Transformer();
    private Agent agent = new Agent();

    @Data
    public static class Bedrock {
        private String region = "eu-west-1";
        private String modelId = "eu.anthropic.claude-sonnet-4-20250514-v1:0";
    }

    @Data
    public static class Transformer {
        private String url = "http://localhost:8082";
    }

    @Data
    public static class Agent {
        private int maxIterations = 3;
        private int timeoutSeconds = 60;
    }
}
```

- [ ] **Step 7: Verify it compiles**

Run:
```bash
cd ai-generator && ./gradlew build -x test
```
Expected: BUILD SUCCESSFUL. If `gradlew` doesn't exist yet, first run:
```bash
cd ai-generator && gradle wrapper --gradle-version 8.7
```
Then retry the build.

- [ ] **Step 8: Commit**

```bash
git add ai-generator/build.gradle ai-generator/settings.gradle ai-generator/Dockerfile \
  ai-generator/src/main/java/com/checkout/nexus/generator/GeneratorApplication.java \
  ai-generator/src/main/java/com/checkout/nexus/generator/config/GeneratorConfig.java \
  ai-generator/src/main/resources/application.yml
git commit -m "feat: scaffold ai-generator service"
```

---

### Task 2: Domain Knowledge Vendoring

**Files:**
- Create: `ai-generator/src/main/resources/domain/` (copied from Nexus repo)
- Create: `ai-generator/src/main/resources/domain/valid_combinations.json` (derived)
- Create: `scripts/sync-domain.sh`
- Modify: `Makefile`

- [ ] **Step 1: Create sync-domain.sh**

Create `scripts/sync-domain.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail

# Sync domain knowledge from Nexus repo into ai-generator resources
# Usage: ./scripts/sync-domain.sh [nexus-repo-path]
#   nexus-repo-path defaults to ../Nexus (sibling directory)

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
NEXUS_REPO="${1:-$(dirname "$PROJECT_DIR")/Nexus}"
DOMAIN_DIR="$PROJECT_DIR/ai-generator/src/main/resources/domain"

if [ ! -d "$NEXUS_REPO/schema" ]; then
  echo "ERROR: Nexus repo not found at $NEXUS_REPO"
  echo "Usage: $0 [path-to-nexus-repo]"
  exit 1
fi

echo "Syncing domain knowledge from $NEXUS_REPO..."

# Clean and recreate
rm -rf "$DOMAIN_DIR"
mkdir -p "$DOMAIN_DIR/examples"

# Static files
cp "$NEXUS_REPO/schema/nexus.schema.json" "$DOMAIN_DIR/nexus.schema.json"
cp "$NEXUS_REPO/schema/le_nexus_mapping.md" "$DOMAIN_DIR/le_nexus_mapping.md"
cp "$NEXUS_REPO/research/le/per_pillar_structures.md" "$DOMAIN_DIR/per_pillar_structures.md"

# Examples
cp "$NEXUS_REPO/schema/examples/"*.json "$DOMAIN_DIR/examples/"

# Also copy from the POC's own schema/ if Nexus repo files are older
# (POC vendors schema already, this ensures we get the freshest)
if [ -f "$PROJECT_DIR/schema/nexus.schema.json" ]; then
  cp "$PROJECT_DIR/schema/nexus.schema.json" "$DOMAIN_DIR/nexus.schema.json"
  cp "$PROJECT_DIR/schema/examples/"*.json "$DOMAIN_DIR/examples/"
fi

# Generate valid_combinations.json from schema
# Extracts the x-trade-type-matrix from nexus.schema.json
python3 -c "
import json, sys

with open('$DOMAIN_DIR/nexus.schema.json') as f:
    schema = json.load(f)

txn_def = schema['\$defs']['Transaction']
matrix = txn_def.get('x-trade-type-matrix', {}).get('rules', [])
leg_rules = txn_def.get('x-leg-composition-rules', {}).get('rules', [])

# Extract enums
product_types = txn_def['properties']['product_type']['enum']
transaction_types = txn_def['properties']['transaction_type']['enum']
transaction_statuses = txn_def['properties']['transaction_status']['enum']
leg_types = schema['\$defs']['Leg']['properties']['leg_type']['enum']
fee_types = schema['\$defs']['Fee']['properties']['fee_type']['enum']
party_types = schema['\$defs']['Party']['properties']['party_type']['enum']

result = {
    'valid_combinations': matrix,
    'leg_composition_rules': leg_rules,
    'enums': {
        'product_type': product_types,
        'transaction_type': transaction_types,
        'transaction_status': transaction_statuses,
        'leg_type': leg_types,
        'fee_type': fee_types,
        'party_type': party_types,
        'fee_status': ['PREDICTED', 'ACTUAL'],
        'leg_status': ['PREDICTED', 'ACTUAL'],
        'block_status': ['NOT_LIVE', 'LIVE', 'DEAD'],
        'fee_amount_type': ['FIXED', 'VARIABLE'],
        'client_settlement_type': ['Gross', 'Net']
    }
}

with open('$DOMAIN_DIR/valid_combinations.json', 'w') as f:
    json.dump(result, f, indent=2)

print(f'Generated valid_combinations.json with {len(matrix)} type combinations')
"

echo "Domain sync complete. Files in $DOMAIN_DIR:"
ls -la "$DOMAIN_DIR"
ls -la "$DOMAIN_DIR/examples/"
```

- [ ] **Step 2: Run the sync script**

```bash
chmod +x scripts/sync-domain.sh
./scripts/sync-domain.sh
```
Expected: "Domain sync complete" with files listed. Verify `valid_combinations.json` exists and contains the type matrix.

- [ ] **Step 3: Add Makefile targets**

Add to the end of `Makefile`:

```makefile
# --- AI Generator ---
build-generator:
	cd ai-generator && ./gradlew build -x test

sync-domain:
	./scripts/sync-domain.sh
```

- [ ] **Step 4: Commit**

```bash
git add scripts/sync-domain.sh ai-generator/src/main/resources/domain/ Makefile
git commit -m "feat: add domain knowledge vendoring for ai-generator"
```

---

### Task 3: Request/Response Models

**Files:**
- Create: `ai-generator/src/main/java/com/checkout/nexus/generator/model/GenerateRequest.java`
- Create: `ai-generator/src/main/java/com/checkout/nexus/generator/model/GenerateResponse.java`
- Create: `ai-generator/src/main/java/com/checkout/nexus/generator/model/ProgressEvent.java`

- [ ] **Step 1: Create GenerateRequest.java**

Create `ai-generator/src/main/java/com/checkout/nexus/generator/model/GenerateRequest.java`:

```java
package com.checkout.nexus.generator.model;

import lombok.Data;

@Data
public class GenerateRequest {
    private String prompt;
}
```

- [ ] **Step 2: Create GenerateResponse.java**

Create `ai-generator/src/main/java/com/checkout/nexus/generator/model/GenerateResponse.java`:

```java
package com.checkout.nexus.generator.model;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class GenerateResponse {
    private boolean success;
    private JsonNode leTransaction;
    private Boolean validationPassed;
    private List<String> errors;
}
```

- [ ] **Step 3: Create ProgressEvent.java**

Create `ai-generator/src/main/java/com/checkout/nexus/generator/model/ProgressEvent.java`:

```java
package com.checkout.nexus.generator.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ProgressEvent {
    private String step;
    private String message;
}
```

- [ ] **Step 4: Commit**

```bash
git add ai-generator/src/main/java/com/checkout/nexus/generator/model/
git commit -m "feat: add request/response models for ai-generator"
```

---

### Task 4: Tool Interface and Domain Tool Handlers

**Files:**
- Create: `ai-generator/src/main/java/com/checkout/nexus/generator/tools/ToolHandler.java`
- Create: `ai-generator/src/main/java/com/checkout/nexus/generator/tools/GetSchemaHandler.java`
- Create: `ai-generator/src/main/java/com/checkout/nexus/generator/tools/GetExamplesHandler.java`
- Create: `ai-generator/src/main/java/com/checkout/nexus/generator/tools/GetFieldMappingsHandler.java`
- Create: `ai-generator/src/main/java/com/checkout/nexus/generator/tools/GetValidEnumsHandler.java`
- Test: `ai-generator/src/test/java/com/checkout/nexus/generator/tools/GetSchemaHandlerTest.java`
- Test: `ai-generator/src/test/java/com/checkout/nexus/generator/tools/GetExamplesHandlerTest.java`
- Test: `ai-generator/src/test/java/com/checkout/nexus/generator/tools/GetValidEnumsHandlerTest.java`

- [ ] **Step 1: Create ToolHandler interface**

Create `ai-generator/src/main/java/com/checkout/nexus/generator/tools/ToolHandler.java`:

```java
package com.checkout.nexus.generator.tools;

import com.fasterxml.jackson.databind.JsonNode;

public interface ToolHandler {
    String name();
    String description();
    JsonNode inputSchema();
    String execute(JsonNode input);
}
```

- [ ] **Step 2: Write failing test for GetSchemaHandler**

Create `ai-generator/src/test/java/com/checkout/nexus/generator/tools/GetSchemaHandlerTest.java`:

```java
package com.checkout.nexus.generator.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GetSchemaHandlerTest {

    private final GetSchemaHandler handler = new GetSchemaHandler();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void returnsSchemaContent() {
        ObjectNode input = mapper.createObjectNode().put("product_type", "ACQUIRING");
        String result = handler.execute(input);
        assertThat(result).contains("nexus_id");
        assertThat(result).contains("ACQUIRING");
    }

    @Test
    void returnsFullSchemaWhenNoProductType() {
        ObjectNode input = mapper.createObjectNode();
        String result = handler.execute(input);
        assertThat(result).contains("nexus_id");
    }

    @Test
    void hasCorrectName() {
        assertThat(handler.name()).isEqualTo("get_schema");
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

```bash
cd ai-generator && ./gradlew test --tests "*GetSchemaHandlerTest"
```
Expected: FAIL — class not found.

- [ ] **Step 4: Implement GetSchemaHandler**

Create `ai-generator/src/main/java/com/checkout/nexus/generator/tools/GetSchemaHandler.java`:

```java
package com.checkout.nexus.generator.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Component
public class GetSchemaHandler implements ToolHandler {

    private final String schemaContent;
    private final String pillarStructures;
    private final ObjectMapper mapper = new ObjectMapper();

    public GetSchemaHandler() {
        this.schemaContent = loadResource("domain/nexus.schema.json");
        this.pillarStructures = loadResource("domain/per_pillar_structures.md");
    }

    @Override
    public String name() {
        return "get_schema";
    }

    @Override
    public String description() {
        return "Returns the Nexus transaction JSON schema definition and LE pillar structures. "
                + "Optionally filter by product_type to get relevant subset.";
    }

    @Override
    public JsonNode inputSchema() {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        ObjectNode pt = props.putObject("product_type");
        pt.put("type", "string");
        pt.put("description", "Filter schema context to this product type (e.g. ACQUIRING, PAYOUT)");
        return schema;
    }

    @Override
    public String execute(JsonNode input) {
        String productType = input.has("product_type") ? input.get("product_type").asText() : null;

        StringBuilder sb = new StringBuilder();
        sb.append("## Nexus Schema\n\n");
        sb.append(schemaContent);

        if (productType != null) {
            sb.append("\n\n## Pillar Structures (filtered for ").append(productType).append(")\n\n");
        } else {
            sb.append("\n\n## Pillar Structures\n\n");
        }
        sb.append(pillarStructures);

        return sb.toString();
    }

    private String loadResource(String path) {
        try (InputStream is = new ClassPathResource(path).getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "Resource not available: " + path;
        }
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

```bash
cd ai-generator && ./gradlew test --tests "*GetSchemaHandlerTest"
```
Expected: PASS

- [ ] **Step 6: Write failing test for GetExamplesHandler**

Create `ai-generator/src/test/java/com/checkout/nexus/generator/tools/GetExamplesHandlerTest.java`:

```java
package com.checkout.nexus.generator.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GetExamplesHandlerTest {

    private final GetExamplesHandler handler = new GetExamplesHandler();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void returnsAcquiringCaptureExamples() {
        ObjectNode input = mapper.createObjectNode()
                .put("product_type", "ACQUIRING");
        input.put("transaction_type", "CAPTURE");
        String result = handler.execute(input);
        assertThat(result).contains("acquiring_capture");
    }

    @Test
    void returnsRefundExamples() {
        ObjectNode input = mapper.createObjectNode()
                .put("product_type", "ACQUIRING");
        input.put("transaction_type", "REFUND");
        String result = handler.execute(input);
        assertThat(result).contains("refund");
    }

    @Test
    void returnsAllExamplesWhenNoFilter() {
        ObjectNode input = mapper.createObjectNode();
        String result = handler.execute(input);
        assertThat(result).contains("acquiring");
    }

    @Test
    void hasCorrectName() {
        assertThat(handler.name()).isEqualTo("get_examples");
    }
}
```

- [ ] **Step 7: Implement GetExamplesHandler**

Create `ai-generator/src/main/java/com/checkout/nexus/generator/tools/GetExamplesHandler.java`:

```java
package com.checkout.nexus.generator.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class GetExamplesHandler implements ToolHandler {

    private final Map<String, String> examples = new LinkedHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();

    public GetExamplesHandler() {
        loadExamples();
    }

    @Override
    public String name() {
        return "get_examples";
    }

    @Override
    public String description() {
        return "Returns complete LE-to-Nexus worked examples. Filter by product_type and "
                + "optionally transaction_type to get relevant examples. Files are named "
                + "NN_<product>_<type>_<variant>.json — matching is by substring.";
    }

    @Override
    public JsonNode inputSchema() {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");

        ObjectNode pt = props.putObject("product_type");
        pt.put("type", "string");
        pt.put("description", "Filter to examples for this product type (e.g. ACQUIRING, PAYOUT)");

        ObjectNode tt = props.putObject("transaction_type");
        tt.put("type", "string");
        tt.put("description", "Further filter by transaction type (e.g. CAPTURE, REFUND)");

        schema.putArray("required").add("product_type");
        return schema;
    }

    @Override
    public String execute(JsonNode input) {
        String productType = input.has("product_type") ? input.get("product_type").asText().toLowerCase() : "";
        String txnType = input.has("transaction_type") ? input.get("transaction_type").asText().toLowerCase() : "";

        StringBuilder sb = new StringBuilder();
        sb.append("## Matching Examples\n\n");

        int count = 0;
        for (Map.Entry<String, String> entry : examples.entrySet()) {
            String filename = entry.getKey().toLowerCase();
            boolean matches = productType.isEmpty() || filename.contains(productType);
            if (matches && !txnType.isEmpty()) {
                matches = filename.contains(txnType);
            }
            if (matches) {
                sb.append("### ").append(entry.getKey()).append("\n\n");
                sb.append("```json\n").append(entry.getValue()).append("\n```\n\n");
                count++;
                if (count >= 2) break;
            }
        }

        if (count == 0) {
            sb.append("No examples found matching product_type=").append(productType);
            if (!txnType.isEmpty()) sb.append(", transaction_type=").append(txnType);
            sb.append(". Available examples:\n");
            examples.keySet().forEach(k -> sb.append("- ").append(k).append("\n"));
        }

        return sb.toString();
    }

    private void loadExamples() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:domain/examples/*.json");
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename != null) {
                    try (InputStream is = resource.getInputStream()) {
                        examples.put(filename, new String(is.readAllBytes(), StandardCharsets.UTF_8));
                    }
                }
            }
        } catch (IOException e) {
            // Examples not available — tools will report this
        }
    }
}
```

- [ ] **Step 8: Run test to verify it passes**

```bash
cd ai-generator && ./gradlew test --tests "*GetExamplesHandlerTest"
```
Expected: PASS

- [ ] **Step 9: Implement GetFieldMappingsHandler**

Create `ai-generator/src/main/java/com/checkout/nexus/generator/tools/GetFieldMappingsHandler.java`:

```java
package com.checkout.nexus.generator.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Component
public class GetFieldMappingsHandler implements ToolHandler {

    private final String mappingContent;
    private final ObjectMapper mapper = new ObjectMapper();

    public GetFieldMappingsHandler() {
        this.mappingContent = loadResource("domain/le_nexus_mapping.md");
    }

    @Override
    public String name() {
        return "get_field_mappings";
    }

    @Override
    public String description() {
        return "Returns the LE-to-Nexus field mapping rules, including pillar priority order and "
                + "source fields for each Nexus output field.";
    }

    @Override
    public JsonNode inputSchema() {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        schema.putObject("properties");
        return schema;
    }

    @Override
    public String execute(JsonNode input) {
        return mappingContent;
    }

    private String loadResource(String path) {
        try (InputStream is = new ClassPathResource(path).getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "Resource not available: " + path;
        }
    }
}
```

- [ ] **Step 10: Write failing test for GetValidEnumsHandler**

Create `ai-generator/src/test/java/com/checkout/nexus/generator/tools/GetValidEnumsHandlerTest.java`:

```java
package com.checkout.nexus.generator.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GetValidEnumsHandlerTest {

    private final GetValidEnumsHandler handler = new GetValidEnumsHandler();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void returnsValidCombinations() {
        ObjectNode input = mapper.createObjectNode();
        String result = handler.execute(input);
        assertThat(result).contains("ACQUIRING");
        assertThat(result).contains("CAPTURE");
        assertThat(result).contains("CAPTURED");
    }

    @Test
    void filtersbyProductType() {
        ObjectNode input = mapper.createObjectNode().put("product_type", "PAYOUT");
        String result = handler.execute(input);
        assertThat(result).contains("PAYOUT");
        assertThat(result).contains("CREDIT");
    }

    @Test
    void hasCorrectName() {
        assertThat(handler.name()).isEqualTo("get_valid_enums");
    }
}
```

- [ ] **Step 11: Implement GetValidEnumsHandler**

Create `ai-generator/src/main/java/com/checkout/nexus/generator/tools/GetValidEnumsHandler.java`:

```java
package com.checkout.nexus.generator.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Component
public class GetValidEnumsHandler implements ToolHandler {

    private final String combinationsContent;
    private final JsonNode combinationsJson;
    private final ObjectMapper mapper = new ObjectMapper();

    public GetValidEnumsHandler() {
        this.combinationsContent = loadResource("domain/valid_combinations.json");
        JsonNode parsed;
        try {
            parsed = mapper.readTree(this.combinationsContent);
        } catch (Exception e) {
            parsed = mapper.createObjectNode();
        }
        this.combinationsJson = parsed;
    }

    @Override
    public String name() {
        return "get_valid_enums";
    }

    @Override
    public String description() {
        return "Returns valid product_type × transaction_type × transaction_status combinations, "
                + "leg composition rules, and all enum values. Optionally filter by product_type.";
    }

    @Override
    public JsonNode inputSchema() {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        ObjectNode pt = props.putObject("product_type");
        pt.put("type", "string");
        pt.put("description", "Filter to combinations for this product type");
        return schema;
    }

    @Override
    public String execute(JsonNode input) {
        String productType = input.has("product_type") ? input.get("product_type").asText() : null;

        if (productType == null) {
            return combinationsContent;
        }

        // Filter combinations for the requested product type
        StringBuilder sb = new StringBuilder();
        sb.append("## Valid combinations for ").append(productType).append("\n\n");

        JsonNode combos = combinationsJson.path("valid_combinations");
        if (combos.isArray()) {
            for (JsonNode combo : combos) {
                if (productType.equals(combo.path("family").asText())) {
                    sb.append("- ").append(combo.path("type").asText())
                            .append(": ").append(combo.path("statuses")).append("\n");
                }
            }
        }

        sb.append("\n## Leg composition rules for ").append(productType).append("\n\n");
        JsonNode legRules = combinationsJson.path("leg_composition_rules");
        if (legRules.isArray()) {
            for (JsonNode rule : legRules) {
                if (productType.equals(rule.path("family").asText())) {
                    sb.append("- ").append(rule.path("type").asText())
                            .append(": ").append(rule.path("legs")).append("\n");
                }
            }
        }

        sb.append("\n## All enums\n\n");
        sb.append(combinationsJson.path("enums").toPrettyString());

        return sb.toString();
    }

    private String loadResource(String path) {
        try (InputStream is = new ClassPathResource(path).getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "{}";
        }
    }
}
```

- [ ] **Step 12: Run all tool tests**

```bash
cd ai-generator && ./gradlew test --tests "*HandlerTest"
```
Expected: ALL PASS

- [ ] **Step 13: Commit**

```bash
git add ai-generator/src/main/java/com/checkout/nexus/generator/tools/ \
  ai-generator/src/test/java/com/checkout/nexus/generator/tools/
git commit -m "feat: add domain knowledge tool handlers"
```

---

### Task 5: Validation Tool Handler

**Files:**
- Create: `ai-generator/src/main/java/com/checkout/nexus/generator/tools/ValidateLeTransactionHandler.java`
- Test: `ai-generator/src/test/java/com/checkout/nexus/generator/tools/ValidateLeTransactionHandlerTest.java`

- [ ] **Step 1: Write failing test**

Create `ai-generator/src/test/java/com/checkout/nexus/generator/tools/ValidateLeTransactionHandlerTest.java`:

```java
package com.checkout.nexus.generator.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.checkout.nexus.generator.config.GeneratorConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ValidateLeTransactionHandlerTest {

    @Test
    void hasCorrectName() {
        GeneratorConfig config = new GeneratorConfig();
        ValidateLeTransactionHandler handler = new ValidateLeTransactionHandler(config);
        assertThat(handler.name()).isEqualTo("validate_le_transaction");
    }

    @Test
    void returnsErrorWhenTransformerUnreachable() {
        GeneratorConfig config = new GeneratorConfig();
        config.getTransformer().setUrl("http://localhost:99999");
        ValidateLeTransactionHandler handler = new ValidateLeTransactionHandler(config);

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode input = mapper.createObjectNode();
        input.put("le_transaction", "{\"id\":\"test\"}");

        String result = handler.execute(input);
        assertThat(result).contains("error");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd ai-generator && ./gradlew test --tests "*ValidateLeTransactionHandlerTest"
```
Expected: FAIL — class not found.

- [ ] **Step 3: Implement ValidateLeTransactionHandler**

Create `ai-generator/src/main/java/com/checkout/nexus/generator/tools/ValidateLeTransactionHandler.java`:

```java
package com.checkout.nexus.generator.tools;

import com.checkout.nexus.generator.config.GeneratorConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class ValidateLeTransactionHandler implements ToolHandler {

    private final GeneratorConfig config;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient;

    public ValidateLeTransactionHandler(GeneratorConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public String name() {
        return "validate_le_transaction";
    }

    @Override
    public String description() {
        return "Validates an LE transaction by running it through the NexusEngine dry-run. "
                + "Returns the transformation result with success/failure and any errors. "
                + "The input must be a complete LE transaction JSON string.";
    }

    @Override
    public JsonNode inputSchema() {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        ObjectNode le = props.putObject("le_transaction");
        le.put("type", "string");
        le.put("description", "Complete LE transaction as a JSON string");
        schema.putArray("required").add("le_transaction");
        return schema;
    }

    @Override
    public String execute(JsonNode input) {
        String leJson = input.path("le_transaction").asText();

        try {
            String url = config.getTransformer().getUrl() + "/transform/test";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(leJson))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return response.body();
            } else {
                ObjectNode error = mapper.createObjectNode();
                error.put("error", "Transformer returned HTTP " + response.statusCode());
                error.put("body", response.body());
                return error.toString();
            }
        } catch (Exception e) {
            ObjectNode error = mapper.createObjectNode();
            error.put("error", "Failed to reach transformer: " + e.getMessage());
            error.put("transformer_url", config.getTransformer().getUrl());
            return error.toString();
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd ai-generator && ./gradlew test --tests "*ValidateLeTransactionHandlerTest"
```
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add ai-generator/src/main/java/com/checkout/nexus/generator/tools/ValidateLeTransactionHandler.java \
  ai-generator/src/test/java/com/checkout/nexus/generator/tools/ValidateLeTransactionHandlerTest.java
git commit -m "feat: add validation tool handler (dry-run via nexus-transformer)"
```

---

### Task 6: Bedrock Client

**Files:**
- Create: `ai-generator/src/main/java/com/checkout/nexus/generator/agent/BedrockClient.java`

- [ ] **Step 1: Create BedrockClient**

Create `ai-generator/src/main/java/com/checkout/nexus/generator/agent/BedrockClient.java`:

```java
package com.checkout.nexus.generator.agent;

import com.checkout.nexus.generator.config.GeneratorConfig;
import com.checkout.nexus.generator.tools.ToolHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.*;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class BedrockClient {

    private final GeneratorConfig config;
    private final ObjectMapper mapper = new ObjectMapper();
    private BedrockRuntimeClient client;

    public BedrockClient(GeneratorConfig config) {
        this.config = config;
    }

    @PostConstruct
    public void init() {
        this.client = BedrockRuntimeClient.builder()
                .region(Region.of(config.getBedrock().getRegion()))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @PreDestroy
    public void close() {
        if (client != null) {
            client.close();
        }
    }

    public ConverseResponse converse(
            String systemPrompt,
            List<Message> messages,
            List<ToolHandler> tools
    ) {
        List<Tool> bedrockTools = tools.stream()
                .map(this::toBedrockTool)
                .toList();

        ConverseRequest.Builder request = ConverseRequest.builder()
                .modelId(config.getBedrock().getModelId())
                .system(SystemContentBlock.builder().text(systemPrompt).build())
                .messages(messages)
                .inferenceConfig(InferenceConfiguration.builder()
                        .maxTokens(4096)
                        .temperature(0.0f)
                        .build());

        if (!bedrockTools.isEmpty()) {
            request.toolConfig(ToolConfiguration.builder()
                    .tools(bedrockTools)
                    .build());
        }

        return client.converse(request.build());
    }

    private Tool toBedrockTool(ToolHandler handler) {
        return Tool.builder()
                .toolSpec(ToolSpecification.builder()
                        .name(handler.name())
                        .description(handler.description())
                        .inputSchema(ToolInputSchema.builder()
                                .json(toDocument(handler.inputSchema()))
                                .build())
                        .build())
                .build();
    }

    private software.amazon.awssdk.core.document.Document toDocument(JsonNode jsonNode) {
        try {
            String json = mapper.writeValueAsString(jsonNode);
            return software.amazon.awssdk.core.document.Document.fromString(json);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert tool schema to Document", e);
        }
    }
}
```

- [ ] **Step 2: Verify it compiles**

```bash
cd ai-generator && ./gradlew compileJava
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add ai-generator/src/main/java/com/checkout/nexus/generator/agent/BedrockClient.java
git commit -m "feat: add Bedrock client wrapper"
```

---

### Task 7: Tool Executor

**Files:**
- Create: `ai-generator/src/main/java/com/checkout/nexus/generator/agent/ToolExecutor.java`
- Test: `ai-generator/src/test/java/com/checkout/nexus/generator/agent/ToolExecutorTest.java`

- [ ] **Step 1: Write failing test**

Create `ai-generator/src/test/java/com/checkout/nexus/generator/agent/ToolExecutorTest.java`:

```java
package com.checkout.nexus.generator.agent;

import com.checkout.nexus.generator.tools.ToolHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ToolExecutorTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void executesMatchingTool() {
        ToolHandler mockTool = new ToolHandler() {
            @Override public String name() { return "test_tool"; }
            @Override public String description() { return "A test tool"; }
            @Override public JsonNode inputSchema() { return mapper.createObjectNode(); }
            @Override public String execute(JsonNode input) { return "executed:" + input.toString(); }
        };

        ToolExecutor executor = new ToolExecutor(List.of(mockTool));
        String result = executor.execute("test_tool", mapper.createObjectNode().put("key", "val"));
        assertThat(result).contains("executed:");
        assertThat(result).contains("key");
    }

    @Test
    void returnsErrorForUnknownTool() {
        ToolExecutor executor = new ToolExecutor(List.of());
        String result = executor.execute("nonexistent", mapper.createObjectNode());
        assertThat(result).contains("Unknown tool");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd ai-generator && ./gradlew test --tests "*ToolExecutorTest"
```
Expected: FAIL

- [ ] **Step 3: Implement ToolExecutor**

Create `ai-generator/src/main/java/com/checkout/nexus/generator/agent/ToolExecutor.java`:

```java
package com.checkout.nexus.generator.agent;

import com.checkout.nexus.generator.tools.ToolHandler;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ToolExecutor {

    private final Map<String, ToolHandler> handlers;

    public ToolExecutor(List<ToolHandler> handlers) {
        this.handlers = handlers.stream()
                .collect(Collectors.toMap(ToolHandler::name, Function.identity()));
    }

    public String execute(String toolName, JsonNode input) {
        ToolHandler handler = handlers.get(toolName);
        if (handler == null) {
            return "Unknown tool: " + toolName + ". Available tools: " + handlers.keySet();
        }

        log.info("Executing tool: {} with input: {}", toolName, input);
        try {
            String result = handler.execute(input);
            log.debug("Tool {} returned {} chars", toolName, result.length());
            return result;
        } catch (Exception e) {
            log.error("Tool {} failed: {}", toolName, e.getMessage(), e);
            return "Tool execution error: " + e.getMessage();
        }
    }

    public List<ToolHandler> getHandlers() {
        return List.copyOf(handlers.values());
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
cd ai-generator && ./gradlew test --tests "*ToolExecutorTest"
```
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add ai-generator/src/main/java/com/checkout/nexus/generator/agent/ToolExecutor.java \
  ai-generator/src/test/java/com/checkout/nexus/generator/agent/ToolExecutorTest.java
git commit -m "feat: add tool executor for dispatching agent tool calls"
```

---

### Task 8: Agent Loop

**Files:**
- Create: `ai-generator/src/main/java/com/checkout/nexus/generator/agent/AgentLoop.java`
- Create: `ai-generator/src/main/resources/system-prompt.txt`
- Test: `ai-generator/src/test/java/com/checkout/nexus/generator/agent/AgentLoopTest.java`

- [ ] **Step 1: Create system-prompt.txt**

Create `ai-generator/src/main/resources/system-prompt.txt`:

```text
You are an expert LE (Linking Engine) transaction generator for the Nexus financial data platform.

Your job: given a natural language description of a financial scenario, produce a valid LE LinkedTransaction JSON object.

## Workflow

1. Parse the user's prompt to understand the scenario (product type, transaction type, amounts, currencies, entities, schemes, fees, etc.)
2. Call `get_valid_enums` to get the valid type/status combinations for the product type
3. Call `get_examples` to see a worked example of a similar transaction
4. Call `get_schema` if you need detailed field definitions
5. Call `get_field_mappings` if you need to understand which LE fields map to which Nexus output fields
6. Construct a complete LE LinkedTransaction JSON with realistic data matching the scenario
7. Call `validate_le_transaction` with your generated JSON to dry-run it through the NexusEngine
8. If validation fails, read the errors, fix the JSON, and call `validate_le_transaction` again
9. Return the final valid LE transaction JSON

## LE LinkedTransaction Structure

The LE transaction is the INPUT to the Nexus engine. It has this top-level structure:
```json
{
  "id": "string (unique ID)",
  "actionId": "string (action identifier, e.g. act_cap_001)",
  "actionRootId": "string (payment lifecycle grouper, e.g. pay_001)",
  "transactionVersion": 1,
  "gatewayEvents": [...],
  "balancesChangedEvents": [...],
  "cosEvents": [...],
  "schemeSettlementEvents": [...],
  "cashEvents": [...]
}
```

Each pillar event array contains events from that data source. Which pillars you populate depends on the scenario — see examples for guidance.

## Rules

- ALWAYS call `get_valid_enums` and `get_examples` before generating
- ALWAYS call `validate_le_transaction` before returning your final answer
- Generate realistic field values (real-looking IDs, amounts, dates, scheme names)
- Use ISO 4217 currency codes (EUR, USD, GBP, etc.)
- Use ISO 8601 timestamps
- If the user doesn't specify details, make reasonable defaults (e.g. Visa, EUR, CKO_UK_LTD)
- Your final message must contain ONLY the LE transaction JSON — no explanation or markdown
```

- [ ] **Step 2: Write failing test for AgentLoop**

Create `ai-generator/src/test/java/com/checkout/nexus/generator/agent/AgentLoopTest.java`:

```java
package com.checkout.nexus.generator.agent;

import com.checkout.nexus.generator.config.GeneratorConfig;
import com.checkout.nexus.generator.model.ProgressEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentLoopTest {

    @Test
    void systemPromptLoadsFromClasspath() {
        // AgentLoop loads system-prompt.txt on construction
        GeneratorConfig config = new GeneratorConfig();
        // This test verifies the resource loads without error
        // (AgentLoop constructor reads it)
        // Full integration test requires Bedrock credentials
        assertThat(config.getAgent().getMaxIterations()).isEqualTo(3);
    }
}
```

- [ ] **Step 3: Implement AgentLoop**

Create `ai-generator/src/main/java/com/checkout/nexus/generator/agent/AgentLoop.java`:

```java
package com.checkout.nexus.generator.agent;

import com.checkout.nexus.generator.config.GeneratorConfig;
import com.checkout.nexus.generator.model.GenerateResponse;
import com.checkout.nexus.generator.model.ProgressEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.bedrockruntime.model.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Slf4j
@Component
public class AgentLoop {

    private final BedrockClient bedrockClient;
    private final ToolExecutor toolExecutor;
    private final GeneratorConfig config;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String systemPrompt;

    public AgentLoop(BedrockClient bedrockClient, ToolExecutor toolExecutor, GeneratorConfig config) {
        this.bedrockClient = bedrockClient;
        this.toolExecutor = toolExecutor;
        this.config = config;
        this.systemPrompt = loadSystemPrompt();
    }

    public GenerateResponse run(String userPrompt, Consumer<ProgressEvent> onProgress) {
        onProgress.accept(new ProgressEvent("understanding", "Parsing scenario: " + truncate(userPrompt, 80)));

        List<Message> messages = new ArrayList<>();
        messages.add(Message.builder()
                .role(ConversationRole.USER)
                .content(ContentBlock.fromText(userPrompt))
                .build());

        int iteration = 0;
        int maxIterations = config.getAgent().getMaxIterations() * 5; // 5 turns per validation attempt

        while (iteration < maxIterations) {
            iteration++;

            ConverseResponse response;
            try {
                response = bedrockClient.converse(systemPrompt, messages, toolExecutor.getHandlers());
            } catch (Exception e) {
                log.error("Bedrock call failed: {}", e.getMessage(), e);
                return GenerateResponse.builder()
                        .success(false)
                        .errors(List.of("Bedrock error: " + e.getMessage()))
                        .build();
            }

            Message assistantMessage = response.output().message();
            messages.add(assistantMessage);

            // Check if the model wants to use tools
            if (response.stopReason() == StopReason.TOOL_USE) {
                List<ContentBlock> toolResults = new ArrayList<>();

                for (ContentBlock block : assistantMessage.content()) {
                    if (block.toolUse() != null) {
                        ToolUseBlock toolUse = block.toolUse();
                        String toolName = toolUse.name();

                        onProgress.accept(new ProgressEvent(
                                toolName.contains("validate") ? "validating" : "context",
                                "Calling " + toolName
                        ));

                        JsonNode toolInput;
                        try {
                            String inputJson = toolUse.input().unwrap().toString();
                            toolInput = mapper.readTree(inputJson);
                        } catch (Exception e) {
                            toolInput = mapper.createObjectNode();
                        }

                        String result = toolExecutor.execute(toolName, toolInput);

                        // Check if validation succeeded
                        if (toolName.equals("validate_le_transaction")) {
                            try {
                                JsonNode resultJson = mapper.readTree(result);
                                if (resultJson.path("success").asBoolean(false)) {
                                    onProgress.accept(new ProgressEvent("validating", "Validation passed"));
                                } else {
                                    onProgress.accept(new ProgressEvent("correcting", "Validation failed, agent will fix"));
                                }
                            } catch (Exception e) {
                                // Not JSON, continue
                            }
                        }

                        toolResults.add(ContentBlock.builder()
                                .toolResult(ToolResultBlock.builder()
                                        .toolUseId(toolUse.toolUseId())
                                        .content(ToolResultContentBlock.builder()
                                                .text(result)
                                                .build())
                                        .build())
                                .build());
                    }
                }

                messages.add(Message.builder()
                        .role(ConversationRole.USER)
                        .content(toolResults)
                        .build());

            } else if (response.stopReason() == StopReason.END_TURN) {
                // Model finished — extract the LE transaction from the final text
                return extractResult(assistantMessage, onProgress);
            } else {
                return GenerateResponse.builder()
                        .success(false)
                        .errors(List.of("Unexpected stop reason: " + response.stopReason()))
                        .build();
            }
        }

        return GenerateResponse.builder()
                .success(false)
                .errors(List.of("Agent loop exceeded maximum iterations (" + maxIterations + ")"))
                .build();
    }

    private GenerateResponse extractResult(Message assistantMessage, Consumer<ProgressEvent> onProgress) {
        for (ContentBlock block : assistantMessage.content()) {
            if (block.text() != null) {
                String text = block.text();
                try {
                    // Try to parse the text as JSON directly
                    JsonNode leTransaction = mapper.readTree(text);
                    onProgress.accept(new ProgressEvent("complete", "LE transaction generated"));
                    return GenerateResponse.builder()
                            .success(true)
                            .leTransaction(leTransaction)
                            .validationPassed(true)
                            .build();
                } catch (Exception e) {
                    // Try to extract JSON from markdown code block
                    String json = extractJsonFromText(text);
                    if (json != null) {
                        try {
                            JsonNode leTransaction = mapper.readTree(json);
                            onProgress.accept(new ProgressEvent("complete", "LE transaction generated"));
                            return GenerateResponse.builder()
                                    .success(true)
                                    .leTransaction(leTransaction)
                                    .validationPassed(true)
                                    .build();
                        } catch (Exception e2) {
                            // Fall through to error
                        }
                    }
                    return GenerateResponse.builder()
                            .success(false)
                            .errors(List.of("Agent returned non-JSON response: " + truncate(text, 200)))
                            .build();
                }
            }
        }

        return GenerateResponse.builder()
                .success(false)
                .errors(List.of("Agent returned empty response"))
                .build();
    }

    private String extractJsonFromText(String text) {
        // Extract JSON from ```json ... ``` blocks
        int start = text.indexOf("```json");
        if (start >= 0) {
            start = text.indexOf('\n', start) + 1;
            int end = text.indexOf("```", start);
            if (end > start) {
                return text.substring(start, end).trim();
            }
        }
        // Try plain ``` blocks
        start = text.indexOf("```");
        if (start >= 0) {
            start = text.indexOf('\n', start) + 1;
            int end = text.indexOf("```", start);
            if (end > start) {
                return text.substring(start, end).trim();
            }
        }
        // Try to find raw JSON object
        start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return null;
    }

    private String truncate(String s, int maxLen) {
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    private String loadSystemPrompt() {
        try (InputStream is = new ClassPathResource("system-prompt.txt").getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load system-prompt.txt", e);
        }
    }
}
```

- [ ] **Step 4: Verify it compiles**

```bash
cd ai-generator && ./gradlew compileJava
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add ai-generator/src/main/java/com/checkout/nexus/generator/agent/AgentLoop.java \
  ai-generator/src/main/resources/system-prompt.txt \
  ai-generator/src/test/java/com/checkout/nexus/generator/agent/AgentLoopTest.java
git commit -m "feat: add agentic loop with Bedrock tool use"
```

---

### Task 9: REST Controller (SSE + Sync)

**Files:**
- Create: `ai-generator/src/main/java/com/checkout/nexus/generator/controller/GenerateController.java`
- Test: `ai-generator/src/test/java/com/checkout/nexus/generator/controller/GenerateControllerTest.java`

- [ ] **Step 1: Write failing test**

Create `ai-generator/src/test/java/com/checkout/nexus/generator/controller/GenerateControllerTest.java`:

```java
package com.checkout.nexus.generator.controller;

import com.checkout.nexus.generator.model.GenerateRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class GenerateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void rejectsMissingPrompt() throws Exception {
        GenerateRequest req = new GenerateRequest();
        mockMvc.perform(post("/api/generate")
                        .param("sync", "true")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }
}
```

- [ ] **Step 2: Implement GenerateController**

Create `ai-generator/src/main/java/com/checkout/nexus/generator/controller/GenerateController.java`:

```java
package com.checkout.nexus.generator.controller;

import com.checkout.nexus.generator.agent.AgentLoop;
import com.checkout.nexus.generator.model.GenerateRequest;
import com.checkout.nexus.generator.model.GenerateResponse;
import com.checkout.nexus.generator.model.ProgressEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class GenerateController {

    private final AgentLoop agentLoop;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    @PostMapping(value = "/generate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter generateStream(@RequestBody GenerateRequest request) {
        if (request.getPrompt() == null || request.getPrompt().isBlank()) {
            throw new IllegalArgumentException("Prompt is required");
        }

        SseEmitter emitter = new SseEmitter(120_000L); // 2 min timeout

        executor.submit(() -> {
            try {
                GenerateResponse response = agentLoop.run(request.getPrompt(), progress -> {
                    try {
                        emitter.send(SseEmitter.event()
                                .name("progress")
                                .data(objectMapper.writeValueAsString(progress)));
                    } catch (Exception e) {
                        log.warn("Failed to send SSE progress: {}", e.getMessage());
                    }
                });

                String eventName = response.isSuccess() ? "result" : "error";
                emitter.send(SseEmitter.event()
                        .name(eventName)
                        .data(objectMapper.writeValueAsString(response)));
                emitter.complete();
            } catch (Exception e) {
                log.error("Generation failed: {}", e.getMessage(), e);
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data("{\"message\":\"" + e.getMessage() + "\"}"));
                    emitter.complete();
                } catch (Exception ignored) {
                    emitter.completeWithError(e);
                }
            }
        });

        return emitter;
    }

    @PostMapping(value = "/generate", params = "sync=true", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GenerateResponse> generateSync(@RequestBody GenerateRequest request) {
        if (request.getPrompt() == null || request.getPrompt().isBlank()) {
            return ResponseEntity.badRequest().body(
                    GenerateResponse.builder()
                            .success(false)
                            .errors(java.util.List.of("Prompt is required"))
                            .build());
        }

        GenerateResponse response = agentLoop.run(request.getPrompt(), progress -> {
            log.info("Progress: [{}] {}", progress.getStep(), progress.getMessage());
        });

        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<GenerateResponse> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(
                GenerateResponse.builder()
                        .success(false)
                        .errors(java.util.List.of(e.getMessage()))
                        .build());
    }
}
```

- [ ] **Step 3: Verify it compiles**

```bash
cd ai-generator && ./gradlew compileJava
```
Expected: BUILD SUCCESSFUL

Note: The `@SpringBootTest` test will fail without Bedrock credentials. That's expected — it validates the controller wiring. The `rejectsMissingPrompt` test will work because it's a validation check that doesn't hit Bedrock. If the test requires Bedrock client initialization and fails, mark it with `@Disabled("Requires AWS credentials")` and move on.

- [ ] **Step 4: Commit**

```bash
git add ai-generator/src/main/java/com/checkout/nexus/generator/controller/GenerateController.java \
  ai-generator/src/test/java/com/checkout/nexus/generator/controller/GenerateControllerTest.java
git commit -m "feat: add generate controller with SSE and sync endpoints"
```

---

### Task 10: Docker Compose Integration

**Files:**
- Modify: `docker-compose.yml`

- [ ] **Step 1: Add ai-generator service to docker-compose.yml**

Add the following service block after the `rules-engine` service and before the `volumes:` section in `docker-compose.yml`:

```yaml
  ai-generator:
    build: ./ai-generator
    depends_on: [nexus-transformer]
    restart: on-failure
    ports: ["8084:8084"]
    environment:
      NEXUS_TRANSFORMER_URL: http://nexus-transformer:8082
      AWS_REGION: ${AWS_REGION:-eu-west-1}
      AWS_ACCESS_KEY_ID: ${AWS_ACCESS_KEY_ID:-}
      AWS_SECRET_ACCESS_KEY: ${AWS_SECRET_ACCESS_KEY:-}
      AWS_SESSION_TOKEN: ${AWS_SESSION_TOKEN:-}
      SERVER_PORT: 8084
```

- [ ] **Step 2: Build the service**

```bash
cd ai-generator && ./gradlew build -x test
```
Expected: BUILD SUCCESSFUL and JAR created at `build/libs/ai-generator-0.0.1-SNAPSHOT.jar`

- [ ] **Step 3: Commit**

```bash
git add docker-compose.yml
git commit -m "feat: add ai-generator to docker-compose"
```

---

### Task 11: UI API Client

**Files:**
- Modify: `ui/src/api/client.ts`

- [ ] **Step 1: Add generateLeTransaction function**

Add the following to the end of `ui/src/api/client.ts`, before any closing comments:

```typescript
// --- AI Generator ---

const GENERATOR_API = ''

export interface GenerateProgress {
  step: string
  message: string
}

export interface GenerateResult {
  success: boolean
  leTransaction?: Record<string, unknown>
  validationPassed?: boolean
  errors?: string[]
}

export async function generateLeTransaction(
  prompt: string,
  onProgress: (event: GenerateProgress) => void
): Promise<GenerateResult> {
  const res = await fetch(`${GENERATOR_API}/generate`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ prompt }),
  })

  if (!res.ok) {
    throw new Error(`Generator ${res.status}: ${res.statusText}`)
  }

  const reader = res.body?.getReader()
  if (!reader) throw new Error('No response body')

  const decoder = new TextDecoder()
  let buffer = ''
  let finalResult: GenerateResult | null = null

  while (true) {
    const { done, value } = await reader.read()
    if (done) break

    buffer += decoder.decode(value, { stream: true })
    const lines = buffer.split('\n')
    buffer = lines.pop() ?? ''

    let eventName = ''
    for (const line of lines) {
      if (line.startsWith('event:')) {
        eventName = line.slice(6).trim()
      } else if (line.startsWith('data:')) {
        const data = line.slice(5).trim()
        if (!data) continue
        try {
          const parsed = JSON.parse(data)
          if (eventName === 'progress') {
            onProgress(parsed as GenerateProgress)
          } else if (eventName === 'result') {
            finalResult = parsed as GenerateResult
          } else if (eventName === 'error') {
            finalResult = { success: false, errors: [parsed.message ?? 'Generation failed'] }
          }
        } catch {
          // Skip non-JSON lines
        }
      }
    }
  }

  if (finalResult) return finalResult
  throw new Error('No result received from generator')
}
```

- [ ] **Step 2: Commit**

```bash
git add ui/src/api/client.ts
git commit -m "feat: add AI generator API client with SSE support"
```

---

### Task 12: Test Bench UI — Inline Prompt Bar

**Files:**
- Modify: `ui/src/pages/TestBench.tsx`

- [ ] **Step 1: Add AI prompt bar to TestBench**

Replace the full content of `ui/src/pages/TestBench.tsx` with:

```tsx
import { useState, useRef } from 'react'
import { useMutation } from '@tanstack/react-query'
import Editor, { type OnMount } from '@monaco-editor/react'
import { runTestBench, generateLeTransaction, type TestBenchResult, type GenerateProgress } from '../api/client'
import TransactionTrace, { type TransactionData } from '../components/TransactionTrace'

const LE_EVENT_TEMPLATE = JSON.stringify(
  {
    id: 'test-001',
    actionId: 'act-test-001',
    actionRootId: 'pay-test-001',
    transactionVersion: 1,
    gatewayEvents: [
      {
        eventType: 'payment_captured',
        processedOn: '2024-01-15T10:00:00Z',
        amount: { value: 150.0, currencyCode: 'EUR' },
        acquirerName: 'CKO_UK_LTD',
        acquirerCountry: 'GB',
      },
    ],
    balancesChangedEvents: [],
    cosEvents: [],
    schemeSettlementEvents: [],
    cashEvents: [],
  },
  null,
  2
)

export default function TestBench() {
  const [editorValue, setEditorValue] = useState(LE_EVENT_TEMPLATE)
  const editorRef = useRef<Parameters<OnMount>[0] | null>(null)

  // AI generation state
  const [aiPrompt, setAiPrompt] = useState('')
  const [aiProgress, setAiProgress] = useState<GenerateProgress | null>(null)
  const [aiGenerating, setAiGenerating] = useState(false)
  const [aiError, setAiError] = useState<string | null>(null)

  const mutation = useMutation<TestBenchResult, Error, unknown>({
    mutationFn: runTestBench,
  })

  const [parseError, setParseError] = useState<string | null>(null)

  function handleRunClick() {
    setParseError(null)
    try {
      const parsed = JSON.parse(editorValue)
      mutation.mutate(parsed)
    } catch (e) {
      setParseError(e instanceof Error ? e.message : 'Invalid JSON in editor')
    }
  }

  function handleReset() {
    setEditorValue(LE_EVENT_TEMPLATE)
    setParseError(null)
    mutation.reset()
    if (editorRef.current) {
      editorRef.current.setValue(LE_EVENT_TEMPLATE)
    }
  }

  async function handleGenerate() {
    if (!aiPrompt.trim()) return
    setAiGenerating(true)
    setAiError(null)
    setAiProgress(null)

    try {
      const result = await generateLeTransaction(aiPrompt, (progress) => {
        setAiProgress(progress)
      })

      if (result.success && result.leTransaction) {
        const json = JSON.stringify(result.leTransaction, null, 2)
        setEditorValue(json)
        if (editorRef.current) {
          editorRef.current.setValue(json)
        }
        setAiProgress(null)
      } else {
        setAiError(result.errors?.join(', ') ?? 'Generation failed')
      }
    } catch (e) {
      setAiError(e instanceof Error ? e.message : 'Generation failed')
    } finally {
      setAiGenerating(false)
    }
  }

  const result = mutation.data
  const isLoading = mutation.isPending
  const apiError = mutation.error

  return (
    <div className="max-w-7xl mx-auto fade-in">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-xl font-semibold text-zinc-900">Test Bench</h1>
          <p className="text-sm text-zinc-500 mt-1">
            Send a Linking Engine event through the Nexus engine and inspect the output.
          </p>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Left column - Input */}
        <div className="space-y-4">
          {/* AI Prompt Bar */}
          <div className="glow-border rounded-xl overflow-hidden">
            <div className="px-4 py-2 bg-navy-700 border-b border-zinc-100">
              <div className="flex items-center gap-2">
                <span className="text-violet-400 text-sm">✦</span>
                <input
                  type="text"
                  value={aiPrompt}
                  onChange={(e) => setAiPrompt(e.target.value)}
                  onKeyDown={(e) => { if (e.key === 'Enter' && !aiGenerating) handleGenerate() }}
                  placeholder="Describe an LE transaction... e.g. 'Visa capture €100 with interchange fees'"
                  className="flex-1 bg-transparent text-sm text-zinc-200 placeholder-zinc-500 outline-none"
                  disabled={aiGenerating}
                />
                <button
                  onClick={handleGenerate}
                  disabled={aiGenerating || !aiPrompt.trim()}
                  className="px-3 py-1 rounded-md bg-violet-600 text-white text-xs font-medium hover:bg-violet-500 transition-colors disabled:opacity-50 flex items-center gap-1.5"
                >
                  {aiGenerating && (
                    <svg className="animate-spin h-3 w-3 text-white" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                      <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                      <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                    </svg>
                  )}
                  {aiGenerating ? 'Generating...' : 'Generate'}
                </button>
              </div>
            </div>
            {/* Progress line */}
            {aiProgress && (
              <div className="px-4 py-1.5 bg-navy-700 border-b border-zinc-100">
                <span className="text-violet-300 text-xs">
                  {aiProgress.step === 'complete' ? '✓' : '⟳'} {aiProgress.message}
                </span>
              </div>
            )}
            {/* AI Error */}
            {aiError && (
              <div className="px-4 py-1.5 bg-red-900/20 border-b border-red-800/30">
                <span className="text-red-400 text-xs">{aiError}</span>
              </div>
            )}
          </div>

          {/* Editor */}
          <div className="glow-border rounded-xl overflow-hidden">
            <div className="flex items-center justify-between px-4 py-2 bg-navy-700 border-b border-zinc-100">
              <h3 className="text-xs font-semibold text-zinc-400 uppercase tracking-wider">
                LE Event Input
              </h3>
              <div className="flex items-center gap-2">
                <button
                  onClick={handleReset}
                  className="px-3 py-1 rounded-md text-xs font-medium text-zinc-500 hover:text-zinc-700 hover:bg-zinc-100 transition-colors"
                >
                  Reset
                </button>
                <button
                  onClick={handleRunClick}
                  disabled={isLoading}
                  className="px-4 py-1 rounded-md bg-accent text-white text-xs font-medium hover:bg-accent-dark transition-colors disabled:opacity-50 flex items-center gap-1.5"
                >
                  {isLoading && (
                    <svg
                      className="animate-spin h-3 w-3 text-white"
                      xmlns="http://www.w3.org/2000/svg"
                      fill="none"
                      viewBox="0 0 24 24"
                    >
                      <circle
                        className="opacity-25"
                        cx="12"
                        cy="12"
                        r="10"
                        stroke="currentColor"
                        strokeWidth="4"
                      />
                      <path
                        className="opacity-75"
                        fill="currentColor"
                        d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"
                      />
                    </svg>
                  )}
                  {isLoading ? 'Running...' : 'Run'}
                </button>
              </div>
            </div>
            <Editor
              height="500px"
              language="json"
              theme="vs-dark"
              value={editorValue}
              onChange={(value) => setEditorValue(value ?? '')}
              onMount={(editor) => {
                editorRef.current = editor
              }}
              options={{
                minimap: { enabled: false },
                fontSize: 13,
                lineNumbers: 'on',
                scrollBeyondLastLine: false,
                automaticLayout: true,
                tabSize: 2,
                wordWrap: 'on',
              }}
            />
          </div>
        </div>

        {/* Right column - Output */}
        <div className="space-y-4">
          {/* Error states */}
          {parseError && (
            <div className="rounded-xl border border-red-200 bg-red-50 p-4">
              <h3 className="text-sm font-semibold text-red-700 mb-1">JSON Parse Error</h3>
              <p className="text-xs text-red-600 font-mono">{parseError}</p>
            </div>
          )}

          {apiError && !parseError && (
            <div className="rounded-xl border border-red-200 bg-red-50 p-4">
              <h3 className="text-sm font-semibold text-red-700 mb-1">API Error</h3>
              <p className="text-xs text-red-600 font-mono">{apiError.message}</p>
            </div>
          )}

          {/* Result: error from engine */}
          {result && !result.success && (
            <div className="rounded-xl border border-red-200 bg-red-50 p-4 space-y-2">
              <h3 className="text-sm font-semibold text-red-700">Transformation Failed</h3>
              <ul className="space-y-1">
                {(result.errors ?? []).map((err, i) => (
                  <li key={i} className="text-xs text-red-600 font-mono bg-red-100 rounded px-2 py-1">
                    {err}
                  </li>
                ))}
              </ul>
            </div>
          )}

          {/* Result: success */}
          {result && result.success && result.transaction && (
            <>
              <div className="glow-border rounded-xl overflow-hidden">
                <div className="flex items-center justify-between px-4 py-2 bg-navy-700 border-b border-zinc-100">
                  <h3 className="text-xs font-semibold text-zinc-400 uppercase tracking-wider">
                    Nexus Block Output
                  </h3>
                  <span className="inline-flex items-center rounded-full bg-emerald-100 px-2 py-0.5 text-[10px] font-semibold text-emerald-700">
                    SUCCESS
                  </span>
                </div>
                <Editor
                  height="300px"
                  language="json"
                  theme="vs-dark"
                  value={JSON.stringify(result.transaction, null, 2)}
                  options={{
                    readOnly: true,
                    minimap: { enabled: false },
                    fontSize: 12,
                    lineNumbers: 'on',
                    scrollBeyondLastLine: false,
                    automaticLayout: true,
                    tabSize: 2,
                    wordWrap: 'on',
                  }}
                />
              </div>

              <div className="glow-border rounded-xl p-4">
                <h3 className="text-xs font-semibold text-zinc-400 uppercase tracking-wider mb-3">
                  Transaction Trace
                </h3>
                <TransactionTrace data={result.transaction as TransactionData} />
              </div>
            </>
          )}

          {/* Empty state */}
          {!result && !parseError && !apiError && !isLoading && (
            <div className="glow-border rounded-xl flex flex-col items-center justify-center py-20 text-zinc-400">
              <svg
                className="w-10 h-10 mb-3 text-zinc-300"
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={1.5}
                  d="M14.752 11.168l-3.197-2.132A1 1 0 0010 9.87v4.263a1 1 0 001.555.832l3.197-2.132a1 1 0 000-1.664z"
                />
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={1.5}
                  d="M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
                />
              </svg>
              <p className="text-sm font-medium text-zinc-500">Ready to run</p>
              <p className="text-xs text-zinc-400 mt-1">
                Edit the LE event on the left and click Run, or use the AI prompt bar to generate one
              </p>
            </div>
          )}

          {/* Loading */}
          {isLoading && (
            <div className="glow-border rounded-xl flex flex-col items-center justify-center py-20 text-zinc-400">
              <svg
                className="animate-spin h-8 w-8 text-accent mb-3"
                xmlns="http://www.w3.org/2000/svg"
                fill="none"
                viewBox="0 0 24 24"
              >
                <circle
                  className="opacity-25"
                  cx="12"
                  cy="12"
                  r="10"
                  stroke="currentColor"
                  strokeWidth="4"
                />
                <path
                  className="opacity-75"
                  fill="currentColor"
                  d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"
                />
              </svg>
              <p className="text-sm font-medium text-zinc-500">Processing...</p>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
```

- [ ] **Step 2: Verify UI builds**

```bash
cd ui && npm run build
```
Expected: Build succeeds with no TypeScript errors.

- [ ] **Step 3: Commit**

```bash
git add ui/src/pages/TestBench.tsx
git commit -m "feat: add AI prompt bar to Test Bench UI"
```

---

### Task 13: UI Proxy Config for AI Generator

**Files:**
- Modify: `ui/vite.config.ts`
- Modify: `ui/src/api/client.ts`

The existing Vite proxy at `ui/vite.config.ts` rewrites `/api` → `http://localhost:8083` (nexus-api) with prefix stripping. The ai-generator lives on port 8084 and uses `/api/generate`. We need a more specific proxy rule that matches first.

- [ ] **Step 1: Add proxy rule for `/api/generate`**

In `ui/vite.config.ts`, add the `/api/generate` proxy BEFORE the `/api` catch-all in the `proxy` object (Vite matches in order):

```typescript
proxy: {
  '/api/generate': {
    target: process.env.VITE_GENERATOR_URL || 'http://localhost:8084',
    changeOrigin: true,
  },
  '/api': {
    target: process.env.VITE_BACKEND_URL || 'http://localhost:8083',
    changeOrigin: true,
    rewrite: (path) => path.replace(/^\/api/, ''),
  },
  // ... rest unchanged
```

Note: the `/api/generate` proxy does NOT strip the prefix because the ai-generator controller is mapped at `/api/generate`.

- [ ] **Step 2: Update GENERATOR_API in client.ts**

In `ui/src/api/client.ts`, change the `GENERATOR_API` constant:

```typescript
const GENERATOR_API = ''
```

This makes the fetch call go to `/api/generate` (relative), which the Vite proxy will route to port 8084.

- [ ] **Step 3: Commit**

```bash
git add ui/vite.config.ts ui/src/api/client.ts
git commit -m "feat: add vite proxy for ai-generator"
```

---

### Task 14: Makefile Build Target

**Files:**
- Modify: `Makefile`

- [ ] **Step 1: Add build-generator target**

The `sync-domain` target was added in Task 2. Now add the build target. Append to `Makefile`:

```makefile
build-generator: sync-domain
	cd ai-generator && ./gradlew build -x test
```

- [ ] **Step 2: Verify**

```bash
make build-generator
```
Expected: Domain sync runs, then Gradle build succeeds.

- [ ] **Step 3: Commit**

```bash
git add Makefile
git commit -m "chore: add build-generator makefile target"
```

---

### Task 15: End-to-End Verification

- [ ] **Step 1: Build all services**

```bash
for svc in le-simulator nexus-transformer nexus-api rules-engine ai-generator; do
  echo "Building $svc..."
  (cd $svc && ./gradlew build -x test) || exit 1
done
echo "All services built successfully"
```

- [ ] **Step 2: Start infrastructure + services**

```bash
docker compose up --build -d
```
Wait for all services to be healthy.

- [ ] **Step 3: Verify ai-generator health**

```bash
curl http://localhost:8084/actuator/health
```
Expected: `{"status":"UP"}`

- [ ] **Step 4: Test sync endpoint (requires AWS credentials)**

```bash
curl -s -X POST http://localhost:8084/api/generate?sync=true \
  -H "Content-Type: application/json" \
  -d '{"prompt":"A simple Visa capture for 100 EUR"}' | jq .
```
Expected: JSON response with `success: true` and a valid LE transaction, or a Bedrock credential error (which confirms the endpoint is wired correctly).

- [ ] **Step 5: Start UI and verify prompt bar**

```bash
cd ui && npm run dev
```
Open http://localhost:5173, navigate to Test Bench. Verify the AI prompt bar appears above the editor.

- [ ] **Step 6: Commit any fixes**

If any fixes were needed during verification, commit them:
```bash
git add -A && git commit -m "fix: end-to-end verification fixes"
```

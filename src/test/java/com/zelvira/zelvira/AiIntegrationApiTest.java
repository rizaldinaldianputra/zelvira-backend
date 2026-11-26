package com.zelvira.zelvira;

import com.zelvira.zelvira.dto.ai.AiPageResponse;
import com.zelvira.zelvira.dto.ai.AiProductResponse;
import com.zelvira.zelvira.service.AiIntegrationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.ai.hermes.ceo-key=test-ceo-key",
        "app.ai.hermes.content-key=test-content-key",
        "app.ai.hermes.marketing-key=test-marketing-key",
        "app.ai.hermes.seo-key=test-seo-key",
        "app.ai.hermes.admin-key=test-admin-key",
        "app.ai.hermes.rate-limit-per-minute=100"
})
class AiIntegrationApiTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AiIntegrationService aiIntegrationService;

    @Test
    void shouldRejectMissingApiKey() throws Exception {
        mockMvc.perform(get("/api/v1/ai/health"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void shouldRejectInvalidApiKey() throws Exception {
        mockMvc.perform(get("/api/v1/ai/health")
                        .header("X-AI-API-Key", "wrong-key"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void shouldRejectWriteOperations() throws Exception {
        mockMvc.perform(post("/api/v1/ai/products")
                        .header("X-AI-API-Key", "test-admin-key"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void shouldAllowCeoHealthAndDashboard() throws Exception {
        when(aiIntegrationService.getDashboardSummary())
                .thenReturn(Map.of("totalProducts", 10L, "activeProducts", 8L));

        mockMvc.perform(get("/api/v1/ai/health")
                        .header("X-AI-API-Key", "test-ceo-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.profile").value("hermes-ceo"))
                .andExpect(jsonPath("$.data.mode").value("READ_ONLY"));

        mockMvc.perform(get("/api/v1/ai/dashboard/summary")
                        .header("X-AI-API-Key", "test-ceo-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalProducts").value(10));
    }

    @Test
    void shouldEnforceRolePermissions() throws Exception {
        mockMvc.perform(get("/api/v1/ai/dashboard/summary")
                        .header("X-AI-API-Key", "test-seo-key"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(get("/api/v1/ai/sales/summary")
                        .header("X-AI-API-Key", "test-content-key"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void shouldReturnProductsWithPaginationAndSearch() throws Exception {
        AiProductResponse product = AiProductResponse.builder()
                .id(1L)
                .name("Pashmina Zelvira")
                .sku("PAS-001")
                .status("active")
                .price(AiProductResponse.PriceSummary.builder()
                        .normal(new BigDecimal("150000"))
                        .selling(new BigDecimal("135000"))
                        .promo(new BigDecimal("135000"))
                        .currency("IDR")
                        .build())
                .build();

        AiPageResponse<AiProductResponse> page = AiPageResponse.<AiProductResponse>builder()
                .items(List.of(product))
                .page(0)
                .limit(20)
                .totalItems(1)
                .totalPages(1)
                .build();

        when(aiIntegrationService.getProducts(anyInt(), anyInt(), anyString(), anyString(), any(), any(), any(), any(), any()))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/ai/products/search?q=Pashmina")
                        .header("X-AI-API-Key", "test-marketing-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items[0].name").value("Pashmina Zelvira"))
                .andExpect(jsonPath("$.data.items[0].price.currency").value("IDR"));
    }

    @Test
    void shouldReturn404WhenProductNotFound() throws Exception {
        when(aiIntegrationService.getProductById(999L))
                .thenThrow(new NoSuchElementException("Product not found with id: 999"));

        mockMvc.perform(get("/api/v1/ai/products/999")
                        .header("X-AI-API-Key", "test-admin-key"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }
}

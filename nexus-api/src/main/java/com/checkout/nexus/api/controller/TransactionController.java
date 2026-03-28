package com.checkout.nexus.api.controller;

import com.checkout.nexus.api.service.EventStreamService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final EventStreamService eventStreamService;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @GetMapping
    public List<JsonNode> getRecent(@RequestParam(defaultValue = "50") int limit) {
        return eventStreamService.getRecentTransactions(limit).stream()
                .map(json -> {
                    try {
                        return MAPPER.readTree(json);
                    } catch (Exception e) {
                        return MAPPER.createObjectNode().put("raw", json);
                    }
                })
                .collect(Collectors.toList());
    }
}

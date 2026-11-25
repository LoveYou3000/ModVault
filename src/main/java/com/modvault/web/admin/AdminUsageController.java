package com.modvault.web.admin;

import com.modvault.dal.entity.UsageRecord;
import com.modvault.service.UsageService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/api/v1/usage")
public class AdminUsageController {

    private final UsageService usageService;

    public AdminUsageController(UsageService usageService) {
        this.usageService = usageService;
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam(value = "limit", defaultValue = "100") int limit,
                                    @RequestParam(value = "offset", defaultValue = "0") int offset) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        int safeOffset = Math.max(0, offset);
        List<UsageRecord> items = usageService.findPage(safeLimit, safeOffset);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("total", usageService.count());
        response.put("limit", safeLimit);
        response.put("offset", safeOffset);
        response.put("items", items);
        return response;
    }
}

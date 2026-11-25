package com.modvault.service;

import com.modvault.common.ApiException;
import com.modvault.dal.entity.ApiKey;
import com.modvault.dal.entity.ModelMapping;
import com.modvault.dal.entity.Upstream;
import com.modvault.metrics.ModVaultMetrics;
import com.modvault.translate.RequestConversionResult;
import com.modvault.translate.RequestConverter;
import com.modvault.translate.ResponseConverter;
import com.modvault.translate.TokenEstimator;
import com.modvault.translate.model.AnthropicMessageRequest;
import com.modvault.translate.model.AnthropicMessageResponse;
import com.modvault.translate.model.OpenAiChatResponse;
import com.modvault.upstream.OpenAiClient;
import com.modvault.upstream.UpstreamErrorMapper;
import com.modvault.upstream.UpstreamException;
import com.modvault.upstream.UpstreamHttpException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates the non-streaming Anthropic -&gt; OpenAI -&gt; Anthropic flow.
 */
@Service
public class MessageService {

    private static final Logger log = LoggerFactory.getLogger(MessageService.class);

    private final ModelMappingService mappingService;
    private final UpstreamService upstreamService;
    private final OpenAiClient openAiClient;
    private final UpstreamErrorMapper errorMapper;
    private final UsageService usageService;
    private final ModVaultMetrics metrics;
    private final RequestConverter requestConverter = new RequestConverter();
    private final ResponseConverter responseConverter = new ResponseConverter();
    private final TokenEstimator tokenEstimator = new TokenEstimator();

    public MessageService(ModelMappingService mappingService, UpstreamService upstreamService,
                          OpenAiClient openAiClient, UpstreamErrorMapper errorMapper,
                          UsageService usageService, ModVaultMetrics metrics) {
        this.mappingService = mappingService;
        this.upstreamService = upstreamService;
        this.openAiClient = openAiClient;
        this.errorMapper = errorMapper;
        this.usageService = usageService;
        this.metrics = metrics;
    }

    public AnthropicMessageResponse messages(AnthropicMessageRequest req, ApiKey apiKey, String requestId) {
        long start = System.nanoTime();
        String anthropicModel = req.getModel();
        if (anthropicModel == null || anthropicModel.trim().isEmpty()) {
            throw ApiException.badRequest("model: field required");
        }
        if (Boolean.TRUE.equals(req.getStream())) {
            throw ApiException.badRequest("stream=true is not supported in this milestone (M1 non-streaming)");
        }
        if (req.getMaxTokens() == null) {
            throw ApiException.badRequest("max_tokens: field required");
        }

        ModelMapping mapping = mappingService.resolve(anthropicModel);
        if (mapping == null) {
            metrics.recordRequest("rejected", "unknown");
            throw ApiException.notFound("model not found: " + anthropicModel);
        }
        Upstream upstream = upstreamService.findById(mapping.getUpstreamId());
        if (upstream == null || !upstream.isEnabled()) {
            metrics.recordRequest("rejected", anthropicModel);
            throw ApiException.notFound("model not available: " + anthropicModel);
        }

        String upstreamKey;
        try {
            upstreamKey = upstreamService.resolvePlaintextKey(upstream);
        } catch (ApiException e) {
            metrics.recordRequest("failed", anthropicModel);
            record(requestId, apiKey, anthropicModel, upstream, mapping.getUpstreamModel(),
                    "failed", 0, 0, false, elapsedMs(start), null, e.errorType().wireValue());
            throw e;
        }

        RequestConversionResult conv = requestConverter.convert(req, mapping.getUpstreamModel(), upstream.getMaxTokensField());
        for (String warning : conv.warnings()) {
            log.warn("request_id={} {}", requestId, warning);
        }
        log.info("request_id={} convert_request_done anthropic_model={} upstream_model={} usage_source=estimated",
                requestId, anthropicModel, mapping.getUpstreamModel());

        OpenAiChatResponse upstreamResp;
        try {
            upstreamResp = openAiClient.chat(upstream, upstreamKey, conv.request());
        } catch (UpstreamHttpException e) {
            metrics.recordUpstreamStatus(e.statusCode());
            metrics.recordRequest("failed", anthropicModel);
            ApiException mapped = errorMapper.map(e);
            record(requestId, apiKey, anthropicModel, upstream, mapping.getUpstreamModel(),
                    "failed", conv.estimatedInputTokens(), 0, false, elapsedMs(start), null,
                    mapped.errorType().wireValue());
            log.warn("request_id={} upstream_error status={}", requestId, e.statusCode());
            throw mapped;
        } catch (UpstreamException e) {
            metrics.recordRequest("failed", anthropicModel);
            ApiException mapped = ApiException.overloaded("upstream unreachable or timed out");
            record(requestId, apiKey, anthropicModel, upstream, mapping.getUpstreamModel(),
                    "failed", conv.estimatedInputTokens(), 0, false, elapsedMs(start), null,
                    mapped.errorType().wireValue());
            log.warn("request_id={} upstream_error cause={}", requestId, e.getMessage());
            throw mapped;
        }

        List<String> responseWarnings = new ArrayList<>();
        AnthropicMessageResponse response = responseConverter.convert(upstreamResp, anthropicModel, responseWarnings);
        for (String warning : responseWarnings) {
            log.warn("request_id={} {}", requestId, warning);
        }

        int inputTokens = response.getUsage().getInputTokens();
        int outputTokens = response.getUsage().getOutputTokens();
        record(requestId, apiKey, anthropicModel, upstream, mapping.getUpstreamModel(),
                "success", inputTokens, outputTokens, false, elapsedMs(start), null, null);
        metrics.recordRequest("success", anthropicModel);
        metrics.recordLatency(anthropicModel, elapsedMs(start));
        log.info("request_id={} finish_reason={} status=success duration_ms={}",
                requestId, response.getStopReason(), elapsedMs(start));
        return response;
    }

    public int countTokens(AnthropicMessageRequest req) {
        if (req.getMessages() == null || req.getMessages().isEmpty()) {
            throw ApiException.badRequest("messages: field required");
        }
        return tokenEstimator.estimateInputTokens(req);
    }

    private void record(String requestId, ApiKey apiKey, String anthropicModel, Upstream upstream,
                        String upstreamModel, String status, int inputTokens, int outputTokens,
                        boolean stream, long latencyMs, Long firstTokenMs, String errorType) {
        usageService.record(
                requestId,
                apiKey != null ? apiKey.getId() : null,
                anthropicModel,
                upstream != null ? upstream.getId() : null,
                upstreamModel,
                status,
                inputTokens,
                outputTokens,
                stream,
                latencyMs,
                firstTokenMs,
                errorType);
    }

    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }
}

package com.modvault.service;

import com.modvault.common.ApiException;
import com.modvault.dal.entity.ApiKey;
import com.modvault.dal.entity.ModelMapping;
import com.modvault.dal.entity.Upstream;
import com.modvault.metrics.ModVaultMetrics;
import com.modvault.translate.AnthropicEvent;
import com.modvault.translate.RequestConversionResult;
import com.modvault.translate.RequestConverter;
import com.modvault.translate.ResponseConverter;
import com.modvault.translate.StreamingConverter;
import com.modvault.translate.TokenEstimator;
import com.modvault.translate.model.AnthropicMessageRequest;
import com.modvault.translate.model.AnthropicMessageResponse;
import com.modvault.translate.model.OpenAiChatResponse;
import com.modvault.translate.model.OpenAiChunk;
import com.modvault.upstream.OpenAiClient;
import com.modvault.upstream.UpstreamErrorMapper;
import com.modvault.upstream.UpstreamException;
import com.modvault.upstream.UpstreamHttpException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates the Anthropic -&gt; OpenAI -&gt; Anthropic flow (non-streaming and streaming).
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
        String anthropicModel = requireModel(req);
        if (req.getMaxTokens() == null) {
            throw ApiException.badRequest("max_tokens: field required");
        }

        ResolvedTarget target = resolve(anthropicModel);
        RequestConversionResult conv = requestConverter.convert(req, target.mapping.getUpstreamModel(),
                target.upstream.getMaxTokensField());
        logWarnings(requestId, conv.warnings());
        log.info("request_id={} convert_request_done anthropic_model={} upstream_model={} usage_source=estimated",
                requestId, anthropicModel, target.mapping.getUpstreamModel());

        OpenAiChatResponse upstreamResp;
        try {
            upstreamResp = openAiClient.chat(target.upstream, target.upstreamKey, conv.request());
        } catch (UpstreamHttpException e) {
            metrics.recordUpstreamStatus(e.statusCode());
            metrics.recordRequest("failed", anthropicModel);
            ApiException mapped = errorMapper.map(e);
            record(requestId, apiKey, anthropicModel, target, "failed", conv.estimatedInputTokens(), 0,
                    false, elapsedMs(start), null, mapped.errorType().wireValue());
            log.warn("request_id={} upstream_error status={}", requestId, e.statusCode());
            throw mapped;
        } catch (UpstreamException e) {
            metrics.recordRequest("failed", anthropicModel);
            ApiException mapped = ApiException.overloaded("upstream unreachable or timed out");
            record(requestId, apiKey, anthropicModel, target, "failed", conv.estimatedInputTokens(), 0,
                    false, elapsedMs(start), null, mapped.errorType().wireValue());
            log.warn("request_id={} upstream_error cause={}", requestId, e.getMessage());
            throw mapped;
        }

        List<String> responseWarnings = new ArrayList<>();
        AnthropicMessageResponse response = responseConverter.convert(upstreamResp, anthropicModel, responseWarnings);
        logWarnings(requestId, responseWarnings);

        int inputTokens = response.getUsage().getInputTokens();
        int outputTokens = response.getUsage().getOutputTokens();
        record(requestId, apiKey, anthropicModel, target, "success", inputTokens, outputTokens,
                false, elapsedMs(start), null, null);
        metrics.recordRequest("success", anthropicModel);
        metrics.recordLatency(anthropicModel, elapsedMs(start));
        log.info("request_id={} finish_reason={} status=success duration_ms={}",
                requestId, response.getStopReason(), elapsedMs(start));
        return response;
    }

    /**
     * Streaming path: writes Anthropic SSE events directly to the response. Resolves the
     * target and opens the upstream stream before writing any SSE header, so upstream
     * non-2xx / connect failures still surface as a plain Anthropic JSON error.
     */
    public void streamMessages(AnthropicMessageRequest req, ApiKey apiKey, String requestId,
                               HttpServletResponse response) throws IOException {
        long start = System.nanoTime();
        String anthropicModel = requireModel(req);
        if (req.getMaxTokens() == null) {
            throw ApiException.badRequest("max_tokens: field required");
        }

        ResolvedTarget target = resolve(anthropicModel);
        RequestConversionResult conv = requestConverter.convert(req, target.mapping.getUpstreamModel(),
                target.upstream.getMaxTokensField());
        logWarnings(requestId, conv.warnings());
        log.info("request_id={} convert_request_done anthropic_model={} upstream_model={} stream=true",
                requestId, anthropicModel, target.mapping.getUpstreamModel());

        List<String> streamWarnings = new ArrayList<>();
        StreamingConverter converter = new StreamingConverter(anthropicModel, conv.estimatedInputTokens(), streamWarnings);

        // Open upstream (throws before any SSE header is written on failure).
        OpenAiClient.StreamingConnection connection;
        try {
            connection = openAiClient.openStream(target.upstream, target.upstreamKey, conv.request());
        } catch (UpstreamHttpException e) {
            metrics.recordUpstreamStatus(e.statusCode());
            metrics.recordRequest("failed", anthropicModel);
            ApiException mapped = errorMapper.map(e);
            record(requestId, apiKey, anthropicModel, target, "failed", conv.estimatedInputTokens(), 0,
                    true, elapsedMs(start), null, mapped.errorType().wireValue());
            log.warn("request_id={} upstream_error status={}", requestId, e.statusCode());
            throw mapped;
        } catch (UpstreamException e) {
            metrics.recordRequest("failed", anthropicModel);
            ApiException mapped = ApiException.overloaded("upstream unreachable or timed out");
            record(requestId, apiKey, anthropicModel, target, "failed", conv.estimatedInputTokens(), 0,
                    true, elapsedMs(start), null, mapped.errorType().wireValue());
            throw mapped;
        }

        metrics.incStreamActive();
        response.setStatus(200);
        response.setContentType("text/event-stream");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        PrintWriter writer = response.getWriter();

        long firstTokenMs = -1;
        int outputTokens = 0;
        try (OpenAiClient.StreamingConnection conn = connection) {
            OpenAiChunk chunk;
            boolean disconnected = false;
            while ((chunk = conn.readNextChunk()) != null) {
                if (chunk.getUsage() != null) {
                    outputTokens = chunk.getUsage().getCompletionTokens();
                }
                for (AnthropicEvent event : converter.onChunk(chunk)) {
                    if (firstTokenMs < 0) {
                        firstTokenMs = elapsedMs(start);
                    }
                    writer.write(event.toSse());
                    writer.flush();
                }
                if (writer.checkError()) {
                    disconnected = true;
                    break;
                }
            }
            if (disconnected) {
                record(requestId, apiKey, anthropicModel, target, "failed", conv.estimatedInputTokens(),
                        outputTokens, true, elapsedMs(start), firstTokenMs < 0 ? null : firstTokenMs,
                        "client_disconnected");
                log.info("request_id={} client_disconnected", requestId);
                return;
            }
            for (AnthropicEvent event : converter.finalizeWithoutUsage()) {
                writer.write(event.toSse());
            }
            writer.flush();
            logWarnings(requestId, streamWarnings);
            record(requestId, apiKey, anthropicModel, target, "success", conv.estimatedInputTokens(),
                    outputTokens, true, elapsedMs(start), firstTokenMs < 0 ? null : firstTokenMs, null);
            metrics.recordRequest("success", anthropicModel);
            log.info("request_id={} status=success stream=true duration_ms={} first_token_ms={}",
                    requestId, elapsedMs(start), firstTokenMs);
        } catch (UpstreamException e) {
            // Mid-stream failure: close open blocks, then an error event (FR-14).
            for (AnthropicEvent event : converter.abort()) {
                writer.write(event.toSse());
            }
            writer.write(StreamingConverter.errorEvent("overloaded_error", "upstream stream failed").toSse());
            writer.flush();
            record(requestId, apiKey, anthropicModel, target, "failed", conv.estimatedInputTokens(),
                    outputTokens, true, elapsedMs(start), firstTokenMs < 0 ? null : firstTokenMs, "overloaded_error");
            metrics.recordRequest("failed", anthropicModel);
            log.warn("request_id={} upstream_stream_failed cause={}", requestId, e.getMessage());
        } finally {
            metrics.decStreamActive();
        }
    }

    public int countTokens(AnthropicMessageRequest req) {
        if (req.getMessages() == null || req.getMessages().isEmpty()) {
            throw ApiException.badRequest("messages: field required");
        }
        return tokenEstimator.estimateInputTokens(req);
    }

    private String requireModel(AnthropicMessageRequest req) {
        if (req.getModel() == null || req.getModel().trim().isEmpty()) {
            throw ApiException.badRequest("model: field required");
        }
        return req.getModel();
    }

    private ResolvedTarget resolve(String anthropicModel) {
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
        try {
            String upstreamKey = upstreamService.resolvePlaintextKey(upstream);
            return new ResolvedTarget(mapping, upstream, upstreamKey);
        } catch (ApiException e) {
            metrics.recordRequest("failed", anthropicModel);
            throw e;
        }
    }

    private void logWarnings(String requestId, List<String> warnings) {
        for (String warning : warnings) {
            log.warn("request_id={} {}", requestId, warning);
        }
    }

    private void record(String requestId, ApiKey apiKey, String anthropicModel, ResolvedTarget target,
                        String status, int inputTokens, int outputTokens, boolean stream,
                        long latencyMs, Long firstTokenMs, String errorType) {
        usageService.record(
                requestId,
                apiKey != null ? apiKey.getId() : null,
                anthropicModel,
                target != null ? target.upstream.getId() : null,
                target != null ? target.mapping.getUpstreamModel() : null,
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

    private static final class ResolvedTarget {
        final ModelMapping mapping;
        final Upstream upstream;
        final String upstreamKey;

        ResolvedTarget(ModelMapping mapping, Upstream upstream, String upstreamKey) {
            this.mapping = mapping;
            this.upstream = upstream;
            this.upstreamKey = upstreamKey;
        }
    }
}

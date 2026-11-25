package com.modvault.upstream;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modvault.config.ModVaultProperties;
import com.modvault.dal.entity.Upstream;
import com.modvault.translate.model.OpenAiChatRequest;
import com.modvault.translate.model.OpenAiChatResponse;
import com.modvault.translate.model.OpenAiChunk;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * OpenAI-compatible upstream caller (OkHttp). Joins the base_url correctly (A-17) and
 * merges configured extra headers with the upstream bearer token.
 */
@Component
public class OpenAiClient {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final ModVaultProperties props;
    private final OkHttpClient baseClient;
    private final ObjectMapper upstreamMapper;
    private final ObjectMapper headerMapper;

    public OpenAiClient(ModVaultProperties props) {
        this.props = props;
        this.baseClient = new OkHttpClient.Builder()
                .retryOnConnectionFailure(false)
                .build();
        this.upstreamMapper = new ObjectMapper()
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
        this.headerMapper = new ObjectMapper();
    }

    public OpenAiChatResponse chat(Upstream upstream, String apiKey, OpenAiChatRequest request) {
        String url = buildUrl(upstream.getBaseUrl(), props.isAutoAppendV1());
        String body;
        try {
            body = upstreamMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new UpstreamException("failed to serialize upstream request", e);
        }

        Request.Builder builder = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(body, JSON));

        mergeExtraHeaders(builder, upstream.getExtraHeaders());

        int connectTimeout = upstream.getConnectTimeoutMs() != null
                ? upstream.getConnectTimeoutMs() : props.getDefaultConnectTimeoutMs();
        int readTimeout = upstream.getReadTimeoutMs() != null
                ? upstream.getReadTimeoutMs() : props.getDefaultReadTimeoutMs();

        OkHttpClient client = baseClient.newBuilder()
                .connectTimeout(connectTimeout, TimeUnit.MILLISECONDS)
                .readTimeout(readTimeout, TimeUnit.MILLISECONDS)
                .build();

        try (Response response = client.newCall(builder.build()).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new UpstreamHttpException(response.code(), responseBody);
            }
            try {
                return upstreamMapper.readValue(responseBody, OpenAiChatResponse.class);
            } catch (IOException e) {
                throw new UpstreamException("failed to parse upstream response", e);
            }
        } catch (IOException e) {
            throw new UpstreamException("upstream connection failed: " + e.getMessage(), e);
        }
    }

    /**
     * Opens a streaming upstream call. Throws {@link UpstreamHttpException} on non-2xx and
     * {@link UpstreamException} on transport failure — both before the caller writes any
     * SSE response headers.
     */
    public StreamingConnection openStream(Upstream upstream, String apiKey, OpenAiChatRequest request) {
        String url = buildUrl(upstream.getBaseUrl(), props.isAutoAppendV1());
        String body;
        try {
            body = upstreamMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new UpstreamException("failed to serialize upstream request", e);
        }

        Request.Builder builder = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(body, JSON));
        mergeExtraHeaders(builder, upstream.getExtraHeaders());

        int connectTimeout = upstream.getConnectTimeoutMs() != null
                ? upstream.getConnectTimeoutMs() : props.getDefaultConnectTimeoutMs();
        int readTimeout = upstream.getReadTimeoutMs() != null
                ? upstream.getReadTimeoutMs() : props.getDefaultReadTimeoutMs();

        OkHttpClient client = baseClient.newBuilder()
                .connectTimeout(connectTimeout, TimeUnit.MILLISECONDS)
                .readTimeout(readTimeout, TimeUnit.MILLISECONDS)
                .build();

        try {
            Response response = client.newCall(builder.build()).execute();
            if (!response.isSuccessful()) {
                int code = response.code();
                String responseBody = response.body() != null ? response.body().string() : "";
                response.close();
                throw new UpstreamHttpException(code, responseBody);
            }
            return new StreamingConnection(response);
        } catch (IOException e) {
            throw new UpstreamException("upstream connection failed: " + e.getMessage(), e);
        }
    }

    /**
     * Streaming SSE reader over an open upstream response. Yields one parsed chunk per
     * call; returns {@code null} at {@code data: [DONE]} or EOF.
     */
    public static class StreamingConnection implements Closeable {

        private final Response response;
        private final BufferedReader reader;
        private final ObjectMapper mapper = new ObjectMapper();

        StreamingConnection(Response response) {
            this.response = response;
            this.reader = new BufferedReader(
                    new InputStreamReader(response.body().byteStream(), StandardCharsets.UTF_8));
        }

        public OpenAiChunk readNextChunk() {
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty() || !line.startsWith("data:")) {
                        continue;
                    }
                    String data = line.substring("data:".length()).trim();
                    if (data.isEmpty()) {
                        continue;
                    }
                    if ("[DONE]".equals(data)) {
                        return null;
                    }
                    try {
                        return mapper.readValue(data, OpenAiChunk.class);
                    } catch (JsonProcessingException e) {
                        throw new UpstreamException("failed to parse upstream chunk", e);
                    }
                }
                return null;
            } catch (IOException e) {
                throw new UpstreamException("upstream stream failed: " + e.getMessage(), e);
            }
        }

        @Override
        public void close() {
            response.close();
        }
    }

    /**
     * A.17: append {@code /chat/completions}, adding {@code /v1} only when the base_url
     * is a bare origin (no path) and {@code auto_append_v1} is enabled.
     */
    static String buildUrl(String baseUrl, boolean autoAppendV1) {
        String base = baseUrl == null ? "" : baseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.endsWith("/v1")) {
            return base + "/chat/completions";
        }
        String path = pathOf(base);
        boolean hasPath = path != null && path.length() > 1;
        if (hasPath) {
            return base + "/chat/completions";
        }
        if (autoAppendV1) {
            return base + "/v1/chat/completions";
        }
        return base + "/chat/completions";
    }

    private static String pathOf(String url) {
        int scheme = url.indexOf("://");
        if (scheme < 0) {
            return null;
        }
        int slash = url.indexOf('/', scheme + 3);
        if (slash < 0) {
            return null;
        }
        return url.substring(slash);
    }

    private void mergeExtraHeaders(Request.Builder builder, String extraHeadersJson) {
        if (extraHeadersJson == null || extraHeadersJson.isEmpty()) {
            return;
        }
        try {
            JsonNode node = headerMapper.readTree(extraHeadersJson);
            if (node == null || !node.isObject()) {
                return;
            }
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> e = fields.next();
                JsonNode value = e.getValue();
                if (value == null || value.isNull()) {
                    continue;
                }
                builder.header(e.getKey(), value.isTextual() ? value.textValue() : value.toString());
            }
        } catch (JsonProcessingException ignored) {
            // invalid stored JSON is ignored; admin API validates on write
        }
    }
}

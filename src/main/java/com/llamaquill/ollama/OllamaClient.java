package com.llamaquill.ollama;

import com.llamaquill.model.GenerationSettings;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class OllamaClient
{
    public static final String DEFAULT_HOST = "http://localhost:11434";
    public static final String DEFAULT_MODEL = "hf.co/LatitudeGames/Muse-12B-GGUF:BF16";

    private final HttpClient client;
    private final String host;
    private final String model;

    public OllamaClient()
    {
        this(DEFAULT_HOST, DEFAULT_MODEL);
    }

    public OllamaClient(String host, String model)
    {
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.host = host;
        this.model = model;
    }

    public String generate(String prompt, GenerationSettings settings)
            throws IOException, InterruptedException
    {
        String payload = buildPayload(prompt, settings);
        System.out.println("Ollama payload:");
        System.out.println(payload);
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(host + "/api/generate"))
                .timeout(Duration.ofMinutes(2)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8)).build();

        HttpResponse<java.io.InputStream> response = client.send(request,
                HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300)
        {
            throw new IOException("Ollama returned status " + response.statusCode());
        }

        StringBuilder sb = new StringBuilder();
        String doneLine = null;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(response.body(), StandardCharsets.UTF_8)))
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                String chunk = Json.extractStringField(line, "response");
                if (chunk != null)
                {
                    sb.append(chunk);
                }
                if (Json.isDone(line))
                {
                    doneLine = line;
                    break;
                }
            }
        }
        if (doneLine != null)
        {
            System.out.println("Ollama final response:");
            System.out.println(doneLine);
        }
        return sb.toString();
    }

    private String buildPayload(String prompt, GenerationSettings settings)
    {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        sb.append("\"model\":\"").append(Json.escape(model)).append("\"");
        sb.append(",\"prompt\":\"").append(Json.escape(prompt)).append("\"");
        sb.append(",\"raw\":true");
        sb.append(",\"stream\":true");
        sb.append(",\"options\":{");
        sb.append("\"num_ctx\":").append(settings.contextLimit());
        sb.append(",\"temperature\":").append(settings.temperature());
        sb.append(",\"top_k\":").append(settings.topK());
        sb.append(",\"top_p\":").append(settings.topP());
        sb.append(",\"presence_penalty\":").append(settings.presencePenalty());
        sb.append(",\"frequency_penalty\":").append(settings.frequencyPenalty());
        sb.append(",\"num_predict\":").append(settings.responseLength());
        sb.append('}');
        sb.append('}');
        return sb.toString();
    }

    private static final class Json
    {
        private Json()
        {
        }

        static String escape(String value)
        {
            if (value == null)
            {
                return "";
            }
            StringBuilder sb = new StringBuilder(value.length() + 16);
            for (int i = 0; i < value.length(); i++)
            {
                char c = value.charAt(i);
                switch (c)
                {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
                }
            }
            return sb.toString();
        }

        static String extractStringField(String jsonLine, String field)
        {
            String key = "\"" + field + "\"";
            int idx = jsonLine.indexOf(key);
            if (idx < 0)
            {
                return null;
            }
            int colon = jsonLine.indexOf(':', idx + key.length());
            if (colon < 0)
            {
                return null;
            }
            int start = jsonLine.indexOf('"', colon + 1);
            if (start < 0)
            {
                return null;
            }
            int i = start + 1;
            StringBuilder sb = new StringBuilder();
            boolean escaping = false;
            while (i < jsonLine.length())
            {
                char c = jsonLine.charAt(i++);
                if (escaping)
                {
                    switch (c)
                    {
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case 'u' -> {
                        if (i + 3 < jsonLine.length())
                        {
                            String hex = jsonLine.substring(i, i + 4);
                            try
                            {
                                sb.append((char) Integer.parseInt(hex, 16));
                            }
                            catch (NumberFormatException ignored)
                            {
                                sb.append("\\u").append(hex);
                            }
                            i += 4;
                        }
                    }
                    default -> sb.append(c);
                    }
                    escaping = false;
                    continue;
                }
                if (c == '\\')
                {
                    escaping = true;
                    continue;
                }
                if (c == '"')
                {
                    break;
                }
                sb.append(c);
            }
            return sb.toString();
        }

        static boolean isDone(String jsonLine)
        {
            return jsonLine.contains("\"done\":true");
        }
    }
}

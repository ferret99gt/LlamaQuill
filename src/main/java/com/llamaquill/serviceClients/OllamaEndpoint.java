package com.llamaquill.serviceClients;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

public final class OllamaEndpoint
{
    private OllamaEndpoint()
    {
    }

    public static String normalize(String value)
    {
        String candidate = value == null ? "" : value.trim();
        if (candidate.isEmpty())
        {
            throw new IllegalArgumentException("Ollama URL is required.");
        }

        final URI uri;
        try
        {
            uri = new URI(candidate);
        }
        catch (URISyntaxException e)
        {
            throw new IllegalArgumentException("Ollama URL is invalid: " + e.getMessage(), e);
        }

        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme))
        {
            throw new IllegalArgumentException("Ollama URL must use http or https.");
        }
        if (uri.getHost() == null || uri.getHost().isBlank())
        {
            throw new IllegalArgumentException("Ollama URL must include a host.");
        }
        if (uri.getRawQuery() != null || uri.getRawFragment() != null)
        {
            throw new IllegalArgumentException("Ollama URL cannot include a query or fragment.");
        }

        String path = uri.getPath() == null ? "" : uri.getPath();
        while (path.endsWith("/") && !path.isEmpty())
        {
            path = path.substring(0, path.length() - 1);
        }

        try
        {
            return new URI(scheme, uri.getUserInfo(), uri.getHost(), uri.getPort(), path, null, null).toString();
        }
        catch (URISyntaxException e)
        {
            throw new IllegalArgumentException("Ollama URL could not be normalized.", e);
        }
    }

    public static String normalizeOrDefault(String value)
    {
        try
        {
            return normalize(value);
        }
        catch (IllegalArgumentException ignored)
        {
            return OllamaClient.DEFAULT_HOST;
        }
    }

    public static URI resolve(String baseUrl, String apiPath)
    {
        String base = normalize(baseUrl);
        String path = apiPath == null ? "" : apiPath.trim();
        if (path.isEmpty())
        {
            return URI.create(base);
        }
        if (!path.startsWith("/"))
        {
            path = "/" + path;
        }
        return URI.create(base + path);
    }
}

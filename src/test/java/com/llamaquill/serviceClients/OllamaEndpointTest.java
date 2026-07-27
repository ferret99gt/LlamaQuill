package com.llamaquill.serviceClients;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class OllamaEndpointTest
{
    @Test
    void normalizesTrailingSlashesAndPreservesAProxyPath()
    {
        assertEquals("http://localhost:11434", OllamaEndpoint.normalize(" http://localhost:11434/// "));
        assertEquals("https://example.test:8443/ollama",
                OllamaEndpoint.normalize("https://example.test:8443/ollama/"));
        assertEquals("/ollama/api/tags",
                OllamaEndpoint.resolve("https://example.test/ollama/", "/api/tags").getPath());
    }

    @Test
    void rejectsUnsafeOrAmbiguousBaseUrls()
    {
        assertThrows(IllegalArgumentException.class, () -> OllamaEndpoint.normalize(""));
        assertThrows(IllegalArgumentException.class, () -> OllamaEndpoint.normalize("localhost:11434"));
        assertThrows(IllegalArgumentException.class, () -> OllamaEndpoint.normalize("ftp://localhost/models"));
        assertThrows(IllegalArgumentException.class, () -> OllamaEndpoint.normalize("http://localhost?model=x"));
        assertThrows(IllegalArgumentException.class, () -> OllamaEndpoint.normalize("http://localhost/#fragment"));
    }
}

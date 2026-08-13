package com.llamaquill.serviceClients;

import java.io.IOException;

public class OllamaException extends IOException
{
    private final String endpoint;
    private final int statusCode;
    private final String detail;

    public OllamaException(String message, String endpoint, int statusCode, String detail)
    {
        super(message);
        this.endpoint = endpoint == null ? "" : endpoint;
        this.statusCode = statusCode;
        this.detail = detail == null ? "" : detail;
    }

    public OllamaException(String message, String endpoint, Throwable cause)
    {
        super(message, cause);
        this.endpoint = endpoint == null ? "" : endpoint;
        this.statusCode = -1;
        this.detail = cause == null || cause.getMessage() == null ? "" : cause.getMessage();
    }

    public String endpoint()
    {
        return endpoint;
    }

    public int statusCode()
    {
        return statusCode;
    }

    public String detail()
    {
        return detail;
    }

    public String diagnosticText()
    {
        StringBuilder text = new StringBuilder(getMessage());
        if (!endpoint.isBlank())
        {
            text.append("\nEndpoint: ").append(endpoint);
        }
        if (statusCode >= 0)
        {
            text.append("\nHTTP status: ").append(statusCode);
        }
        if (!detail.isBlank() && !detail.equals(getMessage()))
        {
            text.append("\nDetail: ").append(detail);
        }
        return text.toString();
    }
}

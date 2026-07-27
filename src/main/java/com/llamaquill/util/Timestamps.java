package com.llamaquill.util;

import java.time.Instant;

public final class Timestamps
{
    private Timestamps()
    {
    }

    public static String now()
    {
        return Instant.now().toString();
    }
}

package com.llamaquill;

public final class AppVersion
{
    public static final String CURRENT = "0.2.0";
    public static final String FIRST_MIGRATION_SOURCE = "0.1.0";
    public static final int DATABASE_SCHEMA = 2;

    private AppVersion()
    {
    }

    public static String displayName()
    {
        return "LlamaQuill " + CURRENT;
    }
}

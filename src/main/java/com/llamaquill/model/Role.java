package com.llamaquill.model;

public enum Role
{
    USER("user"),
    ASSISTANT("assistant");

    private final String wire;

    Role(String wire)
    {
        this.wire = wire;
    }

    public String wire()
    {
        return wire;
    }

    public static Role fromWire(String value)
    {
        for (Role role : values())
        {
            if (role.wire.equals(value))
            {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown role: " + value);
    }
}

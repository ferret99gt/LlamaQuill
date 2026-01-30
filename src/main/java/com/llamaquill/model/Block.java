package com.llamaquill.model;

public record Block(String id, String storyId, Role role, String text, String createdAt, int position)
{
}

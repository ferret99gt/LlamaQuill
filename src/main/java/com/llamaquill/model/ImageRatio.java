package com.llamaquill.model;

import java.util.Arrays;

public enum ImageRatio
{
    SQUARE("1:1", 1, 1),
    LANDSCAPE_16_9("16:9", 16, 9),
    LANDSCAPE_3_2("3:2", 3, 2),
    LANDSCAPE_4_3("4:3", 4, 3),
    PORTRAIT_9_16("9:16", 9, 16),
    PORTRAIT_2_3("2:3", 2, 3),
    PORTRAIT_3_4("3:4", 3, 4);

    public static final int MIN_DIMENSION = 64;
    public static final int MAX_DIMENSION = 4096;
    public static final int DIMENSION_STEP = 8;

    private final String label;
    private final int widthUnits;
    private final int heightUnits;

    ImageRatio(String label, int widthUnits, int heightUnits)
    {
        this.label = label;
        this.widthUnits = widthUnits;
        this.heightUnits = heightUnits;
    }

    public Dimensions dimensions(int dimension)
    {
        int longEdge = normalizeDimension(dimension);
        if (widthUnits == heightUnits)
        {
            return new Dimensions(longEdge, longEdge);
        }
        if (widthUnits > heightUnits)
        {
            return new Dimensions(longEdge, scaledShortEdge(longEdge, heightUnits, widthUnits));
        }
        return new Dimensions(scaledShortEdge(longEdge, widthUnits, heightUnits), longEdge);
    }

    public static int normalizeDimension(int value)
    {
        int clamped = Math.max(MIN_DIMENSION, Math.min(MAX_DIMENSION, value));
        int rounded = (int) Math.round(clamped / (double) DIMENSION_STEP) * DIMENSION_STEP;
        return Math.max(MIN_DIMENSION, Math.min(MAX_DIMENSION, rounded));
    }

    public static ImageRatio fromPersisted(String value)
    {
        String normalized = value == null ? "" : value.trim();
        return Arrays.stream(values())
                .filter(ratio -> ratio.label.equals(normalized) || ratio.name().equalsIgnoreCase(normalized))
                .findFirst()
                .orElse(SQUARE);
    }

    public static ImageRatio nearest(int width, int height)
    {
        if (width <= 0 || height <= 0)
        {
            return SQUARE;
        }
        double actual = width / (double) height;
        return Arrays.stream(values())
                .min((left, right) -> Double.compare(
                        Math.abs(actual - left.value()), Math.abs(actual - right.value())))
                .orElse(SQUARE);
    }

    public String persistedValue()
    {
        return label;
    }

    @Override
    public String toString()
    {
        return label;
    }

    private double value()
    {
        return widthUnits / (double) heightUnits;
    }

    private static int scaledShortEdge(int longEdge, int shortUnits, int longUnits)
    {
        int calculated = (int) Math.round(longEdge * shortUnits / (double) longUnits);
        return Math.max(MIN_DIMENSION, normalizeDimension(calculated));
    }

    public record Dimensions(int width, int height)
    {
        public int longEdge()
        {
            return Math.max(width, height);
        }

        @Override
        public String toString()
        {
            return width + " × " + height;
        }
    }
}

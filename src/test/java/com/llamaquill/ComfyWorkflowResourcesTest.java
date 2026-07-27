package com.llamaquill;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class ComfyWorkflowResourcesTest
{
    @Test
    void allBundledWorkflowsAreIndividuallyDiscoverable()
    {
        assertEquals(List.of("ChromaHD", "Chroma2Kaleidoscope"), App.bundledComfyWorkflowNames());
    }
}

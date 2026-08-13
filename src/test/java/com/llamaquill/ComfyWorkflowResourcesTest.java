package com.llamaquill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

class ComfyWorkflowResourcesTest
{
    @Test
    void allBundledWorkflowsAreIndividuallyDiscoverable()
    {
        assertEquals(List.of("ChromaHD", "Kroma"), App.bundledComfyWorkflowNames());
    }

    @Test
    void inactiveWorkflowsRemainPackagedButAreFiltered()
    {
        assertNotNull(App.class.getResource("/comfyui/Chroma2Kaleidoscope.json"));
        assertNotNull(App.class.getResource("/comfyui/Zeta-Chroma.json"));
        assertFalse(App.isActiveComfyWorkflowName("Chroma2Kaleidoscope"));
        assertFalse(App.isActiveComfyWorkflowName("Zeta-Chroma"));
        assertTrue(App.isActiveComfyWorkflowName("Kroma"));
    }

    @Test
    void kromaUsesTheNativeKreaTwoTurboPipeline() throws IOException
    {
        JSONObject workflow = workflow("Kroma");
        assertEquals("kroma-v0.2-turbo.safetensors",
                workflow.getJSONObject("1").getJSONObject("inputs").getString("unet_name"));
        assertEquals("qwen3vl_4b_fp8_scaled.safetensors",
                workflow.getJSONObject("2").getJSONObject("inputs").getString("clip_name"));
        assertEquals("krea2",
                workflow.getJSONObject("2").getJSONObject("inputs").getString("type"));
        assertEquals("qwen_image_vae.safetensors",
                workflow.getJSONObject("3").getJSONObject("inputs").getString("vae_name"));
        assertEquals("ConditioningZeroOut", workflow.getJSONObject("5").getString("class_type"));

        JSONObject sampler = workflow.getJSONObject("7").getJSONObject("inputs");
        assertEquals(8, sampler.getInt("steps"));
        assertEquals(1.0, sampler.getDouble("cfg"));
        assertEquals("euler", sampler.getString("sampler_name"));
        assertEquals("simple", sampler.getString("scheduler"));
    }

    @Test
    void zetaChromaUsesItsPixelSpaceWorkflow() throws IOException
    {
        JSONObject workflow = workflow("Zeta-Chroma");
        assertEquals("zeta-chroma-base-x0-pixel-dino-distance.safetensors",
                workflow.getJSONObject("1").getJSONObject("inputs").getString("unet_name"));
        assertEquals("qwen_3_4b.safetensors",
                workflow.getJSONObject("2").getJSONObject("inputs").getString("clip_name"));
        assertEquals("lumina2",
                workflow.getJSONObject("2").getJSONObject("inputs").getString("type"));
        assertEquals("pixel_space",
                workflow.getJSONObject("3").getJSONObject("inputs").getString("vae_name"));
        assertEquals(8.0,
                workflow.getJSONObject("4").getJSONObject("inputs").getDouble("shift"));
        assertEquals("EmptyImage", workflow.getJSONObject("7").getString("class_type"));
        assertEquals("VAEEncode", workflow.getJSONObject("8").getString("class_type"));

        JSONObject sampler = workflow.getJSONObject("9").getJSONObject("inputs");
        assertEquals(30, sampler.getInt("steps"));
        assertEquals(3.8, sampler.getDouble("cfg"));
        assertEquals("er_sde", sampler.getString("sampler_name"));
        assertEquals("beta", sampler.getString("scheduler"));
        assertEquals(0, sampler.getInt("start_at_step"));
        assertEquals(60, sampler.getInt("end_at_step"));
    }

    private static JSONObject workflow(String name) throws IOException
    {
        try (InputStream stream = App.class.getResourceAsStream("/comfyui/" + name + ".json"))
        {
            assertNotNull(stream, "Missing workflow resource " + name);
            String template = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            return new JSONObject(template
                    .replace("%{prompt}", "test prompt")
                    .replace("%{seed}", "1")
                    .replace("%{width}", "1024")
                    .replace("%{height}", "1024")
                    .replace("%{batch_size}", "1"));
        }
    }
}

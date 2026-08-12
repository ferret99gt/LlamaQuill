package com.llamaquill.serviceClients;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

class ComfyUiClientTest
{
    private static final String SAVE_IMAGE_WORKFLOW = """
            {
              "8": {
                "class_type": "VAEDecode",
                "inputs": { "samples": ["7", 0] }
              },
              "9": {
                "class_type": "SaveImage",
                "inputs": {
                  "filename_prefix": "LlamaQuill\\\\StoryImage",
                  "images": ["8", 0]
                }
              }
            }
            """;

    @Test
    void previewModeSubmitsTemporaryOutputWithoutChangingTheBundledSavePath()
    {
        String submitted = ComfyUiClient.applyImageOutputMode(
                SAVE_IMAGE_WORKFLOW, ComfyUiClient.ImageOutputMode.PREVIEW);

        JSONObject outputNode = new JSONObject(submitted).getJSONObject("9");
        assertEquals("PreviewImage", outputNode.getString("class_type"));
        assertFalse(outputNode.getJSONObject("inputs").has("filename_prefix"));
        assertEquals("8", outputNode.getJSONObject("inputs").getJSONArray("images").getString(0));
        assertTrue(SAVE_IMAGE_WORKFLOW.contains("\"class_type\": \"SaveImage\""));
        assertTrue(SAVE_IMAGE_WORKFLOW.contains("LlamaQuill\\\\StoryImage"));
    }

    @Test
    void permanentModeRetainsTheOriginalSaveImageWorkflow()
    {
        assertEquals(SAVE_IMAGE_WORKFLOW, ComfyUiClient.applyImageOutputMode(
                SAVE_IMAGE_WORKFLOW, ComfyUiClient.ImageOutputMode.PERMANENT));
    }
}

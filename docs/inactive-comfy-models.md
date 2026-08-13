# Inactive ComfyUI models

These developer notes preserve setup and workflow details for image models that are intentionally
hidden from LlamaQuill's released workflow list. Their JSON templates remain under
`src/main/resources/comfyui/` so they can be revisited without reconstructing the node graphs.

## Chroma2 Kaleidoscope

Resource: `Chroma2Kaleidoscope.json`

Status: inactive. Chroma2 Kaleidoscope had no further releases for roughly six months and was
superseded in LlamaQuill testing by Kroma.

Setup:

- Install the custom nodes and supporting models used by ComfyUI Desktop's
  `Flux.2 [Klein] 4B` template.
- The Flux.2 base-model download itself is not required by this workflow.
- Download Chroma2 Kaleidoscope from
  [lodestones/Chroma2-Kaleidoscope](https://huggingface.co/lodestones/Chroma2-Kaleidoscope).

## Zeta-Chroma

Resource: `Zeta-Chroma.json`

Status: inactive after successful integration testing. It followed prompts well enough to retain
core scene details and had good runtime performance, but its output was much more saturated and
less consistent than Kroma. Kroma was also judged superior to Chroma1-HD.

The model card's embedded ComfyUI metadata referenced an unpublished
`zeta-chroma-base-x0-pixel-dino-distance-v8.safetensors`. The preserved workflow uses the closest
released checkpoint, `zeta-chroma-base-x0-pixel-dino-distance.safetensors`.

Setup:

- Update ComfyUI so its native pixel-space VAE and ER-SDE sampler are available.
- Install `zeta-chroma-base-x0-pixel-dino-distance.safetensors` from
  [lodestones/Zeta-Chroma](https://huggingface.co/lodestones/Zeta-Chroma) under
  `ComfyUI/models/diffusion_models/`.
- Install
  [`qwen_3_4b.safetensors`](https://huggingface.co/Comfy-Org/z_image_turbo/resolve/main/split_files/text_encoders/qwen_3_4b.safetensors)
  under `ComfyUI/models/text_encoders/`.
- No VAE file is required; `pixel_space` is built into current ComfyUI versions.

Preserved workflow settings:

- CLIP loader type: `lumina2`
- AuraFlow shift: `8.0`
- Starting latent: black `EmptyImage` encoded through the `pixel_space` VAE
- Sampler: ER-SDE with the beta scheduler
- Steps: `30`
- CFG: `3.8`
- Advanced sampler range: steps `0` through `60`

Local testing with four-image batches took about 24 seconds for the first run after loading or
swapping models and about 16 seconds after warm-up. Speed was competitive with Kroma; quality and
consistency were not.

To reactivate an archived workflow, add its name to `BUNDLED_COMFY_WORKFLOW_NAMES`, remove it from
`INACTIVE_COMFY_WORKFLOW_NAMES`, restore appropriate released-user setup instructions, and update
`ComfyWorkflowResourcesTest`.

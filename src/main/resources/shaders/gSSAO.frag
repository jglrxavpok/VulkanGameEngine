#version 450
#extension GL_ARB_separate_shader_objects : enable
layout (constant_id = 0) const int SAMPLE_COUNT = 16;
layout (constant_id = 1) const float RADIUS = 0.30;
layout (constant_id = 2) const float BIAS = 0.025;
const vec2 noiseScale = vec2(1920.0/4.0, 1080.0/4.0);

layout(location = 0) in vec2 fragPos;
layout(location = 1) in vec2 fragCoords;

layout(binding = 0) uniform sampler2D gPos;
layout(input_attachment_index = 2, binding = 1) uniform subpassInput gNormal;
layout(binding = 2) uniform sampler2D noise;

layout(binding = 3) uniform SSAOBufferObject {
    mat4 projection;
    vec3 noiseSamples[SAMPLE_COUNT];
} ubo;

layout(location = 0) out vec4 ssaoFactor;

void main() {
    vec2 coords = fragCoords;
    vec3 fragViewPos   = texture(gPos, coords).xyz;

    // Gram-Schmidt process: create an orthogonal basis
    vec3 normal    = -normalize(subpassLoad(gNormal).xyz);
    vec3 randomVec = normalize(texture(noise, coords*noiseScale).xyz);
    vec3 tangent   = normalize(randomVec - normal * dot(randomVec, normal));
    vec3 bitangent = cross(normal, tangent);
    // End of Gram-Schmidt

    mat3 TBN       = mat3(tangent, bitangent, normal);
    float occlusion = 0.0;
    for(int i = 0; i < SAMPLE_COUNT; i++) {
        vec3 kernelSample = TBN * ubo.noiseSamples[i];
        kernelSample = fragViewPos + kernelSample * RADIUS;

        vec4 offset = vec4(kernelSample, 1.0);
        offset      = ubo.projection * offset;    // from view to clip-space
        offset.xyz /= offset.w;               // perspective divide
        offset.xyz  = offset.xyz * 0.5 + 0.5; // transform to range 0.0 - 1.0

        float sampleDepth = texture(gPos, offset.xy).z;
        float rangeCheck = smoothstep(0.0, 1.0, RADIUS / abs(fragViewPos.z - sampleDepth));
        occlusion       += (sampleDepth >= kernelSample.z + BIAS ? 1.0 : 0.0) * rangeCheck;
    }

    float factor = (occlusion / SAMPLE_COUNT);
    ssaoFactor = vec4(1.0-factor, 0.0, 0.0, 1.0);
}
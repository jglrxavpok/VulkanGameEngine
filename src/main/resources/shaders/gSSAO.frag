#version 450
#extension GL_ARB_separate_shader_objects : enable
layout (constant_id = 0) const int KERNEL_SIZE = 16;

layout(location = 0) in vec2 fragPos;

layout(binding = 0) uniform sampler2D gPos;
layout(input_attachment_index = 1, binding = 1) uniform subpassInput gNormal;
layout(binding = 2) uniform sampler2D noise;

layout(binding = 3) uniform SSAOBufferObject {
    mat4 projection;
    vec3 noiseSamples[KERNEL_SIZE];
} ubo;

layout(location = 0) out vec4 ssaoFactor;

void main() {
    float factor = 0.5;
    ssaoFactor = vec4(factor);
}
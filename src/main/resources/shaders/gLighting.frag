#version 450
#extension GL_ARB_separate_shader_objects : enable

layout(location = 0) in vec2 fragPos;
layout(location = 1) in vec2 fragCoords;

layout(input_attachment_index = 0, binding = 0) uniform subpassInput gColor;
layout(input_attachment_index = 1, binding = 1) uniform subpassInput gPos;
layout(input_attachment_index = 2, binding = 2) uniform subpassInput gNormal;
layout(location = 0) out vec4 outColor;

void main() {
    // TODO: perform lighting
    outColor = subpassLoad(gColor);
}